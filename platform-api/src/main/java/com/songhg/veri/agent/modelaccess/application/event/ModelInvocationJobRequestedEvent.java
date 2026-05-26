package com.songhg.veri.agent.modelaccess.application.event;

import java.util.UUID;

public record ModelInvocationJobRequestedEvent(UUID jobId) {

    public static final String EVENT_TYPE = "model-access.invocation-job.requested";
}
