import { useEffect, useRef, useState } from 'react';
import { api, resolveLatestGame } from '../utils/api.js';
import { clearSavedSession, readSavedSession, saveSession } from '../utils/sessionStorage.js';

function useSessionState({
  screen,
  room,
  game,
  suppressRoomGameId,
  setUser,
  setRoom,
  setGame,
  setScreen,
  setSuppressRoomGameId
}) {
  const restoringSession = useRef(true);
  const [restoring, setRestoring] = useState(true);
  const preserveSavedSession = useRef(false);

  useEffect(() => {
    let active = true;
    const restore = async () => {
      const saved = readSavedSession();
      let result;
      try {
        result = await api('/api/user');
      } catch (error) {
        if (active) {
          setUser(null);
          setRoom(null);
          setGame(null);
          setScreen('welcome');
          if (error.status === 401) {
            clearSavedSession();
          } else {
            preserveSavedSession.current = true;
          }
        }
        return;
      }

      if (!active) return;
      const username = result.user?.username || null;
      setUser(username);
      if (!username) {
        setScreen('welcome');
        clearSavedSession();
        return;
      }

      if (saved?.gameId) {
        let savedGame;
        try {
          savedGame = await api(`/api/games/${encodeURIComponent(saved.gameId)}`);
        } catch (error) {
          if (error.status !== 404) {
            if (active) {
              preserveSavedSession.current = true;
              setRoom(null);
              setGame(null);
              setScreen('menu');
            }
            return;
          }
        }

        if (savedGame) {
          let currentGame;
          try {
            currentGame = await resolveLatestGame(savedGame);
          } catch {
            if (active) {
              preserveSavedSession.current = true;
              setRoom(null);
              setGame(null);
              setScreen('menu');
            }
            return;
          }
          if (!active) return;
          let restoredRoom = null;
          if (currentGame.roomId) {
            try {
              const roomList = await api('/api/rooms');
              const rooms = Array.isArray(roomList) ? roomList : roomList.rooms || [];
              restoredRoom = rooms.find((candidate) => candidate.id === currentGame.roomId) || null;
            } catch {
              restoredRoom = null;
            }
          }
          if (!active) return;
          setGame(currentGame);
          setRoom(restoredRoom);
          setScreen('game');
          return;
        }
      }

      if (saved?.roomId) {
        let roomList;
        try {
          roomList = await api('/api/rooms');
        } catch {
          if (active) {
            preserveSavedSession.current = true;
            setRoom(null);
            setGame(null);
            setScreen('menu');
          }
          return;
        }
        if (!active) return;
        const rooms = Array.isArray(roomList) ? roomList : roomList.rooms || [];
        const restoredRoom = rooms.find((candidate) =>
          candidate.id === saved.roomId && candidate.users?.includes(username));
        if (restoredRoom) {
          if (restoredRoom.gameId) {
            try {
              const previousGame = await api(`/api/games/${encodeURIComponent(restoredRoom.gameId)}`);
              const activeGame = await resolveLatestGame(previousGame);
              if (activeGame.status !== 'FINISHED') {
                if (!active) return;
                setGame(activeGame);
                setRoom(restoredRoom);
                setScreen('game');
                return;
              }
            } catch {
              // The room remains recoverable even if its game is no longer available.
            }
          }
          if (!active) return;
          setRoom(restoredRoom);
          setSuppressRoomGameId(saved.suppressedGameId || restoredRoom.gameId || null);
          setScreen('room');
          return;
        }
      }
      if (saved?.gameId) clearSavedSession();
      setRoom(null);
      setGame(null);
      setScreen('menu');
    };
    restore()
      .catch((error) => {
        console.error('Unable to restore the Web Chess session.', error);
        if (active) {
          preserveSavedSession.current = true;
          setRoom(null);
          setGame(null);
          setScreen('menu');
        }
      })
      .finally(() => {
        if (active) {
          restoringSession.current = false;
          setRestoring(false);
        }
      });
    return () => { active = false; };
  }, [setGame, setRoom, setScreen, setSuppressRoomGameId, setUser]);

  useEffect(() => {
    if (restoringSession.current) return;
    if (preserveSavedSession.current) {
      preserveSavedSession.current = false;
      return;
    }
    if (screen === 'room' && room) {
      saveSession({
        roomId: room.id,
        suppressedGameId: suppressRoomGameId || room.gameId || null
      });
    } else if (screen === 'game' && game) {
      saveSession({
        gameId: game.gameId,
        roomId: game.roomId || ''
      });
    } else {
      clearSavedSession();
    }
  }, [screen, room, game, suppressRoomGameId, restoring]);

  return restoring;
}

export default useSessionState;
