package com.songhg.veri.agent.common.storage;

import java.nio.file.Path;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Shared download-storage settings for modules that need controlled file retention and authenticated downloads.
 *
 * <p>Object storage is the platform default so production deployments converge on one shared artifact plane, while
 * local profile and explicit module overrides can still pin individual flows back to local disk when needed.</p>
 */
@ConfigurationProperties(prefix = "veri-agent.storage")
public record PlatformStorageProperties(
        /** Platform storage provider, currently local or oss. */
        @DefaultValue("oss") String provider,
        /** Shared storage root used when a module does not override its own local directory. */
        @DefaultValue("") String rootDir,
        /** Shared OSS settings used by modules that do not override storage locally. */
        @DefaultValue OssProperties oss
) {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("local", "oss");

    public String effectiveProvider() {
        String normalized = StringUtils.hasText(provider) ? provider.trim().toLowerCase() : "oss";
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException("unsupported platform storage provider: " + provider);
        }
        return normalized;
    }

    public Path effectiveRootDir() {
        if (StringUtils.hasText(rootDir)) {
            return Path.of(rootDir.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "veri-agent", "storage").toAbsolutePath().normalize();
    }

    public Path namespaceRoot(String namespace) {
        return effectiveRootDir().resolve(namespace).toAbsolutePath().normalize();
    }

    public OssProperties effectiveOss() {
        return oss == null ? new OssProperties("", "", "", "", "veri-agent/storage") : oss;
    }

    public record OssProperties(
            /** External OSS endpoint, for example https://oss-cn-hangzhou.aliyuncs.com */
            @DefaultValue("") String endpoint,
            /** Bucket used for platform-managed artifacts. */
            @DefaultValue("") String bucket,
            /** Access key id for platform artifact writes and reads. */
            @DefaultValue("") String accessKeyId,
            /** Access key secret for platform artifact writes and reads. */
            @DefaultValue("") String accessKeySecret,
            /** Shared key prefix under the bucket before namespace-specific paths are appended. */
            @DefaultValue("veri-agent/storage") String keyPrefix
    ) {

        public String requiredEndpoint() {
            return requireText(endpoint, "veri-agent.storage.oss.endpoint");
        }

        public String requiredBucket() {
            return requireText(bucket, "veri-agent.storage.oss.bucket");
        }

        public String requiredAccessKeyId() {
            return requireText(accessKeyId, "veri-agent.storage.oss.access-key-id");
        }

        public String requiredAccessKeySecret() {
            return requireText(accessKeySecret, "veri-agent.storage.oss.access-key-secret");
        }

        public String normalizedKeyPrefix() {
            if (!StringUtils.hasText(keyPrefix)) {
                return "";
            }
            String normalized = keyPrefix.trim().replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        private static String requireText(String value, String propertyName) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalStateException(propertyName + " is required when veri-agent.storage.provider=oss");
            }
            return value.trim();
        }
    }
}
