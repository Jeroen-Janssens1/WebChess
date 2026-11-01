import { useEffect, useRef, useState } from 'react';
import { api } from '../../utils/api.js';

function RoomLobby({
  room, user, busy, onRoomChange, onLeave, onTransferHost, onRequestGame,
  onAcceptGameRequest, onDeclineGameRequest, onOpenGame, onRoomClosed
}) {
  const [notice, setNotice] = useState('');
  const isHost = room.creator === user;
  const games = (room.games || []).filter((game) => game.status === 'IN_PROGRESS');
  const gameIds = games.map((game) => game.gameId).join('|');
  const knownGameIds = useRef(new Set(games.map((game) => game.gameId)));
  const openGameRef = useRef(onOpenGame);

  useEffect(() => {
    openGameRef.current = onOpenGame;
  }, [onOpenGame]);

  useEffect(() => {
    const newGames = games.filter((game) => !knownGameIds.current.has(game.gameId));
    games.forEach((game) => knownGameIds.current.add(game.gameId));
    const acceptedGame = newGames.find((game) =>
      game.status === 'IN_PROGRESS' && game.players.includes(user));
    if (acceptedGame) openGameRef.current(acceptedGame.gameId);
  }, [gameIds, user]);

  useEffect(() => {
    const refreshRoom = async () => {
      try {
        onRoomChange(await api(`/api/rooms/${encodeURIComponent(room.id)}`));
      } catch (error) {
        setNotice(error.message);
      }
    };
    const stream = new EventSource(`/api/rooms/${encodeURIComponent(room.id)}/events`);
    const applyRoom = (event) => onRoomChange(JSON.parse(event.data));
    stream.addEventListener('room', applyRoom);
    stream.addEventListener('room_updated', applyRoom);
    stream.addEventListener('room_closed', onRoomClosed);
    stream.onerror = () => setNotice('Reconnecting to room…');
    return () => stream.close();
  }, [room.id, onRoomChange, onRoomClosed]);

  useEffect(() => {
    if (!gameIds) return undefined;
    const streams = (room.games || []).map(({ gameId }) => {
      const stream = new EventSource(
        `/api/rooms/${encodeURIComponent(room.id)}/games/${encodeURIComponent(gameId)}/events`
      );
      const refresh = () => api(`/api/rooms/${encodeURIComponent(room.id)}`)
        .then(onRoomChange)
        .catch((error) => setNotice(error.message));
      ['game_started', 'game_updated', 'game_ended', 'rematch_started'].forEach((event) => {
        stream.addEventListener(event, refresh);
      });
      return stream;
    });
    return () => streams.forEach((stream) => stream.close());
  }, [room.id, gameIds, onRoomChange]);

  const acceptRequest = async (requestId) => {
    await onAcceptGameRequest(requestId);
  };

  return (
    <div className="page-frame">
      <header className="topbar">
        <span className="small-brand"><span>♞</span> WEB CHESS</span>
        <span className="live-label"><span className="online-dot" /> ROOM LIVE</span>
      </header>
      <section className="lobby-layout">
        <div>
          <p className="eyebrow">ROOM / {room.id.slice(0, 8).toUpperCase()}</p>
          <h1>{room.name}</h1>
          <p className="muted-copy">Invite any room member to a game, or spectate a game in progress.</p>
          <div className="lobby-section">
            <div className="lobby-section-head">
              <span className="eyebrow">ROOM GAMES</span>
              <span>{games.length} ACTIVE</span>
            </div>
            {!games.length && <p className="waiting-copy">No active games. Choose a member to send a game request.</p>}
            <div className="room-game-list">
              {games.map((game) => {
                const spectating = !game.players.includes(user);
                return (
                  <div className="room-game-row" key={game.gameId}>
                    <div>
                      <strong>{game.players.join(' vs. ')}</strong>
                      <span>In progress</span>
                    </div>
                    <button className="button button-small button-outline" disabled={busy}
                      onClick={() => onOpenGame(game.gameId)}>
                      {spectating ? 'Spectate' : 'Open game'}
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
          <div className="lobby-section">
            <div className="lobby-section-head">
              <span className="eyebrow">GAME REQUESTS</span>
              <span>{room.gameRequests?.length || 0} PENDING</span>
            </div>
            {!room.gameRequests?.length && <p className="waiting-copy">No pending game requests.</p>}
            <div className="room-game-list">
              {(room.gameRequests || []).map((request) => {
                const incoming = request.opponent === user;
                return (
                  <div className="room-game-row" key={request.id}>
                    <div>
                      <strong>{incoming ? request.requester : request.opponent}</strong>
                      <span>{incoming ? 'Invited you to play' : 'Waiting for a response'}</span>
                    </div>
                    {incoming ? (
                      <div className="room-row-actions">
                        <button className="button button-small button-outline" disabled={busy}
                          onClick={() => acceptRequest(request.id)}>Accept</button>
                        <button className="button button-small button-quiet" disabled={busy}
                          onClick={() => onDeclineGameRequest(request.id)}>Decline</button>
                      </div>
                    ) : <span className="room-count">PENDING</span>}
                  </div>
                );
              })}
            </div>
          </div>
        </div>
        <div className="lobby-card">
          <div className="lobby-card-head">
            <div><span className="eyebrow">PLAYERS</span>
              <strong>{room.users?.length || 0} <i>/ 20</i></strong></div>
            <span className="seat-status"><span className="online-dot" /> LIVE</span>
          </div>
          <div className="player-list">
            {(room.users || []).map((name) => (
              <div className="player-row" key={name}>
                <span className="user-avatar large">{name.slice(0, 1).toUpperCase()}</span>
                <span className="player-name">{name}{name === user && <small>YOU</small>}</span>
                {name === room.creator && <span className="host-mark" title="Room host">♔ <span>HOST</span></span>}
                {name !== user && (
                  <div className="room-row-actions">
                    <button className="button button-small button-outline" disabled={busy}
                      onClick={() => onRequestGame(name)}>Play</button>
                    {isHost && <button className="button button-small button-quiet" disabled={busy}
                      onClick={() => onTransferHost(name)}>Make host</button>}
                  </div>
                )}
              </div>
            ))}
          </div>
          <div className="lobby-actions">
            <button className="button button-quiet full-button" disabled={busy} onClick={onLeave}>Leave room</button>
          </div>
        </div>
        {notice && <p className="inline-notice">{notice}</p>}
      </section>
    </div>
  );
}

export default RoomLobby;
