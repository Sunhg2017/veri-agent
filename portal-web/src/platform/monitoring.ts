export interface MonitoringContext {
  componentStack?: string;
  severity?: 'info' | 'warning' | 'error';
  traceId?: string;
}

export interface MonitoringEvent {
  context?: MonitoringContext;
  error: Error;
  timestamp: string;
}

type MonitoringReporter = (event: MonitoringEvent) => void;

let reporter: MonitoringReporter | undefined;

export function configureMonitoring(nextReporter: MonitoringReporter) {
  reporter = nextReporter;
}

export function reportError(error: Error, context?: MonitoringContext) {
  const event: MonitoringEvent = {
    context,
    error,
    timestamp: new Date().toISOString()
  };
  if (reporter) {
    reporter(event);
    return;
  }
  console.error('Portal error captured', event);
}
