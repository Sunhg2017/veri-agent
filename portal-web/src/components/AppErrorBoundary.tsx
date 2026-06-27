import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Component, type ErrorInfo, type ReactNode } from 'react';
import { reportError } from '../platform/monitoring';
import { translate } from '../platform/i18n';

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  hasError: boolean;
  errorMessage?: string;
}

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { hasError: false };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return {
      hasError: true,
      errorMessage: error.message
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    reportError(error, {
      componentStack: errorInfo.componentStack ?? undefined,
      severity: 'error'
    });
  }

  private reset = () => {
    this.setState({ hasError: false, errorMessage: undefined });
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <main className="app-error-boundary" role="alert">
        <section className="app-error-panel">
          <div className="app-error-icon">
            <AlertTriangle size={24} />
          </div>
          <div className="app-error-copy">
            <h1>{translate('auto.k0225')}</h1>
            <p>{translate('auto.k0226')}</p>
            {this.state.errorMessage && (
              <pre>{this.state.errorMessage}</pre>
            )}
          </div>
          <div className="app-error-actions">
            <button className="btn btn-primary" type="button" onClick={this.reset}>
              <RotateCcw size={16} />
              {translate('auto.k0227')}</button>
            <button className="btn btn-secondary" type="button" onClick={() => window.location.assign('#overview')}>
              {translate('auto.k0228')}</button>
          </div>
        </section>
      </main>
    );
  }
}
