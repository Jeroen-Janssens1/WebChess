package com.jj.WebChess;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
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
        return gameService.requestRematch(gameId, principal.getName());
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

    public record SelectPieceRequest(String square) {}

    public record MovePieceRequest(String from, String to, String promotion) {}
}
