package com.songhg.veri.agent.modelaccess.common;

import java.util.Optional;
import java.util.UUID;

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

    public static void clear() {
        TRACE_ID.remove();
    }
}
