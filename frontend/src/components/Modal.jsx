import { useEffect } from 'react';

function Modal({ title, children, onClose, wide = false, showClose = true }) {
  useEffect(() => {
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section className={`modal-card${wide ? ' modal-wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="modal-title">
        {showClose && <button className="modal-close icon-button" onClick={onClose} aria-label="Close dialog">×</button>}
        <p className="eyebrow">WEB CHESS</p>
        <h2 id="modal-title">{title}</h2>
        {children}
      </section>
    </div>
  );
}

export default Modal;
