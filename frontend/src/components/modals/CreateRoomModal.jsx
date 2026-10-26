import { useState } from 'react';
import Modal from '../Modal.jsx';

function CreateRoomModal({ busy, onClose, onSubmit }) {
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const submit = async (event) => {
    event.preventDefault();
    if (!name.trim()) return setError('Give your room a name.');
    try {
      await onSubmit(name.trim(), password);
    } catch (requestError) {
      setError(requestError.message);
    }
  };
  return (
    <Modal title="Set the table." onClose={onClose}>
      <p className="modal-copy">Create a room and invite a friend to join.</p>
      <form className="form-stack" onSubmit={submit}>
        <label>Room name<input autoFocus value={name} onChange={(event) => setName(event.target.value)} maxLength={80} required /></label>
        <label>Password <span className="optional-label">OPTIONAL</span><input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button button-primary full-button" disabled={busy}>{busy ? 'Creating…' : 'Create room'} <span>↗</span></button>
      </form>
    </Modal>
  );
}

export default CreateRoomModal;
