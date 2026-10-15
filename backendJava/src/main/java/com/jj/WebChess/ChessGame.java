package com.jj.WebChess;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ChessGame {

    private final String gameId;
    private final String roomId;
    private final List<String> players;
    private final String whitePlayer;
    private final char[][] board = {
        {'r', 'n', 'b', 'q', 'k', 'b', 'n', 'r'},
        {'p', 'p', 'p', 'p', 'p', 'p', 'p', 'p'},
        {'.', '.', '.', '.', '.', '.', '.', '.'},
        {'.', '.', '.', '.', '.', '.', '.', '.'},
        {'.', '.', '.', '.', '.', '.', '.', '.'},
        {'.', '.', '.', '.', '.', '.', '.', '.'},
        {'P', 'P', 'P', 'P', 'P', 'P', 'P', 'P'},
        {'R', 'N', 'B', 'Q', 'K', 'B', 'N', 'R'}
    };
    private final List<String> previousTurns = new ArrayList<>();
    private boolean whiteToMove = true;
    private boolean whiteKingSideCastle = true;
    private boolean whiteQueenSideCastle = true;
    private boolean blackKingSideCastle = true;
    private boolean blackQueenSideCastle = true;
    private int enPassantRow = -1;
    private int enPassantColumn = -1;
    private String status = "IN_PROGRESS";
    private String winner;
    private String winnerColor;
    private String termination;
    private final Set<String> rematchRequests = new LinkedHashSet<>();
    private String rematchGameId;

    ChessGame(String gameId, String roomId, List<String> players, String whitePlayer) {
        this.gameId = gameId;
        this.roomId = roomId;
        this.players = List.copyOf(players);
        this.whitePlayer = whitePlayer;
    }

    GameService.GameState state() {
        List<List<String>> boardView = new ArrayList<>(8);
        for (char[] row : board) {
            List<String> rowView = new ArrayList<>(8);
            for (char piece : row) {
                rowView.add(piece == '.' ? "" : String.valueOf(piece));
            }
            boardView.add(List.copyOf(rowView));
        }
        return new GameService.GameState(
            gameId, roomId, players, whitePlayer, List.copyOf(boardView), whiteToMove,
            List.copyOf(previousTurns), status, winner, winnerColor, termination,
            List.copyOf(rematchRequests), rematchGameId);
    }

    String gameId() {
        return gameId;
    }

    String roomId() {
        return roomId;
    }

    List<String> players() {
        return players;
    }

    String whitePlayer() {
        return whitePlayer;
    }

    String blackPlayer() {
        return players.size() == 1
            ? players.get(0)
            : players.get(0).equals(whitePlayer) ? players.get(1) : players.get(0);
    }

    boolean whiteToMove() {
        return whiteToMove;
    }

    List<String> select(String square) {
        int[] position = parseSquare(square);
        char piece = board[position[0]][position[1]];
        if (piece == '.') {
            return List.of();
        }
        boolean white = Character.isUpperCase(piece);
        List<String> destinations = new ArrayList<>();
        for (Move move : legalMoves(white)) {
            if (move.fromRow == position[0] && move.fromColumn == position[1]) {
                destinations.add(toSquare(move.toRow, move.toColumn));
            }
        }
        return List.copyOf(destinations);
    }

    MoveOutcome move(String from, String to, String promotion) {
        if (!status.equals("IN_PROGRESS")) {
            return new MoveOutcome(false, state());
        }
        int[] source = parseSquare(from);
        int[] destination = parseSquare(to);
        Character promotionPiece = parsePromotion(promotion);
        char piece = board[source[0]][source[1]];
        if (piece == '.' || Character.isUpperCase(piece) != whiteToMove) {
            return new MoveOutcome(false, state());
        }
        if (promotionPiece != null && Character.toLowerCase(piece) != 'p') {
            return new MoveOutcome(false, state());
        }
        List<Move> candidates = legalMoves(whiteToMove);
        Move chosen = candidates.stream()
            .filter(candidate -> candidate.fromRow == source[0]
                && candidate.fromColumn == source[1]
                && candidate.toRow == destination[0]
                && candidate.toColumn == destination[1])
            .findFirst()
            .orElse(null);
        if (chosen == null) {
            return new MoveOutcome(false, state());
        }

        char movingPiece = board[chosen.fromRow][chosen.fromColumn];
        char capturedPiece = board[chosen.toRow][chosen.toColumn];
        boolean enPassantCapture = Character.toLowerCase(movingPiece) == 'p'
            && chosen.fromColumn != chosen.toColumn
            && capturedPiece == '.'
            && chosen.toRow == enPassantRow
            && chosen.toColumn == enPassantColumn;
        boolean capture = capturedPiece != '.' || enPassantCapture;
        String notation = notation(chosen, candidates, movingPiece, capture, promotionPiece);

        updateCastlingRights(chosen, movingPiece, capturedPiece);
        applyMove(board, chosen, enPassantRow, enPassantColumn, promotionPiece);
        enPassantRow = -1;
        enPassantColumn = -1;
        if (Character.toLowerCase(movingPiece) == 'p' && Math.abs(chosen.toRow - chosen.fromRow) == 2) {
            enPassantRow = (chosen.toRow + chosen.fromRow) / 2;
            enPassantColumn = chosen.fromColumn;
        }
        whiteToMove = !whiteToMove;

        boolean opponentInCheck = isKingInCheck(board, whiteToMove);
        List<Move> opponentMoves = legalMoves(whiteToMove);
        if (opponentInCheck) {
            notation += opponentMoves.isEmpty() ? "#" : "+";
        }
        if (opponentMoves.isEmpty()) {
            status = "FINISHED";
            if (opponentInCheck) {
                termination = "CHECKMATE";
                winnerColor = whiteToMove ? "BLACK" : "WHITE";
                winner = whiteToMove ? blackPlayer() : whitePlayer();
            } else {
                termination = "STALEMATE";
            }
        }
        previousTurns.add(notation);
        return new MoveOutcome(true, state());
    }

    boolean forfeit(String username) {
        if (!status.equals("IN_PROGRESS") || !players.contains(username)) {
            return false;
        }
        status = "FINISHED";
        termination = "FORFEIT";
        if (players.size() == 1) {
            winnerColor = whiteToMove ? "BLACK" : "WHITE";
            winner = username;
        } else if (username.equals(whitePlayer)) {
            winnerColor = "BLACK";
            winner = blackPlayer();
        } else {
            winnerColor = "WHITE";
            winner = whitePlayer;
        }
        return true;
    }

    boolean requestRematch(String username) {
        return players.contains(username) && rematchRequests.add(username);
    }

    boolean allPlayersRequestedRematch() {
        return rematchRequests.containsAll(players);
    }

    void setRematchGameId(String rematchGameId) {
        this.rematchGameId = rematchGameId;
    }

    String rematchGameId() {
        return rematchGameId;
    }

    private List<Move> legalMoves(boolean white) {
        List<Move> legal = new ArrayList<>();
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                char piece = board[row][column];
                if (piece == '.' || Character.isUpperCase(piece) != white) {
                    continue;
                }
                for (Move move : pseudoMoves(row, column, white)) {
                    char[][] movedBoard = copyBoard(board);
                    applyMove(movedBoard, move, enPassantRow, enPassantColumn, null);
                    if (!isKingInCheck(movedBoard, white)) {
                        legal.add(move);
                    }
                }
            }
        }
        return legal;
    }

    private List<Move> pseudoMoves(int row, int column, boolean white) {
        char piece = board[row][column];
        char type = Character.toLowerCase(piece);
        List<Move> moves = new ArrayList<>();
        if (type == 'p') {
            int direction = white ? -1 : 1;
            int startRow = white ? 6 : 1;
            int nextRow = row + direction;
            if (inside(nextRow, column) && board[nextRow][column] == '.') {
                moves.add(new Move(row, column, nextRow, column));
                int doubleRow = row + 2 * direction;
                if (row == startRow && board[doubleRow][column] == '.') {
                    moves.add(new Move(row, column, doubleRow, column));
                }
            }
            for (int deltaColumn : new int[] {-1, 1}) {
                int targetColumn = column + deltaColumn;
                if (!inside(nextRow, targetColumn)) {
                    continue;
                }
                char target = board[nextRow][targetColumn];
                if (target != '.' && Character.isUpperCase(target) != white
                        && Character.toLowerCase(target) != 'k') {
                    moves.add(new Move(row, column, nextRow, targetColumn));
                } else if (white == whiteToMove
                        && nextRow == enPassantRow && targetColumn == enPassantColumn) {
                    moves.add(new Move(row, column, nextRow, targetColumn));
                }
            }
        } else if (type == 'n') {
            for (int[] offset : new int[][] {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                {1, -2}, {1, 2}, {2, -1}, {2, 1}
            }) {
                addIfAvailable(moves, row, column, row + offset[0], column + offset[1], white);
            }
        } else if (type == 'b' || type == 'r' || type == 'q') {
            int[][] directions = type == 'b'
                ? new int[][] {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}}
                : type == 'r'
                    ? new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}
                    : new int[][] {{-1, -1}, {-1, 1}, {1, -1}, {1, 1},
                        {-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] direction : directions) {
                int targetRow = row + direction[0];
                int targetColumn = column + direction[1];
                while (inside(targetRow, targetColumn)) {
                    char target = board[targetRow][targetColumn];
                    if (target == '.') {
                        moves.add(new Move(row, column, targetRow, targetColumn));
                    } else {
                        if (Character.isUpperCase(target) != white
                                && Character.toLowerCase(target) != 'k') {
                            moves.add(new Move(row, column, targetRow, targetColumn));
                        }
                        break;
                    }
                    targetRow += direction[0];
                    targetColumn += direction[1];
                }
            }
        } else if (type == 'k') {
            for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                    if (rowOffset != 0 || columnOffset != 0) {
                        addIfAvailable(moves, row, column,
                            row + rowOffset, column + columnOffset, white);
                    }
                }
            }
            addCastlingMoves(moves, row, column, white);
        }
        return moves;
    }

    private void addCastlingMoves(List<Move> moves, int row, int column, boolean white) {
        int homeRow = white ? 7 : 0;
        char rook = white ? 'R' : 'r';
        if (row != homeRow || column != 4 || isSquareAttacked(board, row, column, !white)) {
            return;
        }
        boolean kingSide = white ? whiteKingSideCastle : blackKingSideCastle;
        if (kingSide && board[homeRow][7] == rook
                && board[homeRow][5] == '.' && board[homeRow][6] == '.'
                && !isSquareAttacked(board, homeRow, 5, !white)
                && !isSquareAttacked(board, homeRow, 6, !white)) {
            moves.add(new Move(homeRow, 4, homeRow, 6));
        }
        boolean queenSide = white ? whiteQueenSideCastle : blackQueenSideCastle;
        if (queenSide && board[homeRow][0] == rook
                && board[homeRow][1] == '.' && board[homeRow][2] == '.' && board[homeRow][3] == '.'
                && !isSquareAttacked(board, homeRow, 3, !white)
                && !isSquareAttacked(board, homeRow, 2, !white)) {
            moves.add(new Move(homeRow, 4, homeRow, 2));
        }
    }

    private void addIfAvailable(List<Move> moves, int fromRow, int fromColumn,
            int toRow, int toColumn, boolean white) {
        if (!inside(toRow, toColumn)) {
            return;
        }
        char target = board[toRow][toColumn];
        if (target == '.' || (Character.isUpperCase(target) != white
                && Character.toLowerCase(target) != 'k')) {
            moves.add(new Move(fromRow, fromColumn, toRow, toColumn));
        }
    }

    private String notation(Move move, List<Move> candidates, char piece, boolean capture, Character promotion) {
        if (Character.toLowerCase(piece) == 'k' && Math.abs(move.toColumn - move.fromColumn) == 2) {
            return move.toColumn > move.fromColumn ? "O-O" : "O-O-O";
        }
        StringBuilder notation = new StringBuilder();
        char type = Character.toUpperCase(piece);
        if (type != 'P') {
            notation.append(type);
            List<Move> alternatives = candidates.stream()
                .filter(other -> other != move
                    && other.toRow == move.toRow && other.toColumn == move.toColumn
                    && board[other.fromRow][other.fromColumn] == piece)
                .toList();
            if (!alternatives.isEmpty()) {
                boolean sameFile = alternatives.stream().anyMatch(other -> other.fromColumn == move.fromColumn);
                boolean sameRank = alternatives.stream().anyMatch(other -> other.fromRow == move.fromRow);
                if (!sameFile) {
                    notation.append((char) ('a' + move.fromColumn));
                } else if (!sameRank) {
                    notation.append((char) ('8' - move.fromRow));
                } else {
                    notation.append((char) ('a' + move.fromColumn));
                    notation.append((char) ('8' - move.fromRow));
                }
            }
        } else if (capture) {
            notation.append((char) ('a' + move.fromColumn));
        }
        if (capture) {
            notation.append('x');
        }
        notation.append(toSquare(move.toRow, move.toColumn));
        if (promotion != null || (type == 'P' && (move.toRow == 0 || move.toRow == 7))) {
            char promoted = promotion == null ? (Character.isUpperCase(piece) ? 'Q' : 'q') : promotion;
            notation.append('=').append(Character.toUpperCase(promoted));
        }
        return notation.toString();
    }

    private void updateCastlingRights(Move move, char piece, char capturedPiece) {
        char type = Character.toLowerCase(piece);
        boolean white = Character.isUpperCase(piece);
        if (type == 'k') {
            if (white) {
                whiteKingSideCastle = false;
                whiteQueenSideCastle = false;
            } else {
                blackKingSideCastle = false;
                blackQueenSideCastle = false;
            }
        }
        if (type == 'r') {
            disableRookRight(move.fromRow, move.fromColumn, white);
        }
        if (Character.toLowerCase(capturedPiece) == 'r') {
            disableRookRight(move.toRow, move.toColumn, Character.isUpperCase(capturedPiece));
        }
    }

    private void disableRookRight(int row, int column, boolean white) {
        int homeRow = white ? 7 : 0;
        if (row != homeRow) {
            return;
        }
        if (column == 0) {
            if (white) {
                whiteQueenSideCastle = false;
            } else {
                blackQueenSideCastle = false;
            }
        } else if (column == 7) {
            if (white) {
                whiteKingSideCastle = false;
            } else {
                blackKingSideCastle = false;
            }
        }
    }

    private static void applyMove(char[][] position, Move move, int epRow, int epColumn, Character promotion) {
        char piece = position[move.fromRow][move.fromColumn];
        if (Character.toLowerCase(piece) == 'p'
                && move.fromColumn != move.toColumn
                && position[move.toRow][move.toColumn] == '.'
                && move.toRow == epRow && move.toColumn == epColumn) {
            position[move.fromRow][move.toColumn] = '.';
        }
        position[move.fromRow][move.fromColumn] = '.';
        if (Character.toLowerCase(piece) == 'k' && Math.abs(move.toColumn - move.fromColumn) == 2) {
            int rookFrom = move.toColumn > move.fromColumn ? 7 : 0;
            int rookTo = move.toColumn > move.fromColumn ? 5 : 3;
            position[move.fromRow][rookTo] = position[move.fromRow][rookFrom];
            position[move.fromRow][rookFrom] = '.';
        }
        if (Character.toLowerCase(piece) == 'p' && (move.toRow == 0 || move.toRow == 7)) {
            char promotedPiece = promotion == null
                ? 'q'
                : Character.toLowerCase(promotion);
            position[move.toRow][move.toColumn] = Character.isUpperCase(piece)
                ? Character.toUpperCase(promotedPiece)
                : promotedPiece;
        } else {
            position[move.toRow][move.toColumn] = piece;
        }
    }

    private static boolean isKingInCheck(char[][] position, boolean white) {
        char king = white ? 'K' : 'k';
        for (int row = 0; row < 8; row++) {
            for (int column = 0; column < 8; column++) {
                if (position[row][column] == king) {
                    return isSquareAttacked(position, row, column, !white);
                }
            }
        }
        return true;
    }

    private static boolean isSquareAttacked(char[][] position, int row, int column, boolean byWhite) {
        int pawnRow = row + (byWhite ? 1 : -1);
        char pawn = byWhite ? 'P' : 'p';
        for (int pawnColumn : new int[] {column - 1, column + 1}) {
            if (inside(pawnRow, pawnColumn) && position[pawnRow][pawnColumn] == pawn) {
                return true;
            }
        }
        char knight = byWhite ? 'N' : 'n';
        for (int[] offset : new int[][] {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
            {1, -2}, {1, 2}, {2, -1}, {2, 1}
        }) {
            int targetRow = row + offset[0];
            int targetColumn = column + offset[1];
            if (inside(targetRow, targetColumn) && position[targetRow][targetColumn] == knight) {
                return true;
            }
        }
        char king = byWhite ? 'K' : 'k';
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                if ((rowOffset != 0 || columnOffset != 0)
                        && inside(row + rowOffset, column + columnOffset)
                        && position[row + rowOffset][column + columnOffset] == king) {
                    return true;
                }
            }
        }
        return rayAttacked(position, row, column, byWhite, true)
            || rayAttacked(position, row, column, byWhite, false);
    }

    private static boolean rayAttacked(char[][] position, int row, int column, boolean byWhite, boolean diagonal) {
        int[][] directions = diagonal
            ? new int[][] {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}}
            : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        char bishop = byWhite ? 'B' : 'b';
        char rook = byWhite ? 'R' : 'r';
        char queen = byWhite ? 'Q' : 'q';
        for (int[] direction : directions) {
            int targetRow = row + direction[0];
            int targetColumn = column + direction[1];
            while (inside(targetRow, targetColumn)) {
                char piece = position[targetRow][targetColumn];
                if (piece != '.') {
                    if (piece == queen || (diagonal && piece == bishop) || (!diagonal && piece == rook)) {
                        return true;
                    }
                    break;
                }
                targetRow += direction[0];
                targetColumn += direction[1];
            }
        }
        return false;
    }

    private static char[][] copyBoard(char[][] source) {
        char[][] copy = new char[8][];
        for (int row = 0; row < 8; row++) {
            copy[row] = source[row].clone();
        }
        return copy;
    }

    private static Character parsePromotion(String promotion) {
        if (promotion == null || promotion.isBlank()) {
            return null;
        }
        if (promotion.length() != 1 || "qrbnQRBN".indexOf(promotion.charAt(0)) < 0) {
            throw new IllegalArgumentException("Promotion must be one of q, r, b, or n");
        }
        return Character.toLowerCase(promotion.charAt(0));
    }

    private static int[] parseSquare(String square) {
        if (square == null || !square.matches("[a-hA-H][1-8]")) {
            throw new IllegalArgumentException("Square must be in algebraic form, for example e2");
        }
        return new int[] {'8' - square.charAt(1), Character.toLowerCase(square.charAt(0)) - 'a'};
    }

    private static String toSquare(int row, int column) {
        return new String(new char[] {(char) ('a' + column), (char) ('8' - row)});
    }

    private static boolean inside(int row, int column) {
        return row >= 0 && row < 8 && column >= 0 && column < 8;
    }

    record MoveOutcome(boolean moved, GameService.GameState state) {}

    private record Move(int fromRow, int fromColumn, int toRow, int toColumn) {}
}
