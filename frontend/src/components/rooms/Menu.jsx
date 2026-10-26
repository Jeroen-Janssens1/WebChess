function Menu({ user, busy, onLocal, onCreate, onJoin, onLogout }) {
  return (
    <div className="page-frame">
      <header className="topbar">
        <a className="small-brand" href="#" onClick={(event) => event.preventDefault()}><span>♞</span> WEB CHESS</a>
        <div className="user-chip"><span className="user-avatar">{user?.slice(0, 1)?.toUpperCase()}</span><span>{user}</span></div>
      </header>
      <section className="menu-layout">
        <div className="menu-heading">
          <p className="eyebrow">YOUR NEXT MOVE STARTS HERE</p>
          <h1>Good to see you,<br /><em>{user || 'player'}.</em></h1>
          <p>Choose a board and settle in.</p>
        </div>
        <nav className="menu-actions" aria-label="Main menu">
          <button className="menu-action featured" onClick={onLocal} disabled={busy}>
            <span className="menu-action-icon">♟</span><span><strong>Start Local Game</strong><small>One device. Two sides. Your move.</small></span><b>↗</b>
          </button>
          <button className="menu-action" onClick={onCreate}><span className="menu-action-icon">＋</span><span><strong>Create Room</strong><small>Set up a private table for a friend.</small></span><b>↗</b></button>
          <button className="menu-action" onClick={onJoin}><span className="menu-action-icon">⌕</span><span><strong>Join Room</strong><small>Find an open table and play.</small></span><b>↗</b></button>
          <button className="logout-link" onClick={onLogout} disabled={busy}>Log out <span>↗</span></button>
        </nav>
        <div className="menu-side-note"><span>♔</span><p>“Every chess master was once a beginner.”</p><small>— Irving Chernev</small></div>
      </section>
    </div>
  );
}

export default Menu;
