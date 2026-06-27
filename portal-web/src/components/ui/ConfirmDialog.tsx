import * as Dialog from '@radix-ui/react-dialog';
import { AlertTriangle } from 'lucide-react';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { translate } from '../../platform/i18n';

export interface ConfirmDialogOptions {
  cancelLabel?: string;
  confirmLabel?: string;
  description?: string;
  title: string;
  tone?: 'default' | 'danger';
}

interface ConfirmRequest extends ConfirmDialogOptions {
  id: number;
  resolve: (confirmed: boolean) => void;
}

type ConfirmDialogContextValue = (options: ConfirmDialogOptions) => Promise<boolean>;

const ConfirmDialogContext = createContext<ConfirmDialogContextValue | null>(null);

export function ConfirmDialogProvider({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<ConfirmRequest | null>(null);

  const confirm = useCallback((options: ConfirmDialogOptions) => new Promise<boolean>((resolve) => {
    setRequest({
      cancelLabel: translate('auto.k0220'),
      confirmLabel: translate('auto.k0807'),
      id: Date.now(),
      resolve,
      tone: 'default',
      ...options
    });
  }), []);

  const close = useCallback((confirmed: boolean) => {
    setRequest((current) => {
      current?.resolve(confirmed);
      return null;
    });
  }, []);

  const value = useMemo(() => confirm, [confirm]);

  return (
    <ConfirmDialogContext.Provider value={value}>
      {children}
      <Dialog.Root open={Boolean(request)} onOpenChange={(open) => !open && close(false)}>
        <Dialog.Portal>
          <Dialog.Overlay className="confirm-dialog-overlay" />
          <Dialog.Content className="confirm-dialog-panel" aria-describedby={request ? `confirm-desc-${request.id}` : undefined}>
            {request ? (
              <>
                <div className={`confirm-dialog-icon ${request.tone === 'danger' ? 'danger' : ''}`}>
                  <AlertTriangle size={20} />
                </div>
                <div className="confirm-dialog-copy">
                  <Dialog.Title className="confirm-dialog-title">{request.title}</Dialog.Title>
                  {request.description ? (
                    <Dialog.Description className="confirm-dialog-desc" id={`confirm-desc-${request.id}`}>
                      {request.description}
                    </Dialog.Description>
                  ) : null}
                </div>
                <div className="confirm-dialog-actions">
                  <button className="btn btn-secondary" type="button" onClick={() => close(false)}>
                    {request.cancelLabel}
                  </button>
                  <button className={request.tone === 'danger' ? 'btn btn-danger' : 'btn btn-primary'} type="button" onClick={() => close(true)}>
                    {request.confirmLabel}
                  </button>
                </div>
              </>
            ) : null}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </ConfirmDialogContext.Provider>
  );
}

export function useConfirmDialog() {
  const confirm = useContext(ConfirmDialogContext);
  if (!confirm) {
    throw new Error('useConfirmDialog must be used inside ConfirmDialogProvider');
  }
  return confirm;
}
