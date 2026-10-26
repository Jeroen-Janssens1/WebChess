import { createRoot } from 'react-dom/client';
import { useCallback, useState } from 'react';
import AuthModal from './components/modals/AuthModal.jsx';
import CreateRoomModal from './components/modals/CreateRoomModal.jsx';
import JoinRoomModal from './components/modals/JoinRoomModal.jsx';
import GameScreen from './components/game/GameScreen.jsx';
import Menu from './components/rooms/Menu.jsx';
import RoomLobby from './components/rooms/RoomLobby.jsx';
import RoomSearch from './components/rooms/RoomSearch.jsx';
import Welcome from './components/rooms/Welcome.jsx';
import useAuthActions from './hooks/useAuthActions.js';
import useGameActions from './hooks/useGameActions.js';
import useRoomActions from './hooks/useRoomActions.js';
import useSessionState from './hooks/useSessionState.js';
import './styles/styles.css';

function App() {
  const [user, setUser] = useState(null);
  const [screen, setScreen] = useState('welcome');
  const [modal, setModal] = useState(null);
  const [toast, setToast] = useState('');
  const [toastError, setToastError] = useState(false);
  const [busy, setBusy] = useState(false);
  const [room, setRoom] = useState(null);
  const [game, setGame] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [gameError, setGameError] = useState('');
  const [suppressRoomGameId, setSuppressRoomGameId] = useState(null);

  const notify = useCallback((message, isError = false) => {
    setToast(message);
    setToastError(isError);
    window.clearTimeout(notify.timeout);
    notify.timeout = window.setTimeout(() => setToast(''), 3600);
  }, []);

  const restoring = useSessionState({
    screen, room, game, suppressRoomGameId,
    setUser, setRoom, setGame, setScreen, setSuppressRoomGameId
  });

  const { createRoom, joinRoom, startRoomGame, leaveRoom, handleRoomClosed } = useRoomActions({
    room, screen, setRooms, setRoom, setGame, setScreen, setModal,
    setSuppressRoomGameId, setBusy, notify
  });
  const { openLocalGame, openExistingGame, returnFromGame, forfeitGame, requestRematch } = useGameActions({
    game, room, setGame, setRoom, setScreen, setSuppressRoomGameId, setBusy, notify
  });
  const { logout, submitAuth } = useAuthActions({
    setUser, setRoom, setGame, setScreen, setModal, setBusy, notify
  });

  const handleGameReady = useCallback((id) => {
    if (id && id !== suppressRoomGameId) openExistingGame(id);
  }, [openExistingGame, suppressRoomGameId]);

  return (
    <main className={`app-shell screen-${screen}`}>
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      {screen === 'welcome' && <Welcome disabled={restoring}
        onAuth={(mode) => {
          if (!restoring) setModal({ type: 'auth', mode });
        }} />}
      {screen === 'menu' && (
        <Menu user={user} busy={busy} onLocal={openLocalGame}
          onCreate={() => setModal({ type: 'create-room' })}
          onJoin={() => setScreen('rooms')} onLogout={logout} />
      )}
      {screen === 'rooms' && (
        <RoomSearch rooms={rooms} busy={busy} onBack={() => setScreen('menu')}
          onJoin={(selected) => selected.passwordProtected
            ? setModal({ type: 'join-room', room: selected })
            : joinRoom(selected, '')} />
      )}
      {screen === 'room' && room && (
        <RoomLobby room={room} user={user} busy={busy}
          onRoomChange={setRoom} onStart={startRoomGame} onLeave={leaveRoom}
          onGameReady={handleGameReady} onRoomClosed={handleRoomClosed} />
      )}
      {screen === 'game' && game && (
        <GameScreen key={game.gameId} initialGame={game} user={user}
          hasRoom={Boolean(room)} onGameChange={setGame} onBack={returnFromGame}
          onForfeit={forfeitGame} onRematch={requestRematch}
          notify={notify} gameError={gameError} setGameError={setGameError} />
      )}
      {modal?.type === 'auth' && <AuthModal mode={modal.mode} busy={busy}
        onMode={(mode) => setModal({ type: 'auth', mode })}
        onClose={() => setModal(null)} onSubmit={submitAuth} />}
      {modal?.type === 'create-room' && <CreateRoomModal busy={busy}
        onClose={() => setModal(null)} onSubmit={createRoom} />}
      {modal?.type === 'join-room' && <JoinRoomModal room={modal.room} busy={busy}
        onClose={() => setModal(null)} onSubmit={(password) => joinRoom(modal.room, password)} />}
      {toast && <div className={`toast${toastError ? ' toast-error' : ''}`} role="status">{toast}</div>}
    </main>
  );
}

export default App;

createRoot(document.getElementById('root')).render(<App />);
