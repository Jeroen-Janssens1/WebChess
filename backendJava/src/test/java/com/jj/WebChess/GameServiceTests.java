package com.jj.WebChess;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GameServiceTests {

    private final GameHistoryRepository gameHistoryRepository = mock(GameHistoryRepository.class);
    private final GameService gameService = new GameService(gameHistoryRepository);

    @Test
    void forfeitPersistsGameHistoryWithPgnAndMinutePrecision() {
        GameService.GameState game = startedGame();
        GameService.GameState state = playMove(game, "e2", "e4");
        state = playMove(state, "e7", "e5");

        GameService.GameState ended = gameService.forfeit(state.gameId(), state.whitePlayer());
        ArgumentCaptor<GameHistoryEntity> historyCaptor = ArgumentCaptor.forClass(GameHistoryEntity.class);
        verify(gameHistoryRepository).save(historyCaptor.capture());
        GameHistoryEntity history = historyCaptor.getValue();

        assertEquals(game.gameId(), history.getId());
        assertEquals(game.whitePlayer(), history.getWhiteUsername());
        assertEquals(game.players().stream().filter(player -> !player.equals(game.whitePlayer()))
            .findFirst().orElseThrow(), history.getBlackUsername());
        assertEquals("0-1", history.getResult());
        assertEquals(0, history.getPlayedAt().getSecond());
        assertEquals(0, history.getPlayedAt().getNano());
        assertTrue(history.getPgn().contains("[White \"" + history.getWhiteUsername() + "\"]"));
        assertTrue(history.getPgn().contains("[Black \"" + history.getBlackUsername() + "\"]"));
        assertTrue(history.getPgn().contains("[Result \"0-1\"]"));
        assertTrue(history.getPgn().endsWith("1. e4 e5 0-1"));

        gameService.forfeit(ended.gameId(), ended.winner());
        verify(gameHistoryRepository, times(1)).save(history);
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
    void completedLocalHotSeatGameIsSavedForItsPlayer() {
        GameService.GameState game = gameService.startLocalGame("alice");
        gameService.forfeit(game.gameId(), "alice");

        verify(gameHistoryRepository).save(org.mockito.ArgumentMatchers.argThat(history ->
            history.getId().equals(game.gameId())
                && history.getWhiteUsername().equals("alice")
                && history.getBlackUsername().equals("alice")));
    }

    @Test
    void forfeitEndsGameAndPreventsFurtherMoves() {
        GameService.GameState game = startedGame();
        String forfeitingPlayer = game.whitePlayer();

        GameService.GameState ended = gameService.forfeit(game.gameId(), forfeitingPlayer);
        String expectedWinner = game.players().stream()
            .filter(player -> !player.equals(forfeitingPlayer))
            .findFirst()
            .orElseThrow();

        assertEquals("FINISHED", ended.status());
        assertEquals("FORFEIT", ended.termination());
        assertEquals(expectedWinner, ended.winner());
        GameService.GameException rejectedMove = assertThrows(GameService.GameException.class,
            () -> gameService.movePiece(game.gameId(), ended.winner(), "e7", "e5", null));
        assertEquals(HttpStatus.CONFLICT, rejectedMove.getStatus());
    }

    @Test
    void rematchStartsAfterBothPlayersRequestIt() {
        GameService.GameState game = startedGame();
        gameService.forfeit(game.gameId(), game.whitePlayer());
        String firstPlayer = game.players().get(0);
        String secondPlayer = game.players().get(1);

        GameService.RematchResult waiting = gameService.requestRematch(game.gameId(), firstPlayer);
        assertFalse(waiting.started());
        assertEquals(List.of(firstPlayer), waiting.gameState().rematchRequests());

        GameService.RematchResult started = gameService.requestRematch(game.gameId(), secondPlayer);
        assertTrue(started.started());
        assertNotNull(started.gameState().gameId());
        assertEquals(game.roomId(), started.gameState().roomId());
        assertEquals(game.players(), started.gameState().players());
        assertEquals("IN_PROGRESS", started.gameState().status());
    }

    @Test
    void localPlayerCanImmediatelyStartARematch() {
        GameService.GameState game = gameService.startLocalGame("alice");
        gameService.forfeit(game.gameId(), "alice");

        GameService.RematchResult rematch = gameService.requestRematch(game.gameId(), "alice");

        assertTrue(rematch.started());
        assertNotNull(rematch.gameState().gameId());
        assertFalse(game.gameId().equals(rematch.gameState().gameId()));
        assertEquals("", rematch.gameState().roomId());
        assertEquals("IN_PROGRESS", rematch.gameState().status());
    }

    @Test
    void roomGameStartsWithAValidInitialBoard() {
        GameService.GameState game = startedGame();

        assertNotNull(game.gameId());
        assertEquals(2, game.players().size());
        assertTrue(game.players().contains(game.whitePlayer()));
        assertEquals(8, game.board().size());
        assertEquals(8, game.board().get(0).size());
        assertEquals("r", game.board().get(0).get(0));
        assertEquals("K", game.board().get(7).get(4));
        assertTrue(game.whiteToMove());
        assertTrue(game.previousTurns().isEmpty());
    }

    @Test
    void checkmateFinishesTheGameWithTheCorrectWinner() {
        GameService.GameState state = startedGame();
        state = playMove(state, "e2", "e3");
        state = playMove(state, "f7", "f6");
        state = playMove(state, "d2", "d4");
        state = playMove(state, "g7", "g5");
        state = playMove(state, "d1", "h5");

        assertEquals("FINISHED", state.status());
        assertEquals("CHECKMATE", state.termination());
        assertTrue(state.previousTurns().get(4).endsWith("#"));
        assertEquals(state.whitePlayer(), state.winner());
        GameService.GameState finishedState = state;
        verify(gameHistoryRepository).save(org.mockito.ArgumentMatchers.argThat(history ->
            history.getId().equals(finishedState.gameId())
                && history.getResult().equals("1-0")
                && history.getPgn().endsWith("1. e3 f6 2. d4 g5 3. Qh5# 1-0")));
    }

    @Test
    void selectionReturnsLegalMovesEvenWhenSelectingOutOfTurn() {
        GameService.GameState game = startedGame();
        String playerWithoutTurn = game.whitePlayer().equals("alice") ? "bob" : "alice";

        GameService.SelectionView selection = gameService.selectPiece(
            game.gameId(), playerWithoutTurn, "e2");

        assertEquals(List.of("e3", "e4"), selection.possibleMoves());
    }

    @Test
    void illegalMovePreservesStateAndLegalMoveAdvancesTurn() {
        GameService.GameState game = startedGame();
        String whitePlayer = game.whitePlayer();

        GameService.MoveResult invalid = gameService.movePiece(
            game.gameId(), whitePlayer, "e2", "e5", null);
        assertFalse(invalid.moved());
        assertEquals(game, invalid.gameState());

        GameService.MoveResult moved = gameService.movePiece(
            game.gameId(), whitePlayer, "e2", "e4", null);
        assertTrue(moved.moved());
        assertFalse(moved.gameState().whiteToMove());
        assertEquals(List.of("e4"), moved.gameState().previousTurns());
        assertEquals("P", moved.gameState().board().get(4).get(4));

        GameService.GameException outOfTurn = assertThrows(GameService.GameException.class,
            () -> gameService.movePiece(game.gameId(), whitePlayer, "d2", "d4", null));
        assertEquals(HttpStatus.FORBIDDEN, outOfTurn.getStatus());
    }

    @Test
    void enPassantCapturesThePassedPawn() {
        GameService.GameState state = startedGame();
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
        GameService.GameState state = startedGame();
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
        GameService.GameState state = startedGame();
        state = playMove(state, "a2", "a4");
        state = playMove(state, "a7", "a6");
        state = playMove(state, "a4", "a5");
        state = playMove(state, "b7", "b6");
        state = playMove(state, "a5", "b6");
        state = playMove(state, "a6", "a5");
        state = playMove(state, "b6", "b7");
        state = playMove(state, "h7", "h6");

        GameService.MoveResult result = gameService.movePiece(
            state.gameId(), state.whitePlayer(), "b7", "c8", "n");

        assertTrue(result.moved());
        assertEquals("N", result.gameState().board().get(0).get(2));
        assertEquals("bxc8=N", result.gameState().previousTurns().get(8));
    }

    @Test
    void movesThatLeaveTheKingInCheckAreRejected() {
        GameService.GameState state = startedGame();
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
        GameService.GameState game = startedGame();

        GameService.GameException selectionDenied = assertThrows(GameService.GameException.class,
            () -> gameService.selectPiece(game.gameId(), "mallory", "e2"));
        assertEquals(HttpStatus.FORBIDDEN, selectionDenied.getStatus());
        GameService.GameException moveDenied = assertThrows(GameService.GameException.class,
            () -> gameService.movePiece(game.gameId(), "mallory", "e2", "e4", null));
        assertEquals(HttpStatus.FORBIDDEN, moveDenied.getStatus());
    }

    private GameService.GameState startedGame() {
        return gameService.startRoomGame("test-room", List.of("alice", "bob"));
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
}
