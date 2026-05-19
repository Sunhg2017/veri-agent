package com.songhg.veri.agent.common.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("db")
@Order(20)
public class ExternalSecretProvider implements SecretProvider {

    private final JdbcTemplate jdbcTemplate;
    private final SecretProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ExternalSecretProvider(JdbcTemplate jdbcTemplate, SecretProviderProperties properties, ObjectMapper objectMapper) {
        this(jdbcTemplate, properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(resolveTimeout(properties))
                .build());
    }

    ExternalSecretProvider(
            JdbcTemplate jdbcTemplate,
            SecretProviderProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public Optional<ResolvedSecret> resolve(String secretRef, SecretResolveContext context) {
        if (!StringUtils.hasText(secretRef)) {
            return Optional.empty();
        }
        ExternalSecretRow row = findExternalSecret(secretRef.trim()).orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        validateContext(row, context);
        if (!StringUtils.hasText(properties.externalResolveUrl())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "外部 Vault/KMS resolve endpoint 未配置");
        }
        try {
            String payload = objectMapper.writeValueAsString(new ResolveRequest(
                    row.secretRef(),
                    row.providerCode(),
                    row.providerType(),
                    row.secretVersion(),
                    context == null ? null : context.purpose(),
                    context == null ? null : context.callerService(),
                    context == null ? null : context.scopeType(),
                    context == null ? null : context.scopeId()
            ));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.externalResolveUrl().trim()))
                    .timeout(resolveTimeout(properties))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (StringUtils.hasText(properties.externalAuthToken())) {
                builder.header("Authorization", "Bearer " + properties.externalAuthToken().trim());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                return Optional.empty();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                        "外部 Vault/KMS 返回异常状态: " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.has("data") && root.path("data").isObject() ? root.path("data") : root;
            String value = text(data, "value", "secret", "plaintext");
            if (!StringUtils.hasText(value)) {
                throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR, "外部 Vault/KMS 响应缺少密钥值");
            }
            return Optional.of(new ResolvedSecret(
                    row.secretRef(),
                    value,
                    firstText(text(data, "provider"), row.providerCode()),
                    firstText(text(data, "version"), row.secretVersion())
            ));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "外部 Vault/KMS 密钥解析失败: " + exception.getMessage());
        }
    }

    private Optional<ExternalSecretRow> findExternalSecret(String secretRef) {
        return jdbcTemplate.query("""
                select sr.secret_ref,
                       sr.purpose,
                       sr.scope_type,
                       sr.scope_id::text as scope_id,
                       sr.secret_version,
                       sp.provider_code,
                       sp.provider_type
                from secret_reference sr
                join secret_provider sp on sp.id = sr.provider_id
                where sr.secret_ref = ?
                  and sr.status = 'ACTIVE'
                  and (sr.expires_at is null or sr.expires_at > now())
                  and sr.deleted_at is null
                  and sp.status = 'ENABLED'
                  and sp.deleted_at is null
                  and sp.provider_type in ('VAULT','KMS')
                """, (rs, rowNum) -> new ExternalSecretRow(
                rs.getString("secret_ref"),
                rs.getString("purpose"),
                rs.getString("scope_type"),
                rs.getString("scope_id"),
                rs.getString("secret_version"),
                rs.getString("provider_code"),
                rs.getString("provider_type")
        ), secretRef).stream().findFirst();
    }

    private void validateContext(ExternalSecretRow row, SecretResolveContext context) {
        if (context == null) {
            return;
        }
        if (StringUtils.hasText(context.purpose()) && !context.purpose().trim().equalsIgnoreCase(row.purpose())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥用途不匹配: " + row.secretRef());
        }
        if (StringUtils.hasText(context.scopeType()) && !context.scopeType().trim().equalsIgnoreCase(row.scopeType())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域类型不匹配: " + row.secretRef());
        }
        if (StringUtils.hasText(context.scopeId()) && !context.scopeId().trim().equalsIgnoreCase(row.scopeId())) {
            throw new BusinessException(ErrorCode.SECRET_PROVIDER_ERROR,
                    "密钥作用域不匹配: " + row.secretRef());
        }
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static Duration resolveTimeout(SecretProviderProperties properties) {
        int seconds = properties.externalTimeoutSeconds() <= 0 ? 3 : properties.externalTimeoutSeconds();
        return Duration.ofSeconds(seconds);
    }

    private record ResolveRequest(
            String secretRef,
            String providerCode,
            String providerType,
            String secretVersion,
            String purpose,
            String callerService,
            String scopeType,
            String scopeId
    ) {
    }

    private record ExternalSecretRow(
            String secretRef,
            String purpose,
            String scopeType,
            String scopeId,
            String secretVersion,
            String providerCode,
            String providerType
    ) {
    }
}
