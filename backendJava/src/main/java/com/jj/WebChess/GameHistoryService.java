package com.jj.WebChess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GameHistoryService {

    public static final int PAGE_SIZE = 10;

    private static final Set<String> COLORS = Set.of("WHITE", "BLACK");
    private static final Set<String> CONDITIONS =
        Set.of("CHECKMATE", "FORFEIT", "STALEMATE", "DRAW");
    private static final DateTimeFormatter PLAYED_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final Pattern PGN_MOVE_NUMBER = Pattern.compile("^\\d+\\.(?:\\.\\.)?$");
    private static final Pattern PGN_COMMENT = Pattern.compile("\\{[^}]*}");

    private final GameHistoryRepository gameHistoryRepository;

    public GameHistoryService(GameHistoryRepository gameHistoryRepository) {
        this.gameHistoryRepository = gameHistoryRepository;
    }

    public HistoryPage getHistory(
            String username, int page, String playerName, LocalDate from, LocalDate to,
            String condition, String color) {
        if (page < 0) {
            throw new HistoryException("Page number must be zero or greater");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new HistoryException("Start date must not be after end date");
        }

        String normalizedColor = normalizeFilter(color, COLORS, "color");
        String normalizedCondition = normalizeFilter(condition, CONDITIONS, "condition");
        String normalizedPlayerName = playerName == null || playerName.isBlank()
            ? null
            : playerName.trim();
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay();

        Page<GameHistoryEntity> result = gameHistoryRepository.findPlayerHistory(
            username, normalizedPlayerName, fromTime, toTime, normalizedColor, normalizedCondition,
            PageRequest.of(page, PAGE_SIZE));
        return new HistoryPage(
            result.getContent().stream().map(GameHistoryEntry::from).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(),
            result.getTotalPages(), result.isFirst(), result.isLast());
    }

    public ReplayView getReplay(String username, String gameId) {
        GameHistoryEntity history = gameHistoryRepository.findPlayerGame(gameId, username)
            .orElseThrow(() -> new HistoryException("Game not found", HttpStatus.NOT_FOUND));
        ChessGame replay = new ChessGame(
            history.getId(), "", List.of(history.getWhiteUsername(), history.getBlackUsername()),
            history.getWhiteUsername(), history.getPlayedAt());
        List<ReplayPosition> positions = new ArrayList<>();
        positions.add(ReplayPosition.from(replay.state()));

        String[] pgnParts = history.getPgn().split("\\R\\s*\\R", 2);
        if (pgnParts.length == 2) {
            String moveText = PGN_COMMENT.matcher(pgnParts[1]).replaceAll(" ");
            for (String token : moveText.trim().split("\\s+")) {
                if (token.isBlank() || PGN_MOVE_NUMBER.matcher(token).matches()
                        || token.matches("\\d+\\.\\.\\.")
                        || Set.of("1-0", "0-1", "1/2-1/2", "*").contains(token)) {
                    continue;
                }
                try {
                    positions.add(ReplayPosition.from(replay.replaySan(token)));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException(
                        "Could not replay stored game " + history.getId(), exception);
                }
            }
        }

        return new ReplayView(
            history.getId(), history.getWhiteUsername(), history.getBlackUsername(),
            history.getResult(), history.getTermination(),
            history.getPlayedAt().format(PLAYED_AT_FORMAT), List.copyOf(positions));
    }

    private String normalizeFilter(String value, Set<String> allowed, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new HistoryException("Invalid " + name + " filter");
        }
        return normalized;
    }

    public record HistoryPage(
            java.util.List<GameHistoryEntry> content,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last) {}

    public record GameHistoryEntry(
            String id,
            String whiteUsername,
            String blackUsername,
            String result,
            String termination,
            String winnerColor,
            String playedAt) {
        private static GameHistoryEntry from(GameHistoryEntity game) {
            String winnerColor = switch (game.getResult()) {
                case "1-0" -> "WHITE";
                case "0-1" -> "BLACK";
                default -> null;
            };
            return new GameHistoryEntry(
                game.getId(), game.getWhiteUsername(), game.getBlackUsername(), game.getResult(),
                game.getTermination(), winnerColor,
                game.getPlayedAt().format(PLAYED_AT_FORMAT));
        }
    }

    public record ReplayView(
            String id,
            String whiteUsername,
            String blackUsername,
            String result,
            String termination,
            String playedAt,
            List<ReplayPosition> positions) {}

    public record ReplayPosition(
            List<List<String>> board, boolean whiteToMove, List<String> previousTurns) {
        private static ReplayPosition from(GameService.GameState state) {
            return new ReplayPosition(state.board(), state.whiteToMove(), state.previousTurns());
        }
    }

    public static class HistoryException extends RuntimeException {
        private final HttpStatus status;

        public HistoryException(String message) {
            this(message, HttpStatus.BAD_REQUEST);
        }

        public HistoryException(String message, HttpStatus status) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }
}
