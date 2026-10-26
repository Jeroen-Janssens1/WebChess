import { useState } from 'react';
import Modal from '../Modal.jsx';

function JoinRoomModal({ room, busy, onClose, onSubmit }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const submit = async (event) => {
    event.preventDefault();
    setError('');
    try {
      await onSubmit(password);
    } catch (requestError) {
      setError(requestError.message);
    }
  };
  return (
    <Modal title="A private table." onClose={onClose}>
      <p className="modal-copy">Enter the password to join <strong>{room.name}</strong>.</p>
      <form className="form-stack" onSubmit={submit}>
        <label>Room password<input autoFocus type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button button-primary full-button" disabled={busy}>{busy ? 'Joining…' : 'Join room'} <span>↗</span></button>
      </form>
    </Modal>
  );
}

export default JoinRoomModal;
