import { useEffect, useRef, useState } from 'react';
import Modal from '../Modal.jsx';
import PromotionModal from './PromotionModal.jsx';
import { api, resolveLatestGame } from '../../utils/api.js';
import { pieceGlyphs } from '../../utils/chess.js';

function GameScreen({
  initialGame, user, hasRoom, onGameChange, onBack, onForfeit, onRematch,
  notify, gameError, setGameError
}) {
  const [game, setGame] = useState(initialGame);
  const [selected, setSelected] = useState(null);
  const [legalMoves, setLegalMoves] = useState([]);
  const [busy, setBusy] = useState(false);
  const [dragPosition, setDragPosition] = useState(null);
  const [promotionTarget, setPromotionTarget] = useState(null);
  const [confirmForfeit, setConfirmForfeit] = useState(false);
  const [rematchError, setRematchError] = useState('');
  const boardRef = useRef(null);
  const gesture = useRef(null);
  const suppressClick = useRef(false);
  const isLocal = !game.roomId;
  const isWhite = game.players.length === 1 || game.whitePlayer === user;
  const sideToMovePlayer = game.whiteToMove ? game.whitePlayer
    : game.players.find((player) => player !== game.whitePlayer);
  const opponentName = isLocal ? 'Black side' : game.players.find((player) => player !== user);
  const selfName = isLocal ? 'White side' : user;
  const isYourTurn = isLocal || sideToMovePlayer === user;
  const turnColor = game.whiteToMove ? 'White' : 'Black';
  const gameEnded = game.status === 'FINISHED' || game.previousTurns?.at(-1)?.endsWith('#');
  const rematchRequested = game.rematchRequests?.includes(user);
  const didWin = game.winnerColor ? (game.winnerColor === 'WHITE') === isWhite : false;

  useEffect(() => {
    let alive = true;
    const applyState = async (state) => {
      const current = await resolveLatestGame(state);
      if (alive) updateGame(current);
    };
    api(`/api/games/${encodeURIComponent(initialGame.gameId)}`)
      .then(applyState)
      .catch((error) => { if (alive) setGameError(error.message); });
    const stream = new EventSource(`/api/games/${encodeURIComponent(initialGame.gameId)}/events`);
    const update = (event) => applyState(JSON.parse(event.data))
      .catch((error) => { if (alive) setGameError(error.message); });
    stream.addEventListener('game_started', update);
    stream.addEventListener('game_updated', update);
    stream.addEventListener('game_ended', update);
    stream.addEventListener('rematch_requested', update);
    stream.addEventListener('rematch_started', update);
    stream.onopen = () => alive && setGameError('');
    stream.onerror = () => alive && setGameError('Connection interrupted. Reconnecting…');
    return () => {
      alive = false;
      stream.close();
    };
  }, [initialGame.gameId, setGameError]);

  function updateGame(state) {
    setGame(state);
    onGameChange(state);
    setSelected(null);
    setLegalMoves([]);
  }

  const selectSquare = async (square) => {
    if (busy || gameEnded) return [];
    setBusy(true);
    try {
      const selection = await api(`/api/games/${encodeURIComponent(game.gameId)}/select`, {
        method: 'POST',
        body: JSON.stringify({ square })
      });
      setSelected(square);
      setLegalMoves(selection.possibleMoves || []);
      return selection.possibleMoves || [];
    } catch (error) {
      notify(error.message, true);
      setSelected(null);
      setLegalMoves([]);
      return [];
    } finally {
      setBusy(false);
    }
  };

  const moveTo = async (to, promotion = null, fromSquare = selected) => {
    if (!fromSquare || busy || !isYourTurn || gameEnded) return;
    const from = fromSquare;
    const movingPiece = game.board[8 - Number(from[1])][from.charCodeAt(0) - 97];
    const reachesLastRank = movingPiece?.toLowerCase() === 'p'
      && ((movingPiece === movingPiece.toUpperCase() && to[1] === '8')
        || (movingPiece === movingPiece.toLowerCase() && to[1] === '1'));
    if (reachesLastRank && !promotion) {
      setPromotionTarget(to);
      return;
    }
    setBusy(true);
    try {
      const result = await api(`/api/games/${encodeURIComponent(game.gameId)}/move`, {
        method: 'POST',
        body: JSON.stringify({ from, to, promotion })
      });
      if (result.moved) updateGame(result.gameState);
      else {
        setSelected(null);
        setLegalMoves([]);
      }
    } catch (error) {
      notify(error.message, true);
      setSelected(null);
      setLegalMoves([]);
    } finally {
      setBusy(false);
    }
  };

  const squareAtPoint = (x, y) => document.elementFromPoint(x, y)?.closest('[data-square]')?.dataset.square;
  const handlePointerDown = (event, square, piece) => {
    if (event.button !== 0 || !piece || busy || gameEnded) return;
    if (selected && legalMoves.includes(square)) return;
    const canSelect = isLocal || (piece === piece.toUpperCase()) === isWhite;
    if (!canSelect) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    const selectPromise = selectSquare(square);
    gesture.current = {
      from: square,
      startX: event.clientX,
      startY: event.clientY,
      dragging: false,
      selectPromise
    };
  };
  const handlePointerMove = (event) => {
    if (!gesture.current) return;
    const distance = Math.hypot(event.clientX - gesture.current.startX, event.clientY - gesture.current.startY);
    if (distance > 5) gesture.current.dragging = true;
    if (gesture.current.dragging) setDragPosition({ x: event.clientX, y: event.clientY });
  };
  const handlePointerUp = (event) => {
    if (!gesture.current) return;
    const current = gesture.current;
    gesture.current = null;
    setDragPosition(null);
    suppressClick.current = true;
    window.setTimeout(() => { suppressClick.current = false; }, 0);
    if (!current.dragging) return;
    const destination = squareAtPoint(event.clientX, event.clientY);
    if (destination && destination !== current.from) {
      current.selectPromise.then((moves) => {
        if (moves.includes(destination)) moveTo(destination, null, current.from);
      });
    }
  };
  const handleSquareClick = (square, piece) => {
    if (suppressClick.current) return;
    if (selected && legalMoves.includes(square)) {
      moveTo(square);
      return;
    }
    if (selected === square) {
      setSelected(null);
      setLegalMoves([]);
      return;
    }
    const canSelect = piece && (isLocal || (piece === piece.toUpperCase()) === isWhite);
    if (canSelect && !gameEnded) {
      selectSquare(square);
    }
  };

  const forfeit = async () => {
    setConfirmForfeit(false);
    try {
      const ended = await onForfeit();
      updateGame(ended);
    } catch (error) {
      notify(error.message, true);
    }
  };

  const rematch = async () => {
    setRematchError('');
    try {
      const response = await onRematch();
      if (response) updateGame(response.gameState);
    } catch (error) {
      setRematchError(error.message);
    }
  };

  const rows = isWhite
    ? game.board
    : [...game.board].reverse().map((row) => [...row].reverse());
  const files = isWhite ? 'abcdefgh'.split('') : 'hgfedcba'.split('');
  const endTitle = game.termination === 'CHECKMATE'
    ? (isLocal ? 'Checkmate.' : didWin ? 'A beautiful win.' : 'Game over.')
    : game.termination === 'FORFEIT'
      ? (isLocal ? 'Game forfeited.' : didWin ? 'Your opponent forfeited.' : 'You forfeited.')
      : 'Draw.';
  const endCopy = game.termination === 'CHECKMATE'
    ? `${game.winnerColor || (game.previousTurns?.length % 2 ? 'White' : 'Black')} wins by checkmate.`
    : game.termination === 'FORFEIT'
      ? (isLocal ? 'The local game was forfeited.' : `${game.winner || 'Your opponent'} wins by forfeit.`)
      : 'The game ended in a draw by stalemate.';

  return (
    <div className="page-frame game-frame">
      <header className="topbar game-topbar">
        <span className="game-type-label">{isLocal ? 'LOCAL BOARD' : 'ROOM BOARD'}</span>
        <a className="small-brand" href="#" onClick={(event) => event.preventDefault()}><span>♞</span> WEB CHESS</a>
      </header>
      <section className="game-layout">
        <div className="game-title-row">
          <div><p className="eyebrow">{isLocal ? 'LOCAL GAME' : 'ROOM GAME'}</p><h1>The board is <em>yours.</em></h1></div>
          <div className={`turn-indicator${isYourTurn && !gameEnded ? ' your-turn' : ''}`}><span className={`turn-piece ${game.whiteToMove ? 'turn-white' : 'turn-black'}`}>{game.whiteToMove ? '♙' : '♟'}</span><span><small>{gameEnded ? 'GAME OVER' : isYourTurn ? 'YOUR TURN' : 'OPPONENT’S TURN'}</small><strong>{gameEnded ? endTitle : `${turnColor} to move`}</strong></span></div>
        </div>
        <div className="play-area">
          <div className="board-column">
            <div className="opponent-strip">
              <span className="user-avatar">{opponentName?.slice(0, 1).toUpperCase() || '♟'}</span>
              <span>{opponentName}</span>
              <span className="color-tag">{isWhite ? 'BLACK' : 'WHITE'}</span>
            </div>
            <div className="board-shell" ref={boardRef} onPointerMove={handlePointerMove} onPointerUp={handlePointerUp} onPointerCancel={handlePointerUp} onContextMenu={(event) => { event.preventDefault(); setSelected(null); setLegalMoves([]); }}>
              <div className={`chessboard${!isWhite ? ' board-flipped' : ''}`}>
                {rows.map((row, displayRow) => row.map((piece, displayCol) => {
                  const square = `${files[displayCol]}${isWhite ? 8 - displayRow : displayRow + 1}`;
                  const isDark = (displayRow + displayCol) % 2 === 1;
                  const canMove = legalMoves.includes(square);
                  return (
                    <button type="button" key={square} data-square={square}
                      className={`board-square ${isDark ? 'dark-square' : 'light-square'}${selected === square ? ' selected-square' : ''}${canMove ? ' legal-square' : ''}`}
                      aria-label={`${square}${piece ? ` ${piece}` : ''}${canMove ? ', legal move' : ''}`}
                      onPointerDown={(event) => handlePointerDown(event, square, piece)}
                      onClick={() => handleSquareClick(square, piece)}>
                      <span className="coordinate">{displayCol === 0 && (isWhite ? 8 - displayRow : displayRow + 1)}</span>
                      {piece && <span className={`board-piece ${piece === piece.toUpperCase() ? 'white-piece' : 'black-piece'}${selected === square && dragPosition ? ' dragged-origin' : ''}`}>{pieceGlyphs[piece]}</span>}
                      {canMove && <span className={`move-dot${piece ? ' capture-dot' : ''}`} />}
                      <span className="file-coordinate">{displayRow === 7 && files[displayCol]}</span>
                    </button>
                  );
                }))}
              </div>
              {dragPosition && selected && (
                <span className="drag-piece" style={{ left: dragPosition.x, top: dragPosition.y }}>
                  {pieceGlyphs[game.board[8 - Number(selected[1])][selected.charCodeAt(0) - 97]]}
                </span>
              )}
            </div>
            <div className="opponent-strip bottom-strip">
              <span className="user-avatar">{selfName?.slice(0, 1).toUpperCase() || '♙'}</span>
              <span>{selfName}</span>
              <span className="color-tag">{isWhite ? 'WHITE' : 'BLACK'}</span>
            </div>
          </div>
          <aside className="game-sidebar">
            <div className="sidebar-card">
              <p className="eyebrow">GAME STATUS</p>
              <div className={`status-callout${isYourTurn && !gameEnded ? ' status-active' : ''}`}><span className="status-pip" /><strong>{gameEnded ? endTitle : isYourTurn ? 'Your move' : `${turnColor} to move`}</strong></div>
              <p className="status-note">{gameEnded ? endCopy : isLocal ? 'Pass the device after each turn.' : isYourTurn ? 'Choose a piece to see its legal moves.' : 'Select one of your pieces to preview its moves.'}</p>
            </div>
            <div className="sidebar-card moves-card">
              <div className="move-history-head"><p className="eyebrow">MOVE HISTORY</p><span>{game.previousTurns?.length || 0} PLY</span></div>
              {!game.previousTurns?.length ? <p className="empty-moves">Your first move will appear here.</p> : (
                <ol className="move-history">
                  {Array.from({ length: Math.ceil(game.previousTurns.length / 2) }, (_, index) => (
                    <li key={index}><span>{index + 1}.</span><b>{game.previousTurns[index * 2]}</b><b>{game.previousTurns[index * 2 + 1] || '—'}</b></li>
                  ))}
                </ol>
              )}
            </div>
            {gameError && <p className="connection-note">{gameError}</p>}
            {!gameEnded && <button className="button button-quiet full-button" onClick={() => setConfirmForfeit(true)}>Forfeit game</button>}
          </aside>
        </div>
      </section>
      {gameEnded && <Modal title={endTitle} onClose={() => {}} showClose={false}>
        <p className="modal-copy">{endCopy}</p>
        {game.players.length > 1 && <p className="modal-copy rematch-status">
          {rematchRequested
            ? 'You asked for a rematch. Waiting for the other player…'
            : game.rematchRequests?.length
              ? `${game.rematchRequests.join(' and ')} asked for a rematch.`
              : 'Would you like to play again?'}
        </p>}
        {rematchError && <p className="form-error" role="alert">{rematchError}</p>}
        <button className="button button-primary full-button"
          disabled={!isLocal && game.players.length > 1 && rematchRequested}
          onClick={rematch}>
          {!isLocal && game.players.length > 1 && rematchRequested ? 'Waiting for opponent…' : 'Rematch'} <span>↻</span>
        </button>
        <button className="button button-quiet full-button end-return-button" onClick={onBack}>
          {game.roomId && hasRoom ? 'Return to room' : 'Back to main menu'} <span>↗</span>
        </button>
      </Modal>}
      {promotionTarget && <PromotionModal isWhite={game.board[8 - Number(selected[1])][selected.charCodeAt(0) - 97] === 'P'}
        onChoose={(promotion) => {
          const target = promotionTarget;
          setPromotionTarget(null);
          moveTo(target, promotion);
        }}
        onClose={() => setPromotionTarget(null)} />}
      {confirmForfeit && <Modal title="Forfeit this game?" onClose={() => setConfirmForfeit(false)}>
        <p className="modal-copy">The game will end immediately. Your opponent will be awarded the win.</p>
        <button className="button button-danger full-button" onClick={forfeit}>Confirm forfeit</button>
        <button className="button button-quiet full-button end-return-button" onClick={() => setConfirmForfeit(false)}>Keep playing</button>
      </Modal>}
    </div>
  );
}

export default GameScreen;
