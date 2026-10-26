import { useCallback, useEffect } from 'react';
import { api } from '../utils/api.js';
import { clearSavedSession } from '../utils/sessionStorage.js';

function useRoomActions({
  room,
  screen,
  setRooms,
  setRoom,
  setGame,
  setScreen,
  setModal,
  setSuppressRoomGameId,
  setBusy,
  notify
}) {
  const loadRooms = useCallback(async () => {
    const result = await api('/api/rooms');
    setRooms(Array.isArray(result) ? result : result.rooms || []);
  }, [setRooms]);

  useEffect(() => {
    if (screen !== 'rooms') return undefined;
    let active = true;
    const refresh = () => loadRooms().catch((error) => active && notify(error.message, true));
    refresh();
    const timer = window.setInterval(refresh, 5000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [screen, loadRooms, notify]);

  const createRoom = async (name, password) => {
    setBusy(true);
    try {
      const created = await api('/api/rooms', {
        method: 'POST',
        body: JSON.stringify({ name, password: password || null })
      });
      setRoom(created);
      setScreen('room');
      setModal(null);
    } catch (error) {
      notify(error.message, true);
    } finally {
      setBusy(false);
    }
  };

  const joinRoom = async (roomInfo, password) => {
    setBusy(true);
    try {
      const joined = await api('/api/rooms/join', {
        method: 'POST',
        body: JSON.stringify({ roomIdOrName: roomInfo.id, password: password || null })
      });
      setRoom(joined);
      setScreen('room');
      setModal(null);
      setSuppressRoomGameId(null);
    } catch (error) {
      notify(error.message, true);
      throw error;
    } finally {
      setBusy(false);
    }
  };

  const startRoomGame = async () => {
    if (!room) return;
    setBusy(true);
    try {
      const started = await api(`/api/rooms/${encodeURIComponent(room.id)}/games`, { method: 'POST' });
      setGame(started);
      setScreen('game');
    } catch (error) {
      notify(error.message, true);
    } finally {
      setBusy(false);
    }
  };

  const leaveRoom = async () => {
    if (!room) return;
    setBusy(true);
    try {
      await api(`/api/rooms/${encodeURIComponent(room.id)}/members/me`, { method: 'DELETE' });
      setRoom(null);
      setGame(null);
      setScreen('menu');
    } catch (error) {
      notify(error.message, true);
    } finally {
      setBusy(false);
    }
  };

  const handleRoomClosed = useCallback(() => {
    setRoom(null);
    setGame(null);
    setScreen('menu');
    clearSavedSession();
    notify('The room was closed by its host.');
  }, [notify, setGame, setRoom, setScreen]);

  return { createRoom, joinRoom, startRoomGame, leaveRoom, handleRoomClosed };
}

export default useRoomActions;
