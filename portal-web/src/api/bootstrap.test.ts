import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from './client';
import { bootstrapSuperAdmin } from './bootstrap';

vi.mock('./client', () => ({
  requestJson: vi.fn()
}));

const requestJsonMock = vi.mocked(requestJson);

describe('bootstrap API helpers', () => {
  beforeEach(() => {
    requestJsonMock.mockReset();
  });

  it('sends bootstrap fields with the backend camelCase contract and normalizes the response', async () => {
    requestJsonMock.mockResolvedValue({
      code: 'OK',
      message: 'ok',
      trace_id: 'trace-bootstrap',
      data: {
        userId: 'user-1',
        role: 'SuperAdmin',
        mustChangePassword: true
      }
    });

    const response = await bootstrapSuperAdmin({
      bootstrap_token: 'init-token',
      username: 'admin_user',
      password: 'PlainPassword123',
      display_name: '平台管理员',
      email: 'admin@example.com'
    });

    expect(requestJsonMock).toHaveBeenCalledWith('/api/v1/bootstrap/super-admin', {
      method: 'POST',
      body: JSON.stringify({
        bootstrapToken: 'init-token',
        username: 'admin_user',
        password: 'PlainPassword123',
        displayName: '平台管理员',
        email: 'admin@example.com'
      })
    });
    expect(response.data).toEqual({
      user_id: 'user-1',
      role: 'SuperAdmin',
      must_change_password: true
    });
  });
});
