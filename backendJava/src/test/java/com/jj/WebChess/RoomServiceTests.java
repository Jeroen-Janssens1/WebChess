package com.jj.WebChess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RoomServiceTests {

    private final RoomService roomService = new RoomService(new BCryptPasswordEncoder());

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
}