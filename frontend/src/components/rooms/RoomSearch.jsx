import { useState } from 'react';

function RoomSearch({ rooms, busy, onBack, onJoin }) {
  const [query, setQuery] = useState('');
  const [hideFull, setHideFull] = useState(true);
  const [hideProtected, setHideProtected] = useState(false);
  const visibleRooms = rooms.filter((room) => {
    const matchesName = room.name?.toLowerCase().includes(query.toLowerCase());
    const isFull = room.users?.length >= 2 || room.gameId;
    return matchesName && !(hideFull && isFull) && !(hideProtected && room.passwordProtected);
  });
  return (
    <div className="page-frame">
      <header className="topbar">
        <button className="back-link" onClick={onBack}>← <span>Main menu</span></button>
        <a className="small-brand" href="#" onClick={(event) => event.preventDefault()}><span>♞</span> WEB CHESS</a>
      </header>
      <section className="content-panel">
        <p className="eyebrow">FIND YOUR TABLE</p>
        <h1>Join a <em>room.</em></h1>
        <p className="muted-copy">Choose a room and take your seat.</p>
        <div className="room-tools">
          <label className="search-input"><span>⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search rooms by name" /></label>
          <label className="check-filter"><input type="checkbox" checked={hideFull} onChange={(event) => setHideFull(event.target.checked)} /><span>Hide full rooms</span></label>
          <label className="check-filter"><input type="checkbox" checked={hideProtected} onChange={(event) => setHideProtected(event.target.checked)} /><span>Hide password rooms</span></label>
        </div>
        <div className="room-list">
          {!visibleRooms.length && <div className="empty-state"><span>♙</span><strong>No rooms to show</strong><p>Try another search or check back in a moment.</p></div>}
          {visibleRooms.map((item) => {
            const full = item.users?.length >= 2 || item.gameId;
            return (
              <article className="room-row" key={item.id}>
                <div className="room-symbol">♟</div>
                <div className="room-meta"><strong>{item.name}</strong><span>Hosted by {item.creator}</span></div>
                <span className="room-count">{item.users?.length || 0} / 2 players</span>
                {item.passwordProtected && <span className="lock-label" title="Password required">⌑</span>}
                <button className="button button-small button-outline" disabled={full || busy} onClick={() => onJoin(item)}>{full ? 'Full' : 'Join'}</button>
              </article>
            );
          })}
        </div>
        <p className="refresh-note"><span className="online-dot" /> Room list refreshes automatically.</p>
      </section>
    </div>
  );
}

export default RoomSearch;
