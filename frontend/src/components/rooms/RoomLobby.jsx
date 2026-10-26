import { useEffect, useState } from 'react';

function RoomLobby({ room, user, busy, onRoomChange, onStart, onLeave, onGameReady, onRoomClosed }) {
  const [notice, setNotice] = useState('');
  const isHost = room.creator === user;
  useEffect(() => {
    const stream = new EventSource(`/api/rooms/${encodeURIComponent(room.id)}/events`);
    const applyRoom = (event) => {
      const updated = JSON.parse(event.data);
      onRoomChange(updated);
      if (updated.gameId) onGameReady(updated.gameId);
    };
    stream.addEventListener('room', applyRoom);
    stream.addEventListener('room_updated', applyRoom);
    stream.addEventListener('room_closed', onRoomClosed);
    stream.onerror = () => setNotice('Reconnecting to room…');
    return () => stream.close();
  }, [room.id, onRoomChange, onGameReady, onRoomClosed]);

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
          <p className="muted-copy">Waiting for the host to start the game.</p>
        </div>
        <div className="lobby-card">
          <div className="lobby-card-head"><div><span className="eyebrow">PLAYERS</span><strong>{room.users?.length || 0} <i>/ 2</i></strong></div><span className="seat-status"><span className="online-dot" /> LIVE</span></div>
          <div className="player-list">
            {(room.users || []).map((name) => (
              <div className="player-row" key={name}>
                <span className="user-avatar large">{name.slice(0, 1).toUpperCase()}</span>
                <span className="player-name">{name}{name === user && <small>YOU</small>}</span>
                {name === room.creator && <span className="host-mark" title="Room host">♔ <span>HOST</span></span>}
              </div>
            ))}
            {room.users?.length < 2 && <div className="player-row empty-seat"><span className="user-avatar large">·</span><span>Waiting for a player…</span></div>}
          </div>
          <div className="lobby-actions">
            {isHost ? <button className="button button-primary full-button" disabled={busy || room.users?.length !== 2} onClick={onStart}>Start game <span>↗</span></button>
              : <p className="waiting-copy">The host will start when everyone is ready.</p>}
            <button className="button button-quiet full-button" disabled={busy} onClick={onLeave}>Leave room</button>
          </div>
        </div>
        {notice && <p className="inline-notice">{notice}</p>}
      </section>
    </div>
  );
}

export default RoomLobby;
