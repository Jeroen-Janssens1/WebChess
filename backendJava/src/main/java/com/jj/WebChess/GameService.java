package com.jj.WebChess;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class GameService {

    private final Map<String, ChessGame> games = new LinkedHashMap<>();
    private final Map<String, List<Subscription>> subscriptions = new HashMap<>();
    private final Set<String> persistedGames = new HashSet<>();
    private final GameHistoryRepository gameHistoryRepository;

    public GameService(GameHistoryRepository gameHistoryRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
    }

    public synchronized GameState startLocalGame(String username) {
        return startGame("", List.of(username), username);
    }

    synchronized GameState startRoomGame(String roomId, List<String> players) {
        if (players.size() != 2) {
            throw new GameException(HttpStatus.BAD_REQUEST, "A room game requires exactly two players");
        }
        String whitePlayer = java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
            ? players.get(0)
            : players.get(1);
        return startGame(roomId, players, whitePlayer);
    }

    private GameState startGame(String roomId, List<String> players, String whitePlayer) {
        LocalDateTime startedAt = LocalDateTime.now().withSecond(0).withNano(0);
        ChessGame game = new ChessGame(
            UUID.randomUUID().toString(), roomId, players, whitePlayer, startedAt);
        games.put(game.gameId(), game);
        return game.state();
    }

    public synchronized GameState getState(String gameId, String username) {
        return getAuthorizedGame(gameId, username).state();
    }

    synchronized GameState getRoomGameState(String roomId, String gameId) {
        ChessGame game = getRoomGame(roomId, gameId);
        return game.state();
    }

    synchronized List<RoomGameView> getRoomGames(String roomId) {
        return games.values().stream()
            .filter(game -> game.roomId().equals(roomId))
            .filter(game -> game.state().status().equals("IN_PROGRESS"))
            .map(game -> new RoomGameView(game.gameId(), game.players(), game.state().status()))
            .toList();
    }

    synchronized boolean hasActiveRoomGame(String roomId, String username) {
        return games.values().stream().anyMatch(game ->
            game.roomId().equals(roomId)
                && game.players().contains(username)
                && game.state().status().equals("IN_PROGRESS"));
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
        if (!game.state().status().equals("IN_PROGRESS")) {
            throw new GameException(HttpStatus.CONFLICT, "The game has already ended");
        }
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
            if (outcome.state().status().equals("FINISHED")) {
                persistCompletedGame(game);
                notifyGameEvent(gameId, "game_ended", outcome.state());
            } else {
                notifyGameEvent(gameId, "game_updated", outcome.state());
            }
        }
        return new MoveResult(outcome.moved(), outcome.state());
    }

    public synchronized GameState forfeit(String gameId, String username) {
        ChessGame game = getAuthorizedGame(gameId, username);
        boolean newlyEnded = game.forfeit(username);
        if (game.state().status().equals("FINISHED")) {
            persistCompletedGame(game);
        }
        if (newlyEnded) {
            notifyGameEvent(gameId, "game_ended", game.state());
        }
        return game.state();
    }

    private void persistCompletedGame(ChessGame game) {
        if (persistedGames.contains(game.gameId())) {
            return;
        }

        GameState state = game.state();
        String result = state.winnerColor() == null ? "1/2-1/2"
            : state.winnerColor().equals("WHITE") ? "1-0" : "0-1";
        LocalDateTime playedAt = game.startedAt();
        String pgn = toPgn(game, state, result, playedAt);
        gameHistoryRepository.save(new GameHistoryEntity(
            game.gameId(), game.whitePlayer(), game.blackPlayer(), result, playedAt, pgn));
        persistedGames.add(game.gameId());
    }

    private String toPgn(ChessGame game, GameState state, String result, LocalDateTime playedAt) {
        StringBuilder pgn = new StringBuilder();
        appendPgnTag(pgn, "Event", "WebChess");
        appendPgnTag(pgn, "Site", "WebChess");
        appendPgnTag(pgn, "Date", playedAt.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        appendPgnTag(pgn, "Round", "-");
        appendPgnTag(pgn, "White", game.whitePlayer());
        appendPgnTag(pgn, "Black", game.blackPlayer());
        appendPgnTag(pgn, "Result", result);
        appendPgnTag(pgn, "Termination", state.termination());
        pgn.append('\n');

        List<String> turns = state.previousTurns();
        for (int index = 0; index < turns.size(); index += 2) {
            if (index > 0) {
                pgn.append(' ');
            }
            pgn.append(index / 2 + 1).append(". ").append(turns.get(index));
            if (index + 1 < turns.size()) {
                pgn.append(' ').append(turns.get(index + 1));
            }
        }
        if (!turns.isEmpty()) {
            pgn.append(' ');
        }
        return pgn.append(result).toString();
    }

    private void appendPgnTag(StringBuilder pgn, String name, String value) {
        pgn.append('[').append(name).append(" \"")
            .append(value.replace("\\", "\\\\").replace("\"", "\\\""))
            .append("\"]\n");
    }

    public synchronized RematchResult requestRematch(String gameId, String username) {
        ChessGame game = getAuthorizedGame(gameId, username);
        if (!game.state().status().equals("FINISHED")) {
            throw new GameException(HttpStatus.CONFLICT, "A rematch is only available after the game ends");
        }
        if (game.rematchGameId() != null) {
            return new RematchResult(true, getAuthorizedGame(game.rematchGameId(), username).state());
        }

        game.requestRematch(username);
        if (game.allPlayersRequestedRematch()) {
            List<String> players = game.players();
            String whitePlayer = players.size() == 1
                ? players.get(0)
                : java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                    ? players.get(0)
                    : players.get(1);
            GameState rematch = startGame(game.roomId(), players, whitePlayer);
            game.setRematchGameId(rematch.gameId());
            notifyGameEvent(gameId, "rematch_started", rematch);
            return new RematchResult(true, rematch);
        }

        notifyGameEvent(gameId, "rematch_requested", game.state());
        return new RematchResult(false, game.state());
    }

    public synchronized SseEmitter subscribe(String gameId, String username) {
        ChessGame game = getAuthorizedGame(gameId, username);
        return subscribeToGame(gameId, game, username, false);
    }

    synchronized SseEmitter subscribeToRoomGame(String roomId, String gameId, String username) {
        return subscribeToGame(gameId, getRoomGame(roomId, gameId), username, true);
    }

    synchronized void unsubscribeRoomMember(String roomId, String username) {
        unsubscribeRoomSubscriptions(roomId, username);
    }

    synchronized void unsubscribeRoomMembers(String roomId) {
        unsubscribeRoomSubscriptions(roomId, null);
    }

    private SseEmitter subscribeToGame(
            String gameId, ChessGame game, String username, boolean roomScoped) {
        SseEmitter emitter = new SseEmitter(0L);
        subscriptions.computeIfAbsent(gameId, ignored -> new ArrayList<>())
            .add(new Subscription(username, roomScoped, emitter));
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

    private void unsubscribeRoomSubscriptions(String roomId, String username) {
        for (ChessGame game : games.values()) {
            if (!game.roomId().equals(roomId)) {
                continue;
            }
            List<Subscription> gameSubscriptions = subscriptions.get(game.gameId());
            if (gameSubscriptions == null) {
                continue;
            }
            for (Subscription subscription : List.copyOf(gameSubscriptions)) {
                if (subscription.roomScoped
                        && (username == null || subscription.username.equals(username))) {
                    subscription.emitter.complete();
                }
            }
        }
    }

    private ChessGame getRoomGame(String roomId, String gameId) {
        ChessGame game = games.get(gameId);
        if (game == null || !game.roomId().equals(roomId)) {
            throw new GameException(HttpStatus.NOT_FOUND, "Room game not found");
        }
        return game;
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

    public record RematchResult(boolean started, GameState gameState) {}

    public record RoomGameView(String gameId, List<String> players, String status) {}

    public record GameState(
            String gameId,
            String roomId,
            List<String> players,
            String whitePlayer,
            List<List<String>> board,
            boolean whiteToMove,
            List<String> previousTurns,
            String status,
            String winner,
            String winnerColor,
            String termination,
            List<String> rematchRequests,
            String rematchGameId) {}

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

    private record Subscription(String username, boolean roomScoped, SseEmitter emitter) {}
}
