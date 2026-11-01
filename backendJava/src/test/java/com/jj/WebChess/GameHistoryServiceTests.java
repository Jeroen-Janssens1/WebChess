package com.jj.WebChess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class GameHistoryServiceTests {

    private final GameHistoryRepository repository = mock(GameHistoryRepository.class);
    private final GameHistoryService service = new GameHistoryService(repository);

    @Test
    void historyFiltersAreAppliedToTheAuthenticatedPlayersPaginatedQuery() {
        GameHistoryEntity game = new GameHistoryEntity(
            "game-1", "alice", "bob", "0-1", LocalDateTime.of(2026, 11, 1, 16, 5),
            "[Termination \"FORFEIT\"]\n1. e4 0-1");
        when(repository.findPlayerHistory(
                eq("alice"), eq("bob"), eq(LocalDate.of(2026, 11, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 11, 3).atStartOfDay()), eq("BLACK"), eq("FORFEIT"),
                eq(PageRequest.of(2, 10))))
            .thenReturn(new PageImpl<>(List.of(game), PageRequest.of(2, 10), 21));

        GameHistoryService.HistoryPage result = service.getHistory(
            "alice", 2, " bob ", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2),
            "forfeit", "black");

        verify(repository).findPlayerHistory(
            "alice", "bob", LocalDate.of(2026, 11, 1).atStartOfDay(),
            LocalDate.of(2026, 11, 3).atStartOfDay(), "BLACK", "FORFEIT",
            PageRequest.of(2, 10));
        assertEquals(2, result.number());
        assertEquals(10, result.size());
        assertEquals(21, result.totalElements());
        assertEquals(3, result.totalPages());
        assertEquals("2026-11-01T16:05", result.content().get(0).playedAt());
        assertEquals("BLACK", result.content().get(0).winnerColor());
        assertEquals("FORFEIT", result.content().get(0).termination());
    }

    @Test
    void negativePageAndInvalidDateRangeAreRejected() {
        assertThrows(GameHistoryService.HistoryException.class,
            () -> service.getHistory("alice", -1, null, null, null, null, null));
        assertThrows(GameHistoryService.HistoryException.class,
            () -> service.getHistory("alice", 0, null,
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1), null, null));
    }

    @Test
    void replayReturnsTheInitialBoardAndOnePositionForEveryPgnMove() {
        GameHistoryEntity game = new GameHistoryEntity(
            "game-2", "alice", "bob", "1-0", LocalDateTime.of(2026, 11, 1, 16, 5),
            "[White \"alice\"]\n[Black \"bob\"]\n[Result \"1-0\"]\n\n"
                + "1. e4 e5 2. Nf3 Nc6 1-0");
        when(repository.findPlayerGame("game-2", "alice")).thenReturn(Optional.of(game));

        GameHistoryService.ReplayView replay = service.getReplay("alice", "game-2");

        assertEquals(5, replay.positions().size());
        assertEquals("", replay.positions().get(0).board().get(4).get(4));
        assertEquals("P", replay.positions().get(1).board().get(4).get(4));
        assertEquals("p", replay.positions().get(2).board().get(3).get(4));
        assertEquals(List.of("e4", "e5", "Nf3", "Nc6"),
            replay.positions().get(4).previousTurns());
        assertEquals("alice", replay.whiteUsername());
    }

    @Test
    void replayDoesNotExposeGamesToNonParticipants() {
        when(repository.findPlayerGame("game-3", "mallory")).thenReturn(Optional.empty());

        assertThrows(GameHistoryService.HistoryException.class,
            () -> service.getReplay("mallory", "game-3"));
    }

    @Test
    void replayHandlesCastlingMoves() {
        GameHistoryEntity game = new GameHistoryEntity(
            "game-4", "alice", "bob", "1-0", LocalDateTime.of(2026, 11, 1, 16, 5),
            "[White \"alice\"]\n[Black \"bob\"]\n\n"
                + "1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. O-O 1-0");
        when(repository.findPlayerGame("game-4", "alice")).thenReturn(Optional.of(game));

        GameHistoryService.ReplayView replay = service.getReplay("alice", "game-4");

        GameHistoryService.ReplayPosition afterCastle = replay.positions().get(7);
        assertEquals("K", afterCastle.board().get(7).get(6));
        assertEquals("R", afterCastle.board().get(7).get(5));
        assertEquals("O-O", afterCastle.previousTurns().get(6));
    }

    @Test
    void replayHandlesBlackCheckmateInAHeadToHeadGame() {
        GameHistoryEntity game = new GameHistoryEntity(
            "game-5", "alice", "bob", "0-1", LocalDateTime.of(2026, 11, 1, 16, 5),
            "[White \"alice\"]\n[Black \"bob\"]\n[Result \"0-1\"]\n"
                + "[Termination \"CHECKMATE\"]\n\n"
                + "1. f3 e5 2. g4 Qh4# 0-1");
        when(repository.findPlayerGame("game-5", "alice")).thenReturn(Optional.of(game));

        GameHistoryService.ReplayView replay = service.getReplay("alice", "game-5");

        assertEquals(5, replay.positions().size());
        GameHistoryService.ReplayPosition finalPosition = replay.positions().get(4);
        assertEquals(List.of("f3", "e5", "g4", "Qh4#"), finalPosition.previousTurns());
        assertEquals("q", finalPosition.board().get(4).get(7));
    }

    @Test
    void replayHandlesPawnCaptureWithFileDisambiguationInAHeadToHeadGame() {
        GameHistoryEntity game = new GameHistoryEntity(
            "game-6", "alice", "bob", "1-0", LocalDateTime.of(2026, 11, 1, 16, 5),
            "[White \"alice\"]\n[Black \"bob\"]\n[Result \"1-0\"]\n\n"
                + "1. d4 e5 2. dxe5 1-0");
        when(repository.findPlayerGame("game-6", "alice")).thenReturn(Optional.of(game));

        GameHistoryService.ReplayView replay = service.getReplay("alice", "game-6");

        assertEquals(4, replay.positions().size());
        GameHistoryService.ReplayPosition afterCapture = replay.positions().get(3);
        assertEquals("P", afterCapture.board().get(3).get(4));
        assertEquals(List.of("d4", "e5", "dxe5"), afterCapture.previousTurns());
    }

    @Test
    void replayHandlesFileDisambiguationForPieceMoves() {
        GameHistoryEntity game = new GameHistoryEntity(
            "game-7", "alice", "bob", "1-0", LocalDateTime.of(2026, 11, 1, 16, 5),
            "[White \"alice\"]\n[Black \"bob\"]\n[Result \"1-0\"]\n\n"
                + "1. Nf3 d5 2. d4 Nf6 3. e3 e6 4. Bd3 Bd6 "
                + "5. O-O O-O 6. Nbd2 1-0");
        when(repository.findPlayerGame("game-7", "alice")).thenReturn(Optional.of(game));

        GameHistoryService.ReplayView replay = service.getReplay("alice", "game-7");

        assertEquals(12, replay.positions().size());
        assertEquals("Nbd2", replay.positions().get(11).previousTurns().get(10));
    }
}
