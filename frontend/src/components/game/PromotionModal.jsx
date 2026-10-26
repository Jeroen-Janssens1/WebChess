import Modal from '../Modal.jsx';
import { pieceGlyphs } from '../../utils/chess.js';

function PromotionModal({ isWhite, onChoose, onClose }) {
  const choices = ['q', 'r', 'b', 'n'];
  return (
    <Modal title="Choose a promotion." onClose={onClose}>
      <p className="modal-copy">Your pawn reached the far side. Choose its new piece.</p>
      <div className="promotion-options">
        {choices.map((choice) => {
          const piece = isWhite ? choice.toUpperCase() : choice;
          return <button className="promotion-choice" key={choice} onClick={() => onChoose(choice)}
            aria-label={`Promote to ${choice === 'q' ? 'queen' : choice === 'r' ? 'rook' : choice === 'b' ? 'bishop' : 'knight'}`}>
            <span className={isWhite ? 'white-piece' : 'black-piece'}>{pieceGlyphs[piece]}</span>
            <small>{choice.toUpperCase()}</small>
          </button>;
        })}
      </div>
    </Modal>
  );
}

export default PromotionModal;
