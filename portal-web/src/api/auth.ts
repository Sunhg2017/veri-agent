import {
  requestJson,
  setAuthToken,
  setRefreshToken,
  setSessionId,
  getRefreshToken,
  clearAuthToken,
  getAuthToken,
  type ApiResponse
} from './client';

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

type RawCurrentUser = Partial<CurrentUser> & {
  userId?: string;
  displayName?: string;
  mustChangePassword?: boolean;
};

type RawLoginResult = Partial<LoginResult> & RawCurrentUser & {
  accessToken?: string;
  refreshToken?: string;
  sessionId?: string;
  tokenType?: string;
  expiresAt?: string;
};

type RawRefreshResult = Partial<RefreshResult> & {
  accessToken?: string;
  refreshToken?: string;
  sessionId?: string;
  tokenType?: string;
  expiresAt?: string;
};

function normalizeCurrentUser(data: RawCurrentUser): CurrentUser {
  return {
    user_id: data.user_id ?? data.userId ?? '',
    username: data.username ?? '',
    display_name: data.display_name ?? data.displayName ?? '',
    email: data.email,
    must_change_password: data.must_change_password ?? data.mustChangePassword ?? false,
    roles: data.roles ?? [],
    permissions: data.permissions
  };
}

function normalizeLoginResult(data: RawLoginResult): LoginResult {
  return {
    ...normalizeCurrentUser(data),
    access_token: data.access_token ?? data.accessToken ?? '',
    refresh_token: data.refresh_token ?? data.refreshToken ?? '',
    session_id: data.session_id ?? data.sessionId ?? '',
    token_type: data.token_type ?? data.tokenType ?? 'Bearer',
    expires_at: data.expires_at ?? data.expiresAt ?? ''
  };
}

function normalizeRefreshResult(data: RawRefreshResult): RefreshResult {
  return {
    access_token: data.access_token ?? data.accessToken ?? '',
    refresh_token: data.refresh_token ?? data.refreshToken ?? '',
    session_id: data.session_id ?? data.sessionId ?? '',
    token_type: data.token_type ?? data.tokenType ?? 'Bearer',
    expires_at: data.expires_at ?? data.expiresAt ?? ''
  };
}

export async function login(payload: LoginPayload): Promise<ApiResponse<LoginResult>> {
  const response = await requestJson<RawLoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  const data = normalizeLoginResult(response.data);
  setAuthToken(data.access_token);
  setRefreshToken(data.refresh_token);
  setSessionId(data.session_id);
  return { ...response, data };
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
    const response = await requestJson<RawRefreshResult>('/api/v1/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken: currentRefreshToken })
    });
    const data = normalizeRefreshResult(response.data);
    setAuthToken(data.access_token);
    setRefreshToken(data.refresh_token);
    setSessionId(data.session_id);
    return true;
  } catch {
    clearAuthToken();
    return false;
  }
}

export async function fetchCurrentUser(): Promise<ApiResponse<CurrentUser>> {
  const response = await requestJson<RawCurrentUser>('/api/v1/auth/me');
  return { ...response, data: normalizeCurrentUser(response.data) };
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
    body: JSON.stringify({
      oldPassword: payload.old_password,
      newPassword: payload.new_password
    })
  });
}
