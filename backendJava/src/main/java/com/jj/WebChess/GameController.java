package com.jj.WebChess;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;
    private final RoomService roomService;
    private final GameHistoryService gameHistoryService;

    public GameController(
            GameService gameService, RoomService roomService, GameHistoryService gameHistoryService) {
        this.gameService = gameService;
        this.roomService = roomService;
        this.gameHistoryService = gameHistoryService;
    }

    @GetMapping("/history")
    public GameHistoryService.HistoryPage getGameHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String playerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String color,
            Principal principal) {
        return gameHistoryService.getHistory(
            principal.getName(), page, playerName, from, to, condition, color);
    }

    @GetMapping("/history/{gameId}")
    public GameHistoryService.ReplayView getGameReplay(
            @PathVariable String gameId, Principal principal) {
        return gameHistoryService.getReplay(principal.getName(), gameId);
    }

    @PostMapping
    public ResponseEntity<GameService.GameState> startLocalGame(Principal principal) {
        return ResponseEntity.status(201).body(gameService.startLocalGame(principal.getName()));
    }

    @GetMapping("/{gameId}")
    public GameService.GameState getGame(@PathVariable String gameId, Principal principal) {
        return gameService.getState(gameId, principal.getName());
    }

    @PostMapping("/{gameId}/select")
    public GameService.SelectionView selectPiece(
            @PathVariable String gameId,
            @RequestBody SelectPieceRequest request,
            Principal principal) {
        return gameService.selectPiece(gameId, principal.getName(), request.square());
    }

    @PostMapping("/{gameId}/move")
    public GameService.MoveResult movePiece(
            @PathVariable String gameId,
            @RequestBody MovePieceRequest request,
            Principal principal) {
        return gameService.movePiece(
            gameId, principal.getName(), request.from(), request.to(), request.promotion());
    }

    @PostMapping("/{gameId}/forfeit")
    public GameService.GameState forfeit(@PathVariable String gameId, Principal principal) {
        return gameService.forfeit(gameId, principal.getName());
    }

    @PostMapping("/{gameId}/rematch")
    public GameService.RematchResult requestRematch(@PathVariable String gameId, Principal principal) {
        return roomService.requestRematch(gameId, principal.getName());
    }

    @GetMapping(value = "/{gameId}/events", produces = "text/event-stream")
    public SseEmitter subscribe(@PathVariable String gameId, Principal principal) {
        return gameService.subscribe(gameId, principal.getName());
    }

    @ExceptionHandler(GameService.GameException.class)
    public ResponseEntity<Map<String, String>> handleGameException(GameService.GameException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(GameHistoryService.HistoryException.class)
    public ResponseEntity<Map<String, String>> handleHistoryException(
            GameHistoryService.HistoryException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(Map.of("error", exception.getMessage()));
    }

    public record SelectPieceRequest(String square) {}

    public record MovePieceRequest(String from, String to, String promotion) {}
}
