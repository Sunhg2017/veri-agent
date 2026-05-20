import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearAuthToken,
  getRefreshToken,
  requestJson,
  setAuthToken,
  setRefreshToken,
  setSessionId
} from './client';
import { changePassword, fetchCurrentUser, login, refreshToken } from './auth';

vi.mock('./client', () => ({
  requestJson: vi.fn(),
  setAuthToken: vi.fn(),
  setRefreshToken: vi.fn(),
  setSessionId: vi.fn(),
  getRefreshToken: vi.fn(),
  clearAuthToken: vi.fn(),
  getAuthToken: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);
const setAuthTokenMock = vi.mocked(setAuthToken);
const setRefreshTokenMock = vi.mocked(setRefreshToken);
const setSessionIdMock = vi.mocked(setSessionId);
const getRefreshTokenMock = vi.mocked(getRefreshToken);
const clearAuthTokenMock = vi.mocked(clearAuthToken);

describe('auth API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
    setAuthTokenMock.mockReset();
    setRefreshTokenMock.mockReset();
    setSessionIdMock.mockReset();
    getRefreshTokenMock.mockReset();
    clearAuthTokenMock.mockReset();
  });

  it('normalizes camelCase login responses and stores bearer tokens', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-login',
      data: {
        accessToken: 'access-1',
        refreshToken: 'refresh-1',
        sessionId: 'session-1',
        tokenType: 'Bearer',
        expiresAt: '2026-05-20T17:40:00Z',
        userId: 'user-1',
        username: 'admin_user',
        displayName: '平台管理员',
        mustChangePassword: true,
        roles: ['SuperAdmin']
      }
    });

    const response = await login({ username: 'admin_user', password: 'PlainPassword123' });

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: 'admin_user', password: 'PlainPassword123' })
    });
    expect(response.data).toMatchObject({
      access_token: 'access-1',
      refresh_token: 'refresh-1',
      session_id: 'session-1',
      user_id: 'user-1',
      display_name: '平台管理员',
      must_change_password: true
    });
    expect(setAuthTokenMock).toHaveBeenCalledWith('access-1');
    expect(setRefreshTokenMock).toHaveBeenCalledWith('refresh-1');
    expect(setSessionIdMock).toHaveBeenCalledWith('session-1');
  });

  it('normalizes current user permissions from camelCase responses', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-me',
      data: {
        userId: 'user-1',
        username: 'admin_user',
        displayName: '平台管理员',
        mustChangePassword: false,
        roles: ['SuperAdmin'],
        permissions: ['modelAccess:read']
      }
    });

    const response = await fetchCurrentUser();

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/auth/me');
    expect(response.data).toMatchObject({
      user_id: 'user-1',
      display_name: '平台管理员',
      must_change_password: false,
      permissions: ['modelAccess:read']
    });
  });

  it('sends refresh tokens with the backend camelCase contract', async () => {
    getRefreshTokenMock.mockReturnValue('refresh-1');
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-refresh',
      data: {
        accessToken: 'access-2',
        refreshToken: 'refresh-2',
        sessionId: 'session-2',
        tokenType: 'Bearer',
        expiresAt: '2026-05-20T18:00:00Z'
      }
    });

    await expect(refreshToken()).resolves.toBe(true);

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: 'refresh-1' })
    });
    expect(setAuthTokenMock).toHaveBeenCalledWith('access-2');
    expect(setRefreshTokenMock).toHaveBeenCalledWith('refresh-2');
    expect(setSessionIdMock).toHaveBeenCalledWith('session-2');
  });

  it('clears auth state when refresh fails', async () => {
    getRefreshTokenMock.mockReturnValue('refresh-1');
    requestJsonMock.mockRejectedValue(new Error('expired'));

    await expect(refreshToken()).resolves.toBe(false);

    expect(clearAuthTokenMock).toHaveBeenCalledTimes(1);
  });

  it('sends password changes with the backend camelCase contract', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-password',
      data: { passwordChanged: true, sessionInvalidated: true, userId: 'user-1' }
    });

    await changePassword({ old_password: 'PlainPassword123', new_password: 'PlainPassword456' });

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({
        oldPassword: 'PlainPassword123',
        newPassword: 'PlainPassword456'
      })
    });
  });
});
