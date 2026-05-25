package com.songhg.veri.agent.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.service-caller")
public record ServiceCallerProperties(
        List<String> modelAccessTrustedServices,
        List<String> assetTrustedServices,
        List<String> documentInputTrustedServices,
        List<String> testDesignTrustedServices
) {
    public List<String> safeModelAccessTrustedServices() {
        return safeList(modelAccessTrustedServices);
    }

    public List<String> safeAssetTrustedServices() {
        return safeList(assetTrustedServices);
    }

    public List<String> safeDocumentInputTrustedServices() {
        return safeList(documentInputTrustedServices);
    }

    public List<String> safeTestDesignTrustedServices() {
        return safeList(testDesignTrustedServices);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
