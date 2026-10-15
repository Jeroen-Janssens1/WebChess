package com.jj.WebChess;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class GameService {

    private final Map<String, ChessGame> games = new LinkedHashMap<>();
    private final Map<String, List<Subscription>> subscriptions = new HashMap<>();

    public synchronized GameState startLocalGame(String username) {
        return startGame("", List.of(username), username);
    }

    synchronized GameState startRoomGame(String roomId, List<String> players) {
        String whitePlayer = java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
            ? players.get(0)
            : players.get(1);
        return startGame(roomId, players, whitePlayer);
    }

    private GameState startGame(String roomId, List<String> players, String whitePlayer) {
        ChessGame game = new ChessGame(UUID.randomUUID().toString(), roomId, players, whitePlayer);
        games.put(game.gameId(), game);
        return game.state();
    }

    public synchronized GameState getState(String gameId, String username) {
        return getAuthorizedGame(gameId, username).state();
    }

    public synchronized SelectionView selectPiece(String gameId, String username, String square) {
        ChessGame game = getAuthorizedGame(gameId, username);
        try {
            return new SelectionView(gameId, square, game.select(square));
        } catch (IllegalArgumentException exception) {
            throw new GameException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    public synchronized MoveResult movePiece(
            String gameId, String username, String from, String to, String promotion) {
        ChessGame game = getAuthorizedGame(gameId, username);
        boolean playerHasTurn = game.whiteToMove()
            ? game.whitePlayer().equals(username)
            : game.blackPlayer().equals(username);
        if (!playerHasTurn) {
            throw new GameException(HttpStatus.FORBIDDEN, "It is not your turn");
        }

        ChessGame.MoveOutcome outcome;
        try {
            outcome = game.move(from, to, promotion);
        } catch (IllegalArgumentException exception) {
            throw new GameException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (outcome.moved()) {
            notifyGameEvent(gameId, "game_updated", outcome.state());
        }
        return new MoveResult(outcome.moved(), outcome.state());
    }

    public synchronized SseEmitter subscribe(String gameId, String username) {
        ChessGame game = getAuthorizedGame(gameId, username);
        SseEmitter emitter = new SseEmitter(0L);
        subscriptions.computeIfAbsent(gameId, ignored -> new ArrayList<>())
            .add(new Subscription(emitter));
        emitter.onCompletion(() -> unsubscribe(gameId, emitter));
        emitter.onTimeout(() -> unsubscribe(gameId, emitter));
        emitter.onError(exception -> unsubscribe(gameId, emitter));
        try {
            emitter.send(SseEmitter.event().name("game_started").data(game.state()));
        } catch (IOException exception) {
            unsubscribe(gameId, emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private synchronized void unsubscribe(String gameId, SseEmitter emitter) {
        List<Subscription> gameSubscriptions = subscriptions.get(gameId);
        if (gameSubscriptions != null) {
            gameSubscriptions.removeIf(subscription -> subscription.emitter == emitter);
            if (gameSubscriptions.isEmpty()) {
                subscriptions.remove(gameId);
            }
        }
    }

    private void notifyGameEvent(String gameId, String eventName, GameState gameState) {
        List<Subscription> gameSubscriptions = subscriptions.get(gameId);
        if (gameSubscriptions == null) {
            return;
        }
        for (Subscription subscription : List.copyOf(gameSubscriptions)) {
            try {
                subscription.emitter.send(SseEmitter.event().name(eventName).data(gameState));
            } catch (IOException exception) {
                subscription.emitter.completeWithError(exception);
            }
        }
    }

    private ChessGame getAuthorizedGame(String gameId, String username) {
        ChessGame game = games.get(gameId);
        if (game == null) {
            throw new GameException(HttpStatus.NOT_FOUND, "Game not found");
        }
        if (!game.players().contains(username)) {
            throw new GameException(HttpStatus.FORBIDDEN, "You are not a player in this game");
        }
        return game;
    }

    public record SelectionView(String gameId, String square, List<String> possibleMoves) {}

    public record MoveResult(boolean moved, GameState gameState) {}

    public record GameState(
            String gameId,
            String roomId,
            List<String> players,
            String whitePlayer,
            List<List<String>> board,
            boolean whiteToMove,
            List<String> previousTurns) {}

    public static class GameException extends RuntimeException {
        private final HttpStatus status;

        public GameException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    private record Subscription(SseEmitter emitter) {}
}
