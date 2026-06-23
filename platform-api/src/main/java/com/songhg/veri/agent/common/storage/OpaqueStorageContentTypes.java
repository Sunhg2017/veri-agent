package com.songhg.veri.agent.common.storage;

import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

final class OpaqueStorageContentTypes {

    private OpaqueStorageContentTypes() {
    }

    static String normalize(String explicitContentType, String fileName, String detectedContentType) {
        if (StringUtils.hasText(explicitContentType)) {
            return explicitContentType.trim();
        }
        if (StringUtils.hasText(detectedContentType)) {
            return detectedContentType.trim();
        }
        String lowered = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowered.endsWith(".json") || lowered.endsWith(".har")) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        if (lowered.endsWith(".md")) {
            return "text/markdown;charset=UTF-8";
        }
        if (lowered.endsWith(".xml")) {
            return MediaType.APPLICATION_XML_VALUE;
        }
        if (lowered.endsWith(".zip")) {
            return "application/zip";
        }
        if (lowered.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lowered.endsWith(".webm")) {
            return "video/webm";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
