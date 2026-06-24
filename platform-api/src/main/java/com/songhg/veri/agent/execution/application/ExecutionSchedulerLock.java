package com.songhg.veri.agent.execution.application;

import java.time.Duration;

/**
 * Coordinates WP9 scheduler leadership across either one JVM or multiple active worker instances.
 */
public interface ExecutionSchedulerLock {

    LockAttempt tryAcquire(String lockName, Duration waitTime, Duration leaseTime);

    String provider();

    boolean distributed();

    record LockAttempt(
            boolean acquired,
            String provider,
            boolean distributed,
            String skipReason,
            LockLease lease
    ) implements AutoCloseable {

        public static LockAttempt acquired(String provider, boolean distributed, LockLease lease) {
            return new LockAttempt(true, provider, distributed, null, lease);
        }

        public static LockAttempt skipped(String provider, boolean distributed, String reason) {
            return new LockAttempt(false, provider, distributed, reason, null);
        }

        @Override
        public void close() {
            if (lease != null) {
                lease.close();
            }
        }
    }

    interface LockLease extends AutoCloseable {

        @Override
        void close();
    }
}
