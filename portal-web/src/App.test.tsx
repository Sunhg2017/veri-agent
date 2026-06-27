import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { HashRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { ConfirmDialogProvider } from './components/ui/ConfirmDialog';
import { clearAuthToken, getAuthToken } from './api/client';
import { useAppSessionStore } from './platform/appStore';

vi.mock('./api/client', async () => {
  const actual = await vi.importActual<typeof import('./api/client')>('./api/client');
  return {
    ...actual,
    clearAuthToken: vi.fn(),
    getAuthToken: vi.fn()
  };
});

vi.mock('./api/health', () => ({
  fetchHealth: vi.fn().mockResolvedValue({
    code: 'OK',
    data: { service: 'platform-api', status: 'UP' },
    message: 'OK',
    trace_id: 'trace-health'
  })
}));

vi.mock('./api/auth', async () => {
  const actual = await vi.importActual<typeof import('./api/auth')>('./api/auth');
  return {
    ...actual,
    fetchCurrentUser: vi.fn()
  };
});

function renderApp() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false }
    }
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <HashRouter>
        <ConfirmDialogProvider>
          <App />
        </ConfirmDialogProvider>
      </HashRouter>
    </QueryClientProvider>
  );
}

describe('App shell', () => {
  beforeEach(() => {
    vi.mocked(getAuthToken).mockReturnValue(null);
    vi.mocked(clearAuthToken).mockReset();
    useAppSessionStore.setState({ currentUser: null });
    window.location.hash = '#/overview';
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('renders login form with declarative validation affordances', () => {
    renderApp();
    expect(screen.getByRole('main', { name: 'Veri Agent' })).toBeInTheDocument();
    expect(screen.getByLabelText('账号')).toBeRequired();
    expect(screen.getByLabelText('密码')).toBeRequired();
  });

  it('renders the authenticated app shell from session store', async () => {
    vi.mocked(getAuthToken).mockReturnValue('token');
    useAppSessionStore.setState({
      currentUser: {
        display_name: 'Admin',
        must_change_password: false,
        permissions: [],
        roles: ['ADMIN'],
        user_id: 'u-1',
        username: 'admin'
      }
    });
    renderApp();
    await waitFor(() => expect(screen.getByRole('navigation', { name: '功能菜单' })).toBeInTheDocument());
    expect(screen.getByRole('heading', { name: '系统概览' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '切换主题' })).toBeInTheDocument();
  });
});
