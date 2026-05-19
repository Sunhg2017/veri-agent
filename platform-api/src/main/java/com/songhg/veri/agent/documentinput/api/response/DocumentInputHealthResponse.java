package com.songhg.veri.agent.documentinput.api.response;

public record DocumentInputHealthResponse(
        String service,
        String status,
        int supportedSourceTypes,
        boolean inputEnabled,
        boolean webhookEnabled,
        boolean modelParseEnabled,
        long webhookMaxPayloadBytes,
        int batchActionLimit
) {
}
