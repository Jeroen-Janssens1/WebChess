import { useEffect, useState } from 'react';
import { api } from '../../utils/api.js';
import GameHistoryReplay from './GameHistoryReplay.jsx';

const initialFilters = {
  playerName: '',
  from: '',
  to: '',
  condition: '',
  color: ''
};

function describeOutcome(game) {
  if (game.result === '1/2-1/2') {
    return game.termination === 'STALEMATE' ? 'Draw by stalemate' : 'Draw';
  }
  const winner = game.winnerColor === 'WHITE' ? 'White' : 'Black';
  const method = game.termination === 'CHECKMATE' ? 'by checkmate'
    : game.termination === 'FORFEIT' ? 'by forfeit' : '';
  return `${winner} won${method ? ` ${method}` : ''}`;
}

function GameHistory({ user, onBack }) {
  const [filters, setFilters] = useState(initialFilters);
  const [appliedFilters, setAppliedFilters] = useState(initialFilters);
  const [page, setPage] = useState(0);
  const [history, setHistory] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [selectedGameId, setSelectedGameId] = useState(null);
  const [replay, setReplay] = useState(null);
  const [replayError, setReplayError] = useState('');
  const [currentPly, setCurrentPly] = useState(0);

  useEffect(() => {
    let active = true;
    const loadHistory = async () => {
      setLoading(true);
      setError('');
      const params = new URLSearchParams({ page: String(page) });
      Object.entries(appliedFilters).forEach(([key, value]) => {
        if (value) params.set(key, value);
      });
      try {
        const result = await api(`/api/games/history?${params.toString()}`);
        if (active) setHistory(result);
      } catch (loadError) {
        if (active) setError(loadError.message);
      } finally {
        if (active) setLoading(false);
      }
    };
    loadHistory();
    return () => { active = false; };
  }, [page, appliedFilters]);

  useEffect(() => {
    if (!selectedGameId) return undefined;
    let active = true;
    setReplay(null);
    setReplayError('');
    setCurrentPly(0);
    api(`/api/games/history/${encodeURIComponent(selectedGameId)}`)
      .then((result) => { if (active) setReplay(result); })
      .catch((loadError) => { if (active) setReplayError(loadError.message); });
    return () => { active = false; };
  }, [selectedGameId]);

  const setFilter = (key, value) => {
    setFilters((current) => ({ ...current, [key]: value }));
  };

  const applyFilters = (event) => {
    event.preventDefault();
    setPage(0);
    setAppliedFilters({ ...filters });
  };

  const clearFilters = () => {
    setFilters(initialFilters);
    setAppliedFilters(initialFilters);
    setPage(0);
  };

  if (selectedGameId) {
    if (replay) {
      return <GameHistoryReplay user={user} replay={replay} currentPly={currentPly}
        onPlyChange={setCurrentPly} onBack={() => setSelectedGameId(null)} />;
    }
    return (
      <div className="page-frame">
        <header className="topbar">
          <button className="back-link" onClick={() => setSelectedGameId(null)}>← <span>Game history</span></button>
          <span className="small-brand"><span>♞</span> WEB CHESS</span>
        </header>
        <section className="content-panel history-panel">
          {replayError
            ? <p className="history-error" role="alert">{replayError}</p>
            : <p className="waiting-copy">Loading game replay…</p>}
        </section>
      </div>
    );
  }

  return (
    <div className="page-frame">
      <header className="topbar">
        <button className="back-link" onClick={onBack}>← <span>Main menu</span></button>
        <span className="small-brand"><span>♞</span> WEB CHESS</span>
      </header>
      <section className="content-panel history-panel">
        <p className="eyebrow">YOUR RECORD</p>
        <h1>Game <em>history.</em></h1>
        <p className="muted-copy">Completed games played by {user}.</p>

        <form className="history-filters" onSubmit={applyFilters}>
          <label>
            <span>Player name</span>
            <input value={filters.playerName} onChange={(event) => setFilter('playerName', event.target.value)}
              placeholder="Search either player" />
          </label>
          <label>
            <span>Played from</span>
            <input type="date" value={filters.from} onChange={(event) => setFilter('from', event.target.value)} />
          </label>
          <label>
            <span>Played to</span>
            <input type="date" value={filters.to} onChange={(event) => setFilter('to', event.target.value)} />
          </label>
          <label>
            <span>Win condition</span>
            <select value={filters.condition} onChange={(event) => setFilter('condition', event.target.value)}>
              <option value="">All outcomes</option>
              <option value="CHECKMATE">Checkmate</option>
              <option value="FORFEIT">Forfeit</option>
              <option value="STALEMATE">Stalemate</option>
              <option value="DRAW">Draw</option>
            </select>
          </label>
          <label>
            <span>Your color</span>
            <select value={filters.color} onChange={(event) => setFilter('color', event.target.value)}>
              <option value="">Either color</option>
              <option value="WHITE">White</option>
              <option value="BLACK">Black</option>
            </select>
          </label>
          <div className="history-filter-actions">
            <button className="button button-primary" type="submit">Apply filters</button>
            <button className="button button-quiet" type="button" onClick={clearFilters}>Clear</button>
          </div>
        </form>

        {error && <p className="history-error" role="alert">{error}</p>}
        <div className="history-results" aria-live="polite">
          {loading && <p className="waiting-copy">Loading game history…</p>}
          {!loading && history?.content?.length === 0 && (
            <div className="empty-state"><span>♙</span><strong>No games found</strong>
              <p>Try changing or clearing the filters.</p></div>
          )}
          {!loading && history?.content?.map((game) => (
            <button type="button" className="history-game" key={game.id}
              onClick={() => setSelectedGameId(game.id)}>
              <div className="history-game-heading">
                <strong>{describeOutcome(game)}</strong>
                <time dateTime={`${game.playedAt}:00`}>
                  Started {game.playedAt.replace('T', ' ')}
                </time>
              </div>
              <div className="history-players">
                <span className={game.whiteUsername === user ? 'history-own-player' : ''}>
                  <i className="history-color white-history-color" /> White: {game.whiteUsername}
                  {game.whiteUsername === user && <small>YOU</small>}
                </span>
                <span className={game.blackUsername === user ? 'history-own-player' : ''}>
                  <i className="history-color black-history-color" /> Black: {game.blackUsername}
                  {game.blackUsername === user && <small>YOU</small>}
                </span>
              </div>
              <span className="history-game-open">Open replay →</span>
            </button>
          ))}
        </div>
        {history && history.totalElements > 0 && (
          <div className="history-pagination">
            <span>Page {history.number} of {history.totalPages - 1} · {history.totalElements} games</span>
            <div>
              <button className="button button-quiet button-small" disabled={history.first || loading}
                onClick={() => setPage((current) => Math.max(0, current - 1))}>Previous</button>
              <button className="button button-quiet button-small" disabled={history.last || loading}
                onClick={() => setPage((current) => current + 1)}>Next</button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}

export default GameHistory;
