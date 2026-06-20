package com.songhg.veri.agent.execution.application;

import com.songhg.veri.agent.execution.application.view.ExecutionRunDetailResponse;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes sanitized WP9 execution run events to ephemeral subscribers after durable state changes.
 */
public interface ExecutionRunEventPublisher {

    void publish(
            ExecutionRunDetailResponse run,
            String level,
            String stage,
            String message,
            UUID nodeRunId,
            Map<String, Object> metadata
    );
}
