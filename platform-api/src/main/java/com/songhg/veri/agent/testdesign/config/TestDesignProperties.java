package com.songhg.veri.agent.testdesign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "veri-agent.test-design")
public record TestDesignProperties(
        String serviceToken,
        @DefaultValue("true") boolean generationEnabled,
        @DefaultValue("RULE_TEMPLATE") String generationMode,
        @DefaultValue("wp5-test-design-v1") String promptKey,
        @DefaultValue("1.0.0") String promptVersion,
        @DefaultValue("20") int maxRequirementsPerTask,
        @DefaultValue("3") int maxCasesPerRequirement,
        @DefaultValue("100") int batchActionLimit
) {
}
