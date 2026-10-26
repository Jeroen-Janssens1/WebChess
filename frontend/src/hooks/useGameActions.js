import { useCallback } from 'react';
import { api, resolveLatestGame } from '../utils/api.js';

function useGameActions({
  game,
  room,
  setGame,
  setRoom,
  setScreen,
  setSuppressRoomGameId,
  setBusy,
  notify
}) {
  const openLocalGame = async () => {
    setBusy(true);
    try {
      const state = await api('/api/games', { method: 'POST' });
      setGame(state);
      setRoom(null);
      setScreen('game');
    } catch (error) {
      notify(error.message, true);
    } finally {
      setBusy(false);
    }
  };

  const openExistingGame = useCallback(async (gameId) => {
    try {
      const state = await api(`/api/games/${encodeURIComponent(gameId)}`);
      const currentGame = await resolveLatestGame(state);
      setGame(currentGame);
      setScreen('game');
    } catch (error) {
      notify(error.message, true);
    }
  }, [notify, setGame, setScreen]);

  const returnFromGame = () => {
    if (game?.roomId && room) {
      setSuppressRoomGameId(room?.gameId || game.gameId);
      setGame(null);
      setScreen('room');
    } else {
      setRoom(null);
      setScreen('menu');
    }
  };

  const forfeitGame = useCallback(async () => {
    if (!game) return;
    const ended = await api(`/api/games/${encodeURIComponent(game.gameId)}/forfeit`, { method: 'POST' });
    setGame(ended);
    return ended;
  }, [game, setGame]);

  const requestRematch = useCallback(async () => {
    if (!game) return null;
    const response = await api(`/api/games/${encodeURIComponent(game.gameId)}/rematch`, { method: 'POST' });
    setGame(response.gameState);
    if (response.started) setScreen('game');
    return response;
  }, [game, setGame, setScreen]);

  return { openLocalGame, openExistingGame, returnFromGame, forfeitGame, requestRematch };
}

export default useGameActions;
