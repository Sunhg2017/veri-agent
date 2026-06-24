package com.songhg.veri.agent.execution.infrastructure;

import com.songhg.veri.agent.execution.application.ExecutionSchedulerLock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Redis-backed scheduler leader lock used when the `redis` profile provides Redisson.
 */
@Component
@Profile("redis")
@ConditionalOnBean(RedissonClient.class)
public class RedissonExecutionSchedulerLock implements ExecutionSchedulerLock {

    private final RedissonClient redissonClient;

    public RedissonExecutionSchedulerLock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public LockAttempt tryAcquire(String lockName, Duration waitTime, Duration leaseTime) {
        RLock lock = redissonClient.getLock(lockName);
        try {
            boolean acquired = lock.tryLock(
                    Math.max(0, waitTime.toMillis()),
                    Math.max(1, leaseTime.toMillis()),
                    TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                return LockAttempt.skipped(provider(), true, "LEADER_LOCK_BUSY");
            }
            return LockAttempt.acquired(provider(), true, () -> {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LockAttempt.skipped(provider(), true, "LEADER_LOCK_INTERRUPTED");
        } catch (RuntimeException exception) {
            return LockAttempt.skipped(provider(), true, "LEADER_LOCK_UNAVAILABLE");
        }
    }

    @Override
    public String provider() {
        return "REDISSON";
    }

    @Override
    public boolean distributed() {
        return true;
    }
}
