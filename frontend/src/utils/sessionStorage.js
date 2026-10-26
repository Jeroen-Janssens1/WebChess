const sessionKey = 'webchess-session';

export function readSavedSession() {
  try {
    const saved = sessionStorage.getItem(sessionKey);
    return saved ? JSON.parse(saved) : null;
  } catch (error) {
    console.warn('Unable to read the saved Web Chess session.', error);
    clearSavedSession();
    return null;
  }
}

export function saveSession(session) {
  try {
    sessionStorage.setItem(sessionKey, JSON.stringify(session));
  } catch (error) {
    console.warn('Unable to save the Web Chess session.', error);
  }
}

export function clearSavedSession() {
  try {
    sessionStorage.removeItem(sessionKey);
  } catch (error) {
    console.warn('Unable to clear the saved Web Chess session.', error);
  }
}
