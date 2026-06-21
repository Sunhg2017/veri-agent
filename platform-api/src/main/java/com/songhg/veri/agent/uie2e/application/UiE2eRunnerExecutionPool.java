package com.songhg.veri.agent.uie2e.application;

import com.songhg.veri.agent.uie2e.config.UiE2eProperties;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shares one bounded browser-attempt pool across all WP7 runs in the current JVM so capacity can be observed and
 * repeated batch runs do not recreate short-lived executors for every request.
 */
public final class UiE2eRunnerExecutionPool {

    private final ThreadPoolExecutor executor;
    private final int maxConcurrency;

    public UiE2eRunnerExecutionPool(UiE2eProperties properties) {
        this.maxConcurrency = properties == null ? 1 : Math.max(1, properties.effectiveMaxConcurrency());
        this.executor = new ThreadPoolExecutor(
                maxConcurrency,
                maxConcurrency,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new UiE2eRunnerThreadFactory()
        );
        this.executor.allowCoreThreadTimeOut(false);
    }

    <T> List<Future<T>> invokeAll(List<? extends Callable<T>> tasks) throws InterruptedException {
        return executor.invokeAll(tasks);
    }

    CapacitySnapshot snapshot() {
        int activeWorkers = Math.max(0, executor.getActiveCount());
        int queuedTasks = Math.max(0, executor.getQueue().size());
        return new CapacitySnapshot(
                maxConcurrency,
                activeWorkers,
                queuedTasks,
                Math.max(0, executor.getPoolSize()),
                Math.max(0L, executor.getCompletedTaskCount()),
                activeWorkers >= maxConcurrency || queuedTasks > 0
        );
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record CapacitySnapshot(
            int maxConcurrency,
            int activeWorkers,
            int queuedTasks,
            int poolSize,
            long completedTaskCount,
            boolean saturated
    ) {

        public int availableWorkers() {
            return Math.max(0, maxConcurrency - activeWorkers);
        }
    }

    private static final class UiE2eRunnerThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "wp7-ui-e2e-browser-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
