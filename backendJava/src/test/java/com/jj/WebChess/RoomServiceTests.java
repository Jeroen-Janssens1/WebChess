package com.jj.WebChess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RoomServiceTests {

    private final GameService gameService = new GameService();
    private final RoomService roomService = new RoomService(new BCryptPasswordEncoder(), gameService);

    @Test
    void roomRequiresCorrectPasswordAndCannotExceedCapacity() {
        RoomService.RoomView room = roomService.create("alice", "table one", "secret");

        RoomService.RoomException wrongPassword = assertThrows(RoomService.RoomException.class,
            () -> roomService.join("bob", room.id(), "wrong"));
        assertEquals(HttpStatus.FORBIDDEN, wrongPassword.getStatus());

        RoomService.RoomView joined = roomService.join("bob", "table one", "secret");
        assertEquals(2, joined.users().size());

        RoomService.RoomException full = assertThrows(RoomService.RoomException.class,
            () -> roomService.join("charlie", room.id(), "secret"));
        assertEquals(HttpStatus.CONFLICT, full.getStatus());
    }

    @Test
    void creatorLeavingClosesRoomAndRegularMemberCanLeave() {
        RoomService.RoomView room = roomService.create("alice", "open table", null);
        roomService.join("bob", room.id(), null);

        assertFalse(roomService.leave(room.id(), "bob"));
        assertTrue(roomService.leave(room.id(), "alice"));

        RoomService.RoomException removed = assertThrows(RoomService.RoomException.class,
            () -> roomService.join("charlie", room.id(), null));
        assertEquals(HttpStatus.NOT_FOUND, removed.getStatus());
    }

    @Test
    void hostCanStartGameForBothRoomMembers() {
        RoomService.RoomView room = roomService.create("alice", "game room", null);
        roomService.join("bob", room.id(), null);

        GameService.GameState game = roomService.startGame(room.id(), "alice");

        assertNotNull(game.gameId());
        assertEquals(room.id(), game.roomId());
        assertEquals(2, game.players().size());
        assertTrue(game.players().containsAll(List.of("alice", "bob")));
        assertTrue(game.players().contains(game.whitePlayer()));
        assertEquals(8, game.board().size());
        assertEquals(8, game.board().get(0).size());
        assertEquals("r", game.board().get(0).get(0));
        assertEquals("K", game.board().get(7).get(4));
        assertTrue(game.whiteToMove());
        assertTrue(game.previousTurns().isEmpty());
    }

    @Test
    void onlyHostCanStartAndRoomMustHaveTwoPlayers() {
        RoomService.RoomView room = roomService.create("alice", "waiting room", null);

        RoomService.RoomException missingPlayer = assertThrows(RoomService.RoomException.class,
            () -> roomService.startGame(room.id(), "alice"));
        assertEquals(HttpStatus.CONFLICT, missingPlayer.getStatus());

        roomService.join("bob", room.id(), null);
        RoomService.RoomException notHost = assertThrows(RoomService.RoomException.class,
            () -> roomService.startGame(room.id(), "bob"));
        assertEquals(HttpStatus.FORBIDDEN, notHost.getStatus());
    }

    @Test
    void roomCannotStartMoreThanOneGame() {
        RoomService.RoomView room = roomService.create("alice", "one game", null);
        roomService.join("bob", room.id(), null);
        roomService.startGame(room.id(), "alice");

        RoomService.RoomException alreadyStarted = assertThrows(RoomService.RoomException.class,
            () -> roomService.startGame(room.id(), "alice"));
        assertEquals(HttpStatus.CONFLICT, alreadyStarted.getStatus());
    }

    @Test
    void localGameSupportsHotSeatPlayAndIsIndependentOfRooms() {
        GameService.GameState game = gameService.startLocalGame("alice");

        assertEquals("", game.roomId());
        assertEquals(List.of("alice"), game.players());

        GameService.MoveResult whiteMove = gameService.movePiece(
            game.gameId(), "alice", "e2", "e4", null);
        assertTrue(whiteMove.moved());
        GameService.MoveResult blackMove = gameService.movePiece(
            game.gameId(), "alice", "e7", "e5", null);
        assertTrue(blackMove.moved());
        assertTrue(blackMove.gameState().whiteToMove());
        assertEquals(List.of("e4", "e5"), blackMove.gameState().previousTurns());
    }

    @Test
    void roomGameContinuesAfterItsRoomIsClosed() {
        StartedGame game = startedGame("independent game");

        assertTrue(roomService.leave(game.roomId(), "alice"));
        assertEquals(game.state(), gameService.getState(game.state().gameId(), "bob"));
        assertTrue(gameService.movePiece(
            game.state().gameId(), game.state().whitePlayer(), "e2", "e4", null).moved());
    }

    @Test
    void selectionReturnsLegalMovesEvenWhenSelectingOutOfTurn() {
        StartedGame game = startedGame("select game");
        String playerWithoutTurn = game.state().whitePlayer().equals("alice") ? "bob" : "alice";

        GameService.SelectionView selection = gameService.selectPiece(
            game.state().gameId(), playerWithoutTurn, "e2");

        assertEquals(List.of("e3", "e4"), selection.possibleMoves());
    }

    @Test
    void illegalMovePreservesStateAndLegalMoveAdvancesTurn() {
        StartedGame game = startedGame("move game");
        String whitePlayer = game.state().whitePlayer();

        GameService.MoveResult invalid = gameService.movePiece(
            game.state().gameId(), whitePlayer, "e2", "e5", null);
        assertFalse(invalid.moved());
        assertEquals(game.state(), invalid.gameState());

        GameService.MoveResult moved = gameService.movePiece(
            game.state().gameId(), whitePlayer, "e2", "e4", null);
        assertTrue(moved.moved());
        assertFalse(moved.gameState().whiteToMove());
        assertEquals(List.of("e4"), moved.gameState().previousTurns());
        assertEquals("P", moved.gameState().board().get(4).get(4));

        GameService.GameException outOfTurn = assertThrows(GameService.GameException.class,
            () -> gameService.movePiece(
                game.state().gameId(), whitePlayer, "d2", "d4", null));
        assertEquals(HttpStatus.FORBIDDEN, outOfTurn.getStatus());
    }

    @Test
    void enPassantCapturesThePassedPawn() {
        StartedGame game = startedGame("en passant game");
        GameService.GameState state = game.state();
        state = playMove(state, "e2", "e4");
        state = playMove(state, "a7", "a6");
        state = playMove(state, "e4", "e5");
        state = playMove(state, "d7", "d5");
        state = playMove(state, "e5", "d6");

        assertEquals("P", state.board().get(2).get(3));
        assertEquals("", state.board().get(3).get(3));
        assertEquals("exd6", state.previousTurns().get(4));
    }

    @Test
    void castlingMovesKingAndRookAndRecordsChessNotation() {
        StartedGame game = startedGame("castling game");
        GameService.GameState state = game.state();
        state = playMove(state, "g1", "f3");
        state = playMove(state, "a7", "a6");
        state = playMove(state, "e2", "e4");
        state = playMove(state, "a6", "a5");
        state = playMove(state, "f1", "e2");
        state = playMove(state, "b7", "b6");
        state = playMove(state, "e1", "g1");

        assertEquals("K", state.board().get(7).get(6));
        assertEquals("R", state.board().get(7).get(5));
        assertEquals("O-O", state.previousTurns().get(6));
    }

    @Test
    void promotionUsesRequestedPieceAndChessNotation() {
        StartedGame game = startedGame("promotion game");
        GameService.GameState state = game.state();
        state = playMove(state, "a2", "a4");
        state = playMove(state, "a7", "a6");
        state = playMove(state, "a4", "a5");
        state = playMove(state, "b7", "b6");
        state = playMove(state, "a5", "b6");
        state = playMove(state, "a6", "a5");
        state = playMove(state, "b6", "b7");
        state = playMove(state, "h7", "h6");

        String whitePlayer = state.whitePlayer();
        GameService.MoveResult result = gameService.movePiece(
            state.gameId(), whitePlayer, "b7", "c8", "n");

        assertTrue(result.moved());
        assertEquals("N", result.gameState().board().get(0).get(2));
        assertEquals("bxc8=N", result.gameState().previousTurns().get(8));
    }

    @Test
    void movesThatLeaveTheKingInCheckAreRejected() {
        StartedGame game = startedGame("check game");
        GameService.GameState state = game.state();
        state = playMove(state, "e2", "e4");
        state = playMove(state, "f7", "f6");
        state = playMove(state, "d1", "h5");

        GameService.GameState checkedState = state;
        String blackPlayer = checkedState.players().stream()
            .filter(player -> !player.equals(checkedState.whitePlayer()))
            .findFirst()
            .orElseThrow();
        GameService.MoveResult invalidResponse = gameService.movePiece(
            checkedState.gameId(), blackPlayer, "a7", "a6", null);

        assertFalse(invalidResponse.moved());
        assertEquals(checkedState, invalidResponse.gameState());
    }

    @Test
    void nonPlayerCannotSelectOrMovePieces() {
        StartedGame game = startedGame("private game");

        GameService.GameException selectionDenied = assertThrows(GameService.GameException.class,
            () -> gameService.selectPiece(game.state().gameId(), "mallory", "e2"));
        assertEquals(HttpStatus.FORBIDDEN, selectionDenied.getStatus());
        GameService.GameException moveDenied = assertThrows(GameService.GameException.class,
            () -> gameService.movePiece(game.state().gameId(), "mallory", "e2", "e4", null));
        assertEquals(HttpStatus.FORBIDDEN, moveDenied.getStatus());
    }

    private StartedGame startedGame(String roomName) {
        RoomService.RoomView room = roomService.create("alice", roomName, null);
        roomService.join("bob", room.id(), null);
        return new StartedGame(room.id(), roomService.startGame(room.id(), "alice"));
    }

    private GameService.GameState playMove(GameService.GameState state, String from, String to) {
        String username;
        if (state.whiteToMove()) {
            username = state.whitePlayer();
        } else {
            username = state.players().stream()
                .filter(player -> !player.equals(state.whitePlayer()))
                .findFirst()
                .orElseThrow();
        }
        GameService.MoveResult result = gameService.movePiece(state.gameId(), username, from, to, null);
        assertTrue(result.moved(), from + " to " + to + " should be legal");
        return result.gameState();
    }

    private record StartedGame(String roomId, GameService.GameState state) {}
}