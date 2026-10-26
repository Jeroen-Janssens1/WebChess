import { initialBoard, pieceGlyphs } from '../../utils/chess.js';

function Welcome({ onAuth, disabled = false }) {
  return (
    <section className="welcome-layout">
      <div className="welcome-copy">
        <div className="brand-mark"><span>♞</span><span>WEB CHESS</span></div>
        <p className="eyebrow">A LITTLE STRATEGY GOES A LONG WAY</p>
        <h1>Make your<br /><em>next move.</em></h1>
        <p className="welcome-description">A quiet corner for great games.</p>
        <div className="welcome-actions">
          <button className="button button-primary" disabled={disabled} onClick={() => onAuth('register')}>Create account <span>↗</span></button>
          <button className="button button-quiet" disabled={disabled} onClick={() => onAuth('login')}>Log in</button>
        </div>
        <p className="welcome-footnote"><span className="online-dot" /> Your board is waiting.</p>
      </div>
      <div className="hero-board-wrap" aria-hidden="true">
        <div className="hero-board">
          {initialBoard.map((row, rowIndex) => row.map((piece, columnIndex) => (
            <div key={`${rowIndex}-${columnIndex}`} className={(rowIndex + columnIndex) % 2 ? 'square dark' : 'square light'}>
              {piece && <span className={`chess-piece ${piece === piece.toUpperCase() ? 'white-piece' : 'black-piece'}`}>{pieceGlyphs[piece]}</span>}
            </div>
          )))}
        </div>
        <div className="board-caption"><span>01 / OPENING</span><span>EST. YOUR OWN PACE</span></div>
      </div>
    </section>
  );
}

export default Welcome;
