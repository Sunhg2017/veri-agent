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

export function bootstrapSuperAdmin(payload: BootstrapPayload) {
  return requestJson<BootstrapResult>('/api/v1/bootstrap/super-admin', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
}

