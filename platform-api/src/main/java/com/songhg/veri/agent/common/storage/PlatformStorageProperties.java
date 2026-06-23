package com.songhg.veri.agent.common.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Shared download-storage settings for modules that need controlled file retention and authenticated downloads.
 *
 * <p>The current platform implementation uses local disk only, but the namespace-based root layout keeps module code
 * stable when a future object-storage provider replaces the local implementation.</p>
 */
@ConfigurationProperties(prefix = "veri-agent.storage")
public record PlatformStorageProperties(
        /** Storage provider summary exposed to configuration; local is the only supported mode in this slice. */
        @DefaultValue("local") String provider,
        /** Shared storage root used when a module does not override its own local directory. */
        @DefaultValue("") String rootDir
) {

    public String effectiveProvider() {
        if (!StringUtils.hasText(provider)) {
            return "local";
        }
        String normalized = provider.trim().toLowerCase();
        return "local".equals(normalized) ? normalized : "local";
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
}
