import { requestJson, setAuthToken, setRefreshToken, setSessionId, getRefreshToken, clearAuthToken, getAuthToken } from './client';

export interface LoginPayload {
  username: string;
  password: string;
}

export interface ChangePasswordPayload {
  old_password: string;
  new_password: string;
}

export interface CurrentUser {
  user_id: string;
  username: string;
  display_name: string;
  email?: string;
  must_change_password: boolean;
  roles: string[];
  permissions?: string[];
}

export interface LoginResult extends CurrentUser {
  access_token: string;
  refresh_token: string;
  session_id: string;
  token_type: string;
  expires_at: string;
}

export interface RefreshResult {
  access_token: string;
  refresh_token: string;
  session_id: string;
  token_type: string;
  expires_at: string;
}

export async function login(payload: LoginPayload) {
  const response = await requestJson<LoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  setAuthToken(response.data.access_token);
  setRefreshToken(response.data.refresh_token);
  setSessionId(response.data.session_id);
  return response;
}

/**
 * Refresh the access token using the stored refresh token.
 * Returns true on success, false if refresh failed (caller should force re-login).
 */
export async function refreshToken(): Promise<boolean> {
  const currentRefreshToken = getRefreshToken();
  if (!currentRefreshToken) {
    return false;
  }

  try {
    const response = await requestJson<RefreshResult>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refresh_token: currentRefreshToken })
    });
    setAuthToken(response.data.access_token);
    setRefreshToken(response.data.refresh_token);
    setSessionId(response.data.session_id);
    return true;
  } catch {
    clearAuthToken();
    return false;
  }
}

export function fetchCurrentUser() {
  return requestJson<CurrentUser>('/api/v1/auth/me');
}

export function logout() {
  return requestJson<{ revoked: boolean; session_id: string }>('/api/v1/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ reason: 'user logout' })
  });
}

export function changePassword(payload: ChangePasswordPayload) {
  return requestJson<{ password_changed: boolean; session_invalidated: boolean; user_id: string }>('/api/v1/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}
