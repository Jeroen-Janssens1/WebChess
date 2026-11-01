import { pieceGlyphs } from '../../utils/chess.js';

function GameHistoryReplay({ user, replay, currentPly, onPlyChange, onBack }) {
  const position = replay.positions[currentPly];
  const isWhite = replay.whiteUsername === user;
  const rows = isWhite ? position.board : [...position.board].reverse().map((row) => [...row].reverse());
  const files = isWhite ? 'abcdefgh'.split('') : 'hgfedcba'.split('');
  const moveCount = replay.positions.length - 1;
  const gameEnded = currentPly === moveCount;

  return (
    <div className="page-frame game-frame">
      <header className="topbar game-topbar">
        <button className="back-link" onClick={onBack}>← <span>Game history</span></button>
        <span className="small-brand"><span>♞</span> WEB CHESS</span>
      </header>
      <section className="game-layout replay-layout">
        <div className="game-title-row">
          <div>
            <p className="eyebrow">GAME REPLAY</p>
            <h1>{replay.whiteUsername} <em>vs.</em> {replay.blackUsername}</h1>
          </div>
          <div className="turn-indicator">
            <span className={`turn-piece ${position.whiteToMove ? 'turn-white' : 'turn-black'}`}>
              {position.whiteToMove ? '♙' : '♟'}
            </span>
            <span>
              <small>{gameEnded ? 'FINAL POSITION' : 'REPLAYING'}</small>
              <strong>{gameEnded ? replay.result : `${position.whiteToMove ? 'White' : 'Black'} to move`}</strong>
            </span>
          </div>
        </div>
        <div className="play-area">
          <div className="board-column">
            <div className="opponent-strip">
              <span className="user-avatar">{replay.blackUsername.slice(0, 1).toUpperCase()}</span>
              <span>{replay.blackUsername}</span>
              <span className="color-tag">BLACK</span>
            </div>
            <div className="board-shell">
              <div className={`chessboard${!isWhite ? ' board-flipped' : ''}`}>
                {rows.map((row, displayRow) => row.map((piece, displayCol) => {
                  const square = `${files[displayCol]}${isWhite ? 8 - displayRow : displayRow + 1}`;
                  const isDark = (displayRow + displayCol) % 2 === 1;
                  return (
                    <div className={`board-square ${isDark ? 'dark-square' : 'light-square'}`}
                      key={square} aria-label={`${square}${piece ? ` ${piece}` : ''}`}>
                      <span className="coordinate">{displayCol === 0 && (isWhite ? 8 - displayRow : displayRow + 1)}</span>
                      {piece && <span className={`board-piece ${piece === piece.toUpperCase() ? 'white-piece' : 'black-piece'}`}>
                        {pieceGlyphs[piece]}
                      </span>}
                      <span className="file-coordinate">{displayRow === 7 && files[displayCol]}</span>
                    </div>
                  );
                }))}
              </div>
            </div>
            <div className="opponent-strip bottom-strip">
              <span className="user-avatar">{replay.whiteUsername.slice(0, 1).toUpperCase()}</span>
              <span>{replay.whiteUsername}</span>
              <span className="color-tag">WHITE</span>
            </div>
          </div>
          <aside className="game-sidebar">
            <div className="sidebar-card">
              <p className="eyebrow">REPLAY POSITION</p>
              <div className="status-callout">
                <span className="status-pip" />
                <strong>{currentPly === 0 ? 'Initial board' : `Move ${currentPly} of ${moveCount}`}</strong>
              </div>
              <p className="status-note">
                {gameEnded
                  ? `${replay.result} · ${replay.termination.toLowerCase()}`
                  : 'Step through the game one move at a time.'}
              </p>
            </div>
            <div className="sidebar-card moves-card">
              <div className="move-history-head">
                <p className="eyebrow">MOVE HISTORY</p>
                <span>{currentPly} / {moveCount}</span>
              </div>
              {!position.previousTurns.length ? <p className="empty-moves">The game starts here.</p> : (
                <ol className="move-history">
                  {Array.from({ length: Math.ceil(position.previousTurns.length / 2) }, (_, index) => (
                    <li key={index}>
                      <span>{index + 1}.</span>
                      <b>{position.previousTurns[index * 2]}</b>
                      <b>{position.previousTurns[index * 2 + 1] || '—'}</b>
                    </li>
                  ))}
                </ol>
              )}
            </div>
            <div className="replay-controls">
              <button className="button button-quiet" disabled={currentPly === 0}
                onClick={() => onPlyChange((ply) => Math.max(0, ply - 1))}>← Previous move</button>
              <button className="button button-primary" disabled={gameEnded}
                onClick={() => onPlyChange((ply) => Math.min(moveCount, ply + 1))}>Next move →</button>
            </div>
          </aside>
        </div>
      </section>
    </div>
  );
}

export default GameHistoryReplay;
