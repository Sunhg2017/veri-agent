package com.songhg.veri.agent.modelaccess.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.modelaccess.api.response.InvokeModelResponse;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Component
class ModelInvocationStreamSupport {

    private static final int STREAM_CHUNK_CODE_POINTS = 48;

    private final ObjectMapper objectMapper;

    ModelInvocationStreamSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the SSE body after the normal invocation path has already enforced policy and budget.
     * The stream is intentionally derived from the persisted invocation response to keep sync and
     * stream semantics identical.
     */
    StreamingResponseBody stream(InvokeModelResponse response, String traceId) {
        return outputStream -> {
            writeSse(outputStream, "metadata", streamMetadata(response, traceId));
            int index = 0;
            for (String chunk : streamChunks(response.content())) {
                writeSse(outputStream, "delta", Map.of("index", index++, "content", chunk));
            }
            writeSse(outputStream, "done", Map.of(
                    "invocationId", response.invocationId(),
                    "finishReason", "stop"
            ));
        };
    }

    private Map<String, Object> streamMetadata(InvokeModelResponse response, String traceId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("invocationId", response.invocationId());
        metadata.put("providerId", response.providerId());
        metadata.put("providerName", response.providerName());
        metadata.put("modelName", response.modelName());
        metadata.put("fallbackUsed", response.fallbackUsed());
        metadata.put("inputTokens", response.inputTokens());
        metadata.put("outputTokens", response.outputTokens());
        metadata.put("totalCost", response.totalCost());
        metadata.put("traceId", traceId);
        return metadata;
    }

    private List<String> streamChunks(String content) {
        String safeContent = content == null ? "" : content;
        if (safeContent.isEmpty()) {
            return List.of();
        }
        int[] codePoints = safeContent.codePoints().toArray();
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int index = 0; index < codePoints.length; index += STREAM_CHUNK_CODE_POINTS) {
            int length = Math.min(STREAM_CHUNK_CODE_POINTS, codePoints.length - index);
            chunks.add(new String(codePoints, index, length));
        }
        return chunks;
    }

    private void writeSse(OutputStream outputStream, String event, Object data) throws java.io.IOException {
        String payload = "event: " + event + "\n"
                + "data: " + objectMapper.writeValueAsString(data) + "\n\n";
        outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
