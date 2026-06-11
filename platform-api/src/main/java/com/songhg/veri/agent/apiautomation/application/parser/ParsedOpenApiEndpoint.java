package com.songhg.veri.agent.apiautomation.application.parser;

import java.util.List;

public record ParsedOpenApiEndpoint(
        String serviceName,
        String operationId,
        String httpMethod,
        String path,
        String summary,
        List<String> tags,
        int parameterCount,
        boolean requestBodyPresent,
        List<String> responseStatuses,
        String schemaDigest
) {
}
