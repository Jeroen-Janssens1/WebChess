import { useState } from 'react';
import Modal from '../Modal.jsx';

function AuthModal({ mode, busy, onMode, onClose, onSubmit }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const register = mode === 'register';
  const submit = async (event) => {
    event.preventDefault();
    setError('');
    if (!username.trim() || !password) return setError('Enter your username and password.');
    if (register && password !== confirmation) return setError('Your passwords do not match.');
    try {
      await onSubmit(mode, username.trim(), password);
    } catch (requestError) {
      setError(requestError.message);
    }
  };
  return (
    <Modal title={register ? 'Create your account.' : 'Welcome back.'} onClose={onClose}>
      <p className="modal-copy">{register ? 'Pick a name and save your place at the board.' : 'Sign in to find your next game.'}</p>
      <form className="form-stack" onSubmit={submit}>
        <label>Username<input autoFocus autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required /></label>
        <label>Password<input type="password" autoComplete={register ? 'new-password' : 'current-password'} value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
        {register && <label>Confirm password<input type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} required /></label>}
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="button button-primary full-button" disabled={busy}>{busy ? 'One moment…' : register ? 'Create account' : 'Log in'} <span>↗</span></button>
      </form>
      <p className="modal-switch">{register ? 'Already have an account?' : 'New to Web Chess?'} <button onClick={() => onMode(register ? 'login' : 'register')}>{register ? 'Log in' : 'Create an account'}</button></p>
    </Modal>
  );
}

export default AuthModal;
