package com.songhg.veri.agent.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.service-caller")
public record ServiceCallerProperties(
        /** 允许调用模型访问模块的服务编码列表。 */
        List<String> modelAccessTrustedServices,
        /** 允许调用资产模块的服务编码列表。 */
        List<String> assetTrustedServices,
        /** 允许调用文档输入模块的服务编码列表。 */
        List<String> documentInputTrustedServices,
        /** 允许调用测试设计模块的服务编码列表。 */
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
