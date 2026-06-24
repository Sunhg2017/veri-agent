package com.songhg.veri.agent.execution.application;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local fallback lock for single-process development and tests.
 */
@Component
@Profile("!redis")
public class LocalExecutionSchedulerLock implements ExecutionSchedulerLock {

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public LockAttempt tryAcquire(String lockName, Duration waitTime, Duration leaseTime) {
        try {
            boolean acquired = lock.tryLock(Math.max(0, waitTime.toMillis()), TimeUnit.MILLISECONDS);
            if (!acquired) {
                return LockAttempt.skipped(provider(), false, "LEADER_LOCK_BUSY");
            }
            return LockAttempt.acquired(provider(), false, lock::unlock);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LockAttempt.skipped(provider(), false, "LEADER_LOCK_INTERRUPTED");
        }
    }

    @Override
    public String provider() {
        return "LOCAL_JVM";
    }

    @Override
    public boolean distributed() {
        return false;
    }
}
