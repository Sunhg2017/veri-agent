package com.songhg.veri.agent.common.trace;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "trace_id";

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String createTraceId() {
        return "trc_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getTraceId() {
        return Optional.ofNullable(TRACE_ID.get()).orElseGet(TraceContext::createTraceId);
    }

    public static String getOrCreateTraceId() {
        String traceId = TRACE_ID.get();
        if (!hasText(traceId)) {
            traceId = createTraceId();
            setTraceId(traceId);
        }
        return traceId;
    }

    /**
     * Opens a trace scope for async/event workers and restores the previous thread context on close.
     */
    public static TraceScope open(String traceId) {
        String previousTraceId = TRACE_ID.get();
        String previousMdcTraceId = MDC.get(MDC_TRACE_ID);
        String effectiveTraceId = hasText(traceId) ? traceId : createTraceId();
        TRACE_ID.set(effectiveTraceId);
        MDC.put(MDC_TRACE_ID, effectiveTraceId);
        return new TraceScope(previousTraceId, previousMdcTraceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static final class TraceScope implements AutoCloseable {

        private final String previousTraceId;
        private final String previousMdcTraceId;

        private TraceScope(String previousTraceId, String previousMdcTraceId) {
            this.previousTraceId = previousTraceId;
            this.previousMdcTraceId = previousMdcTraceId;
        }

        @Override
        public void close() {
            if (hasText(previousTraceId)) {
                TRACE_ID.set(previousTraceId);
            } else {
                TRACE_ID.remove();
            }
            if (hasText(previousMdcTraceId)) {
                MDC.put(MDC_TRACE_ID, previousMdcTraceId);
            } else {
                MDC.remove(MDC_TRACE_ID);
            }
        }
    }
}
