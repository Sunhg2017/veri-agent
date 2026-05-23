package com.songhg.veri.agent.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.service-caller")
public record ServiceCallerProperties(
        List<String> modelAccessTrustedServices,
        List<String> assetTrustedServices,
        List<String> documentInputTrustedServices
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

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
