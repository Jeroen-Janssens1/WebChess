import { api } from '../utils/api.js';

function useAuthActions({ setUser, setRoom, setGame, setScreen, setModal, setBusy, notify }) {
  const logout = async () => {
    setBusy(true);
    try {
      await api('/api/logout', { method: 'POST' });
      setUser(null);
      setRoom(null);
      setGame(null);
      setScreen('welcome');
    } catch (error) {
      notify(error.message, true);
    } finally {
      setBusy(false);
    }
  };

  const submitAuth = async (mode, username, password) => {
    setBusy(true);
    try {
      const result = await api(`/api/${mode === 'register' ? 'register' : 'login'}`, {
        method: 'POST',
        body: JSON.stringify({ username, password })
      });
      if (mode === 'login') {
        setUser(result.user?.username || username);
        setScreen('menu');
      } else {
        notify('Account created. You can now log in.');
      }
      setModal(null);
    } catch (error) {
      notify(error.message, true);
      throw error;
    } finally {
      setBusy(false);
    }
  };

  return { logout, submitAuth };
}

export default useAuthActions;
