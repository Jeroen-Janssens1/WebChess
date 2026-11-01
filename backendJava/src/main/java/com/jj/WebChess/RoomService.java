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

    private static final int ROOM_CAPACITY = 20;

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

    public synchronized List<RoomView> getRooms() {
        return rooms.values().stream().map(Room::toListingView).toList();
    }

    public synchronized RoomView getRoom(String roomId, String username) {
        Room room = getRoom(roomId);
        requireMember(room, username);
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
        if (room.members.size() < 2) {
            throw new RoomException(HttpStatus.CONFLICT, "A game requires two room members");
        }

        List<String> players = room.members.stream().limit(2).toList();
        ensurePlayersAvailable(room.id, players);
        GameService.GameState gameState = gameService.startRoomGame(roomId, players);
        room.gameIds.add(gameState.gameId());
        notifyRoomUpdated(room, null);
        return gameState;
    }

    public synchronized GameService.RematchResult requestRematch(String gameId, String username) {
        GameService.RematchResult result = gameService.requestRematch(gameId, username);
        if (result.started()) {
            String roomId = result.gameState().roomId();
            Room room = rooms.get(roomId);
            if (room != null) {
                notifyRoomUpdated(room, null);
            }
        }
        return result;
    }

    public synchronized RoomView transferHost(String roomId, String username, String newHost) {
        Room room = getRoom(roomId);
        requireMember(room, username);
        if (!room.creator.equals(username)) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Only the room host can transfer host privileges");
        }
        if (newHost == null || !room.members.contains(newHost)) {
            throw new RoomException(HttpStatus.BAD_REQUEST, "The new host must be a room member");
        }
        if (newHost.equals(username)) {
            throw new RoomException(HttpStatus.BAD_REQUEST, "You are already the room host");
        }
        room.creator = newHost;
        notifyRoomUpdated(room, null);
        return room.toView();
    }

    public synchronized GameRequestView requestGame(String roomId, String username, String opponent) {
        Room room = getRoom(roomId);
        requireMember(room, username);
        if (opponent == null || !room.members.contains(opponent) || username.equals(opponent)) {
            throw new RoomException(HttpStatus.BAD_REQUEST, "Choose another member of this room");
        }
        ensurePlayersAvailable(room.id, List.of(username, opponent));
        if (room.gameRequests.values().stream().anyMatch(request ->
                (request.requester.equals(username) && request.opponent.equals(opponent))
                    || (request.requester.equals(opponent) && request.opponent.equals(username)))) {
            throw new RoomException(HttpStatus.CONFLICT, "A game request is already pending between these members");
        }
        GameRequest request = new GameRequest(UUID.randomUUID().toString(), username, opponent);
        room.gameRequests.put(request.id, request);
        notifyRoomUpdated(room, null);
        return request.toView();
    }

    public synchronized GameService.GameState acceptGameRequest(
            String roomId, String requestId, String username) {
        Room room = getRoom(roomId);
        requireMember(room, username);
        GameRequest request = getGameRequest(room, requestId);
        if (!request.opponent.equals(username)) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Only the invited member can accept this request");
        }
        ensurePlayersAvailable(room.id, List.of(request.requester, request.opponent));
        GameService.GameState game = gameService.startRoomGame(
            roomId, List.of(request.requester, request.opponent));
        room.gameRequests.remove(requestId);
        room.gameIds.add(game.gameId());
        notifyRoomUpdated(room, null);
        return game;
    }

    public synchronized RoomView declineGameRequest(String roomId, String requestId, String username) {
        Room room = getRoom(roomId);
        requireMember(room, username);
        GameRequest request = getGameRequest(room, requestId);
        if (!request.opponent.equals(username)) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Only the invited member can decline this request");
        }
        room.gameRequests.remove(requestId);
        notifyRoomUpdated(room, null);
        return room.toView();
    }

    public synchronized GameService.GameState getRoomGame(String roomId, String gameId, String username) {
        Room room = getRoom(roomId);
        requireMember(room, username);
        return gameService.getRoomGameState(roomId, gameId);
    }

    public synchronized SseEmitter subscribeToRoomGame(String roomId, String gameId, String username) {
        Room room = getRoom(roomId);
        requireMember(room, username);
        return gameService.subscribeToRoomGame(roomId, gameId, username);
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
            gameService.unsubscribeRoomMembers(roomId);
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
        gameService.unsubscribeRoomMember(roomId, username);
        room.gameRequests.values().removeIf(request ->
            request.requester.equals(username) || request.opponent.equals(username));
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
        Room room = getRoom(roomId);
        requireMember(room, username);

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
            boolean passwordProtected, String gameId,
            List<GameService.RoomGameView> games, List<GameRequestView> gameRequests) {}

    public record GameRequestView(String id, String requester, String opponent) {}

    private Room getRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new RoomException(HttpStatus.NOT_FOUND, "Room not found");
        }
        return room;
    }

    private void requireMember(Room room, String username) {
        if (!room.members.contains(username)) {
            throw new RoomException(HttpStatus.FORBIDDEN, "Join the room first");
        }
    }

    private void ensurePlayersAvailable(String roomId, List<String> players) {
        if (players.stream().anyMatch(player -> gameService.hasActiveRoomGame(roomId, player))) {
            throw new RoomException(HttpStatus.CONFLICT, "A room member is already playing an active game");
        }
    }

    private GameRequest getGameRequest(Room room, String requestId) {
        GameRequest request = room.gameRequests.get(requestId);
        if (request == null) {
            throw new RoomException(HttpStatus.NOT_FOUND, "Game request not found");
        }
        return request;
    }

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

    private class Room {
        private final String id;
        private final String name;
        private String creator;
        private final String passwordHash;
        private final Set<String> members = new LinkedHashSet<>();
        private final List<String> gameIds = new ArrayList<>();
        private final Map<String, GameRequest> gameRequests = new LinkedHashMap<>();

        private Room(String id, String name, String creator, String passwordHash) {
            this.id = id;
            this.name = name;
            this.creator = creator;
            this.passwordHash = passwordHash;
        }

        private RoomView toView() {
            List<GameService.RoomGameView> games = gameService.getRoomGames(id);
            String latestGameId = gameIds.isEmpty() ? null : gameIds.get(gameIds.size() - 1);
            return new RoomView(id, name, creator, List.copyOf(members), passwordHash != null,
                latestGameId, games, gameRequests.values().stream().map(GameRequest::toView).toList());
        }

        private RoomView toListingView() {
            String latestGameId = gameIds.isEmpty() ? null : gameIds.get(gameIds.size() - 1);
            return new RoomView(id, name, creator, List.copyOf(members), passwordHash != null,
                latestGameId, List.of(), List.of());
        }
    }

    private record GameRequest(String id, String requester, String opponent) {
        private GameRequestView toView() {
            return new GameRequestView(id, requester, opponent);
        }
    }

    private record Subscription(String username, SseEmitter emitter) {}
}