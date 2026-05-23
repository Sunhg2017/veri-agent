import { AlertCircle, CheckCircle2, Info, XCircle } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastItem {
  id: number;
  type: ToastType;
  message: ReactNode;
}

let nextId = 1;

const icons: Record<ToastType, ReactNode> = {
  success: <CheckCircle2 size={18} className="toast-icon" />,
  error: <XCircle size={18} className="toast-icon" />,
  info: <Info size={18} className="toast-icon" />
};

function ToastCard({ item, onDone }: { item: ToastItem; onDone: (id: number) => void }) {
  const [leaving, setLeaving] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const startLeave = useCallback(() => {
    setLeaving(true);
    timerRef.current = setTimeout(() => onDone(item.id), 200);
  }, [item.id, onDone]);

  useEffect(() => {
    const timer = setTimeout(startLeave, 3500);
    return () => clearTimeout(timer);
  }, [startLeave]);

  useEffect(() => {
    return () => clearTimeout(timerRef.current);
  }, []);

  return (
    <div
      className={`toast toast-${item.type}${leaving ? ' toast-leaving' : ''}`}
      onClick={startLeave}
      role="alert"
    >
      {icons[item.type]}
      <span className="toast-content">{item.message}</span>
    </div>
  );
}

export function useToast() {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const addToast = useCallback((type: ToastType, message: ReactNode) => {
    const id = nextId++;
    setToasts((prev) => [...prev, { id, type, message }]);
  }, []);

  const removeToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const toastContainer = toasts.length > 0 ? (
    <div className="toast-container">
      {toasts.map((item) => (
        <ToastCard key={item.id} item={item} onDone={removeToast} />
      ))}
    </div>
  ) : null;

  return { addToast, toastContainer };
}
