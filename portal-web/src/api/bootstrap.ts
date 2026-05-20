import { requestJson } from './client';

export interface BootstrapPayload {
  bootstrap_token: string;
  username: string;
  password: string;
  display_name: string;
  email: string;
}

export interface BootstrapResult {
  user_id: string;
  role: string;
  must_change_password: boolean;
}

type RawBootstrapResult = Partial<BootstrapResult> & {
  userId?: string;
  mustChangePassword?: boolean;
};

export async function bootstrapSuperAdmin(payload: BootstrapPayload) {
  const response = await requestJson<RawBootstrapResult>('/api/v1/bootstrap/super-admin', {
    method: 'POST',
    body: JSON.stringify({
      bootstrapToken: payload.bootstrap_token,
      username: payload.username,
      password: payload.password,
      displayName: payload.display_name,
      email: payload.email
    })
  });
  return {
    ...response,
    data: {
      user_id: response.data.user_id ?? response.data.userId ?? '',
      role: response.data.role ?? '',
      must_change_password: response.data.must_change_password ?? response.data.mustChangePassword ?? false
    }
  };
}
