import { requestJson } from './client';

export interface HealthResult {
  service: string;
  status: string;
  timestamp: string;
}

export function fetchHealth() {
  return requestJson<HealthResult>('/api/v1/health', {
    method: 'GET'
  });
}

