package com.jj.WebChess;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomService.RoomView> createRoom(
            @RequestBody CreateRoomRequest request, Principal principal) {
        return ResponseEntity.status(201)
            .body(roomService.create(principal.getName(), request.name(), request.password()));
    }

    @PostMapping("/join")
    public RoomService.RoomView joinRoom(@RequestBody JoinRoomRequest request, Principal principal) {
        return roomService.join(principal.getName(), request.roomIdOrName(), request.password());
    }

    @PostMapping("/{roomId}/games")
    public ResponseEntity<GameService.GameState> startGame(
            @PathVariable String roomId, Principal principal) {
        return ResponseEntity.status(201).body(roomService.startGame(roomId, principal.getName()));
    }

    @DeleteMapping("/{roomId}/members/me")
    public Map<String, Object> leaveRoom(@PathVariable String roomId, Principal principal) {
        boolean roomClosed = roomService.leave(roomId, principal.getName());
        return Map.of("message", roomClosed ? "Room closed" : "Left room", "roomClosed", roomClosed);
    }

    @GetMapping(value = "/{roomId}/events", produces = "text/event-stream")
    public SseEmitter subscribe(@PathVariable String roomId, Principal principal) {
        return roomService.subscribe(roomId, principal.getName());
    }

    @ExceptionHandler(RoomService.RoomException.class)
    public ResponseEntity<Map<String, String>> handleRoomException(RoomService.RoomException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(Map.of("error", exception.getMessage()));
    }

    public record CreateRoomRequest(String name, String password) {}

    public record JoinRoomRequest(String roomIdOrName, String password) {}
}