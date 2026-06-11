package com.songhg.veri.agent.apiautomation.application.parser;

import java.util.List;
import java.util.Map;

public record OpenApiParseResult(
        String sanitizedSpecJson,
        Map<String, Object> summary,
        List<ParsedOpenApiEndpoint> endpoints
) {
}
