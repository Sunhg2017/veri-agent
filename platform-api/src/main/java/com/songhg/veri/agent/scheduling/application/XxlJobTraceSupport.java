package com.songhg.veri.agent.scheduling.application;

import com.songhg.veri.agent.common.trace.TraceContext;
import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import com.xxl.job.core.context.XxlJobHelper;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared trace, logging and failure propagation helpers for XXL-JOB handlers.
 */
final class XxlJobTraceSupport {

    private static final Logger log = LoggerFactory.getLogger(XxlJobTraceSupport.class);
    private static final int MAX_SUMMARY_LENGTH = 512;

    private XxlJobTraceSupport() {
    }

    static <T> T execute(String jobName, Callable<T> callable) throws Exception {
        String traceId = TraceContext.createTraceId();
        try (TraceContext.TraceScope ignored = TraceContext.open(traceId)) {
            T result = callable.call();
            XxlJobHelper.handleSuccess("traceId=" + traceId);
            XxlJobHelper.log("{} completed, traceId={}", jobName, traceId);
            return result;
        } catch (RuntimeException exception) {
            String sanitizedError = sanitizedSummary(exception.getMessage(), jobName + " failed");
            XxlJobHelper.handleFail("traceId=" + traceId + ", error=" + sanitizedError);
            XxlJobHelper.log("{} failed, traceId={}, error={}", jobName, traceId, sanitizedError);
            log.warn("{} failed, traceId={}, error={}", jobName, traceId, sanitizedError);
            log.debug("{} failure details", jobName, exception);
            throw exception;
        } catch (Exception exception) {
            String sanitizedError = sanitizedSummary(exception.getMessage(), jobName + " failed");
            XxlJobHelper.handleFail("traceId=" + traceId + ", error=" + sanitizedError);
            XxlJobHelper.log("{} failed, traceId={}, error={}", jobName, traceId, sanitizedError);
            log.warn("{} failed, traceId={}, error={}", jobName, traceId, sanitizedError);
            log.debug("{} failure details", jobName, exception);
            throw exception;
        }
    }

    private static String sanitizedSummary(String value, String fallback) {
        return SensitiveTextSanitizer.sanitizedErrorSummary(value, fallback, MAX_SUMMARY_LENGTH);
    }
}
