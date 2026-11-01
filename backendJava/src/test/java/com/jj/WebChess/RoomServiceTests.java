package com.jj.WebChess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void roomRequiresCorrectPassword() {
        RoomService.RoomView room = roomService.create("alice", "table one", "secret");

        RoomService.RoomException wrongPassword = assertThrows(RoomService.RoomException.class,
            () -> roomService.join("bob", room.id(), "wrong"));
        assertEquals(HttpStatus.FORBIDDEN, wrongPassword.getStatus());

        RoomService.RoomView joined = roomService.join("bob", room.id(), "secret");
        assertEquals(List.of("alice", "bob"), joined.users());
    }

    @Test
    void roomCannotExceedTwentyMembers() {
        RoomService.RoomView room = roomService.create("host", "twenty player room", null);
        for (int index = 1; index < 20; index++) {
            roomService.join("player" + index, room.id(), null);
        }

        assertEquals(20, roomService.getRoom(room.id(), "host").users().size());
        RoomService.RoomException full = assertThrows(RoomService.RoomException.class,
            () -> roomService.join("player20", room.id(), null));
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
    void hostCanStartGameForRoomMembers() {
        RoomService.RoomView room = roomService.create("alice", "game room", null);
        roomService.join("bob", room.id(), null);

        GameService.GameState game = roomService.startGame(room.id(), "alice");

        assertEquals(room.id(), game.roomId());
        assertEquals(2, game.players().size());
        assertTrue(game.players().containsAll(List.of("alice", "bob")));
        assertTrue(roomService.getRoom(room.id(), "alice").games().stream()
            .anyMatch(roomGame -> roomGame.gameId().equals(game.gameId())));
    }

    @Test
    void finishedGamesAreRemovedFromRoomOpenGames() {
        RoomService.RoomView room = roomService.create("alice", "finished games", null);
        roomService.join("bob", room.id(), null);
        GameService.GameState game = roomService.startGame(room.id(), "alice");

        assertEquals(1, roomService.getRoom(room.id(), "alice").games().size());

        gameService.forfeit(game.gameId(), game.players().get(0));

        assertTrue(roomService.getRoom(room.id(), "alice").games().isEmpty());
    }

    @Test
    void acceptedRematchAppearsAsAnOpenRoomGame() {
        RoomService.RoomView room = roomService.create("alice", "rematch room", null);
        roomService.join("bob", room.id(), null);
        GameService.GameState game = roomService.startGame(room.id(), "alice");
        gameService.forfeit(game.gameId(), game.players().get(0));

        assertFalse(roomService.requestRematch(game.gameId(), game.players().get(0)).started());
        GameService.RematchResult accepted = roomService.requestRematch(
            game.gameId(), game.players().get(1));

        assertTrue(accepted.started());
        assertEquals(List.of(accepted.gameState().gameId()), roomService.getRoom(room.id(), "alice")
            .games().stream().map(GameService.RoomGameView::gameId).toList());
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
    void roomMembersCanRequestMultipleGamesAndMembersCanSpectate() {
        RoomService.RoomView room = roomService.create("alice", "multiple games", null);
        roomService.join("bob", room.id(), null);
        roomService.join("charlie", room.id(), null);
        roomService.join("dana", room.id(), null);

        RoomService.GameRequestView firstRequest = roomService.requestGame(room.id(), "alice", "bob");
        RoomService.GameRequestView secondRequest = roomService.requestGame(room.id(), "charlie", "dana");
        assertTrue(roomService.getRooms().get(0).gameRequests().isEmpty());
        assertTrue(roomService.getRooms().get(0).games().isEmpty());
        GameService.GameState firstGame = roomService.acceptGameRequest(
            room.id(), firstRequest.id(), "bob");
        GameService.GameState secondGame = roomService.acceptGameRequest(
            room.id(), secondRequest.id(), "dana");

        assertNotEquals(firstGame.gameId(), secondGame.gameId());
        assertEquals(2, roomService.getRoom(room.id(), "charlie").games().size());
        assertEquals(firstGame, roomService.getRoomGame(room.id(), firstGame.gameId(), "charlie"));

        RoomService.RoomException nonMember = assertThrows(RoomService.RoomException.class,
            () -> roomService.getRoomGame(room.id(), firstGame.gameId(), "mallory"));
        assertEquals(HttpStatus.FORBIDDEN, nonMember.getStatus());
        GameService.GameException spectatorCannotMove = assertThrows(GameService.GameException.class,
            () -> gameService.movePiece(firstGame.gameId(), "charlie", "e2", "e4", null));
        assertEquals(HttpStatus.FORBIDDEN, spectatorCannotMove.getStatus());
    }

    @Test
    void activePlayerCannotStartAnotherRoomGameUntilTheirCurrentGameEnds() {
        RoomService.RoomView room = roomService.create("alice", "one active game per player", null);
        roomService.join("bob", room.id(), null);
        roomService.join("charlie", room.id(), null);

        RoomService.GameRequestView secondRequest = roomService.requestGame(room.id(), "charlie", "alice");
        RoomService.GameRequestView firstRequest = roomService.requestGame(room.id(), "alice", "bob");
        GameService.GameState activeGame = roomService.acceptGameRequest(
            room.id(), firstRequest.id(), "bob");

        RoomService.RoomException hostStartDenied = assertThrows(RoomService.RoomException.class,
            () -> roomService.startGame(room.id(), "alice"));
        assertEquals(HttpStatus.CONFLICT, hostStartDenied.getStatus());

        RoomService.RoomException acceptanceDenied = assertThrows(RoomService.RoomException.class,
            () -> roomService.acceptGameRequest(room.id(), secondRequest.id(), "alice"));
        assertEquals(HttpStatus.CONFLICT, acceptanceDenied.getStatus());
        assertEquals(1, roomService.getRoom(room.id(), "alice").games().size());
        assertEquals(1, roomService.getRoom(room.id(), "alice").gameRequests().size());

        gameService.forfeit(activeGame.gameId(), activeGame.players().get(0));
        GameService.GameState nextGame = roomService.acceptGameRequest(
            room.id(), secondRequest.id(), "alice");

        assertNotEquals(activeGame.gameId(), nextGame.gameId());
        assertTrue(nextGame.players().containsAll(List.of("alice", "charlie")));
        assertEquals(List.of(nextGame.gameId()), roomService.getRoom(room.id(), "alice")
            .games().stream().map(GameService.RoomGameView::gameId).toList());
    }

    @Test
    void hostCanTransferPrivilegesOnlyToRoomMembers() {
        RoomService.RoomView room = roomService.create("alice", "host transfer", null);
        roomService.join("bob", room.id(), null);

        RoomService.RoomException nonHost = assertThrows(RoomService.RoomException.class,
            () -> roomService.transferHost(room.id(), "bob", "alice"));
        assertEquals(HttpStatus.FORBIDDEN, nonHost.getStatus());
        RoomService.RoomException nonMember = assertThrows(RoomService.RoomException.class,
            () -> roomService.transferHost(room.id(), "alice", "charlie"));
        assertEquals(HttpStatus.BAD_REQUEST, nonMember.getStatus());

        RoomService.RoomView transferred = roomService.transferHost(room.id(), "alice", "bob");
        assertEquals("bob", transferred.creator());
    }

    @Test
    void roomGameContinuesAfterItsRoomIsClosed() {
        RoomService.RoomView room = roomService.create("alice", "independent game", null);
        roomService.join("bob", room.id(), null);
        GameService.GameState game = roomService.startGame(room.id(), "alice");

        assertTrue(roomService.leave(room.id(), "alice"));
        assertEquals(game, gameService.getState(game.gameId(), "bob"));
        assertTrue(gameService.movePiece(
            game.gameId(), game.whitePlayer(), "e2", "e4", null).moved());
    }
}
