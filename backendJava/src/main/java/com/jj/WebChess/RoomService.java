package com.jj.WebChess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RoomService {

    private static final int ROOM_CAPACITY = 2;

    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, List<Subscription>> subscriptions = new HashMap<>();
    private final PasswordEncoder passwordEncoder;
    private final GameService gameService;

    public RoomService(PasswordEncoder passwordEncoder, GameService gameService) {
        this.passwordEncoder = passwordEncoder;
        this.gameService = gameService;
    }

    public synchronized RoomView create(String username, String name, String password) {
        if (name == null || name.isBlank()) {
            throw new RoomException(HttpStatus.BAD_REQUEST, "Room name is required");
        }

        String normalizedName = name.trim();
        if (rooms.values().stream().anyMatch(room -> room.name.equals(normalizedName))) {
            throw new RoomException(HttpStatus.CONFLICT, "A room with that name already exists");
        }

        String id = UUID.randomUUID().toString();
        Room room = new Room(id, normalizedName, username,
            password == null || password.isBlank() ? null : passwordEncoder.encode(password));
        room.members.add(username);
        rooms.put(id, room);
        return room.toView();
    }

    public synchronized RoomView join(String username, String roomIdOrName, String password) {
        if (roomIdOrName == null || roomIdOrName.isBlank()) {
            throw new RoomException(HttpStatus.BAD_REQUEST, "Room name or id is required");
        }

        Room room = rooms.get(roomIdOrName);
        if (room == null) {
            room = rooms.values().stream()
                .filter(candidate -> candidate.name.equals(roomIdOrName))
                .findFirst()
                .orElseThrow(() -> new RoomException(HttpStatus.NOT_FOUND, "Room not found"));
        }

        if (room.members.contains(username)) {
            return room.toView();
        }
        if (room.gameId != null) {
            throw new RoomException(HttpStatus.CONFLICT, "A game has already started in this room");
        }
        if (room.passwordHash != null
                && (password == null || !passwordEncoder.matches(password, room.passwordHash))) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Incorrect room password");
        }
        if (room.members.size() >= ROOM_CAPACITY) {
            throw new RoomException(HttpStatus.CONFLICT, "Room is full");
        }

        room.members.add(username);
        notifyRoomUpdated(room, username);
        return room.toView();
    }

    public synchronized GameService.GameState startGame(String roomId, String username) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new RoomException(HttpStatus.NOT_FOUND, "Room not found");
        }
        if (!room.creator.equals(username)) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Only the room host can start a game");
        }
        if (room.members.size() != ROOM_CAPACITY) {
            throw new RoomException(HttpStatus.CONFLICT, "A game can only start with two players");
        }
        if (room.gameId != null) {
            throw new RoomException(HttpStatus.CONFLICT, "A game has already started in this room");
        }

        List<String> players = List.copyOf(room.members);
        GameService.GameState gameState = gameService.startRoomGame(roomId, players);
        room.gameId = gameState.gameId();
        notifyRoomUpdated(room, null);
        return gameState;
    }

    public synchronized boolean leave(String roomId, String username) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new RoomException(HttpStatus.NOT_FOUND, "Room not found");
        }
        if (!room.members.contains(username)) {
            throw new RoomException(HttpStatus.NOT_FOUND, "You are not in this room");
        }

        if (room.creator.equals(username)) {
            rooms.remove(roomId);
            List<Subscription> roomSubscriptions = subscriptions.remove(roomId);
            if (roomSubscriptions != null) {
                for (Subscription subscription : List.copyOf(roomSubscriptions)) {
                    try {
                        subscription.emitter.send(SseEmitter.event()
                            .name("room_closed")
                            .data(Map.of("roomId", roomId, "message", "The room creator left")));
                        subscription.emitter.complete();
                    } catch (IOException exception) {
                        subscription.emitter.completeWithError(exception);
                    }
                }
            }
            return true;
        }

        room.members.remove(username);
        List<Subscription> roomSubscriptions = subscriptions.get(roomId);
        if (roomSubscriptions != null) {
            roomSubscriptions.stream()
                .filter(subscription -> subscription.username.equals(username))
                .map(subscription -> subscription.emitter)
                .toList()
                .forEach(emitter -> emitter.complete());
            roomSubscriptions.removeIf(subscription -> subscription.username.equals(username));
        }
        notifyRoomUpdated(room, username);
        return false;
    }

    private void notifyRoomUpdated(Room room, String excludedUsername) {
        List<Subscription> roomSubscriptions = subscriptions.get(room.id);
        if (roomSubscriptions == null) {
            return;
        }

        for (Subscription subscription : List.copyOf(roomSubscriptions)) {
            if (subscription.username.equals(excludedUsername)) {
                continue;
            }
            try {
                subscription.emitter.send(SseEmitter.event()
                    .name("room_updated")
                    .data(room.toView()));
            } catch (IOException exception) {
                subscription.emitter.completeWithError(exception);
            }
        }
    }

    public synchronized SseEmitter subscribe(String roomId, String username) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new RoomException(HttpStatus.NOT_FOUND, "Room not found");
        }
        if (!room.members.contains(username)) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Join the room before subscribing");
        }

        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(username, emitter);
        subscriptions.computeIfAbsent(roomId, ignored -> new ArrayList<>()).add(subscription);
        emitter.onCompletion(() -> unsubscribe(roomId, emitter));
        emitter.onTimeout(() -> unsubscribe(roomId, emitter));
        emitter.onError(exception -> unsubscribe(roomId, emitter));
        try {
            emitter.send(SseEmitter.event().name("room").data(room.toView()));
        } catch (IOException exception) {
            unsubscribe(roomId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private synchronized void unsubscribe(String roomId, SseEmitter emitter) {
        List<Subscription> roomSubscriptions = subscriptions.get(roomId);
        if (roomSubscriptions != null) {
            roomSubscriptions.removeIf(subscription -> subscription.emitter == emitter);
            if (roomSubscriptions.isEmpty()) {
                subscriptions.remove(roomId);
            }
        }
    }

    public record RoomView(
            String id, String name, String creator, List<String> users,
            boolean passwordProtected, String gameId) {}

    public static class RoomException extends RuntimeException {
        private final HttpStatus status;

        public RoomException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    private static class Room {
        private final String id;
        private final String name;
        private final String creator;
        private final String passwordHash;
        private final Set<String> members = new LinkedHashSet<>();
        private String gameId;

        private Room(String id, String name, String creator, String passwordHash) {
            this.id = id;
            this.name = name;
            this.creator = creator;
            this.passwordHash = passwordHash;
        }

        private RoomView toView() {
            return new RoomView(id, name, creator, List.copyOf(members), passwordHash != null, gameId);
        }
    }

    private record Subscription(String username, SseEmitter emitter) {}
}