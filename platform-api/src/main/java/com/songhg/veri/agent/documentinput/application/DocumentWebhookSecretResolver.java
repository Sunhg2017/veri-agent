package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.common.secret.SecretResolveContext;
import com.songhg.veri.agent.common.secret.SecretProvider;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceConfig;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentWebhookSecretResolver {

    public static final String DEFAULT_WEBHOOK_SECRET_REF = "wp4-webhook-default";
    private static final String LOCAL_SECRET_URI_PREFIX = "secret://wp4/";

    private final DocumentInputProperties properties;
    private final List<SecretProvider> secretProviders;

    @Autowired
    public DocumentWebhookSecretResolver(DocumentInputProperties properties, ObjectProvider<SecretProvider> secretProviders) {
        this(properties, secretProviders.orderedStream().toList());
    }

    DocumentWebhookSecretResolver(DocumentInputProperties properties) {
        this(properties, List.of());
    }

    DocumentWebhookSecretResolver(DocumentInputProperties properties, List<SecretProvider> secretProviders) {
        this.properties = properties;
        this.secretProviders = secretProviders == null ? List.of() : List.copyOf(secretProviders);
    }

    public String resolve(DocumentSourceConfig source) {
        String secretRef = trimToNull(source.secretRef());
        if (source.sourceType() == DocumentSourceType.CUSTOM_API && secretRef == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 密钥引用未配置");
        }
        for (SecretProvider provider : secretProviders) {
            var resolved = provider.resolve(secretRef, new SecretResolveContext(
                    "WEBHOOK_SIGNING",
                    "wp4-document-input",
                    "CONFIG",
                    source.id() == null ? null : source.id().toString()
            ));
            if (resolved.isPresent()) {
                return resolved.get().value();
            }
        }
        if (!properties.localWebhookSecretFallbackEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 密钥引用未解析: " + secretRef);
        }
        Map<String, String> configured = properties.webhookSecrets();
        if (configured != null && secretRef != null) {
            String resolved = trimToNull(configured.get(secretRef));
            if (resolved != null) {
                return resolved;
            }
        }
        if (DEFAULT_WEBHOOK_SECRET_REF.equals(secretRef) || secretRef != null && secretRef.startsWith(LOCAL_SECRET_URI_PREFIX)) {
            return defaultWebhookSecret();
        }
        throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 密钥引用未解析: " + secretRef);
    }

    private String defaultWebhookSecret() {
        if (!StringUtils.hasText(properties.webhookSecret())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "webhook 签名密钥未配置");
        }
        return properties.webhookSecret();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
