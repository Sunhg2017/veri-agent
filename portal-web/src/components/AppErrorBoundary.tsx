import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Component, type ErrorInfo, type ReactNode } from 'react';

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
    console.error('Portal render failed', error, errorInfo);
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
            <h1>页面渲染异常</h1>
            <p>当前视图加载失败，请刷新或返回后重试。错误已写入浏览器控制台，便于排查 trace。</p>
            {this.state.errorMessage && (
              <pre>{this.state.errorMessage}</pre>
            )}
          </div>
          <div className="app-error-actions">
            <button className="btn btn-primary" type="button" onClick={this.reset}>
              <RotateCcw size={16} />
              重试
            </button>
            <button className="btn btn-secondary" type="button" onClick={() => window.location.assign('#overview')}>
              返回概览
            </button>
          </div>
        </section>
      </main>
    );
  }
}
