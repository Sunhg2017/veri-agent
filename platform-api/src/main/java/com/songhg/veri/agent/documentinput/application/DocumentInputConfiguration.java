package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Exposes configuration values for the document-input module health endpoint.
 *
 * <p>Extracted from {@link DocumentContentExtractor} which was mixing content
 * extraction logic with configuration access.
 */
@Component
public class DocumentInputConfiguration {

    private static final int DEFAULT_BINARY_MAX_BYTES = 10 * 1024 * 1024;
    private static final int DEFAULT_OCR_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_OCR_MAX_OUTPUT_CHARS = 20000;
    private static final int DEFAULT_OCR_MAX_CONCURRENT_PROCESSES = 2;
    private static final int DEFAULT_MALWARE_SCAN_TIMEOUT_SECONDS = 15;
    private static final int DEFAULT_MALWARE_SCAN_MAX_OUTPUT_CHARS = 2000;
    private static final int DEFAULT_MALWARE_SCAN_MAX_CONCURRENT_PROCESSES = 2;

    private final DocumentInputProperties properties;

    public DocumentInputConfiguration(DocumentInputProperties properties) {
        this.properties = properties;
    }

    public boolean ocrConfigured() {
        return StringUtils.hasText(properties.ocrCommand())
                || (ocrRemoteWorkerMode() && ocrRemoteWorkerConfigured());
    }

    public int ocrMaxConcurrentProcesses() {
        return properties.ocrMaxConcurrentProcesses() <= 0
                ? DEFAULT_OCR_MAX_CONCURRENT_PROCESSES
                : properties.ocrMaxConcurrentProcesses();
    }

    public int ocrTimeoutSeconds() {
        return properties.ocrTimeoutSeconds() <= 0
                ? DEFAULT_OCR_TIMEOUT_SECONDS
                : properties.ocrTimeoutSeconds();
    }

    public int ocrMaxOutputChars() {
        return properties.ocrMaxOutputChars() <= 0
                ? DEFAULT_OCR_MAX_OUTPUT_CHARS
                : properties.ocrMaxOutputChars();
    }

    public String ocrWorkerMode() {
        return properties.ocrWorkerMode() == null ? "LOCAL_COMMAND" : properties.ocrWorkerMode();
    }

    public boolean ocrRemoteWorkerMode() {
        return "REMOTE_HTTP".equalsIgnoreCase(ocrWorkerMode());
    }

    public boolean ocrRemoteWorkerConfigured() {
        return StringUtils.hasText(properties.ocrWorkerUrl());
    }

    public boolean ocrWorkerTokenConfigured() {
        return StringUtils.hasText(properties.ocrWorkerToken());
    }

    public boolean ocrLocalCommandFallbackEnabled() {
        return properties.ocrLocalCommandFallbackEnabled();
    }

    public boolean binaryMimeValidationEnabled() {
        return properties.binaryMimeValidationEnabled();
    }

    public boolean malwareScanEnabled() {
        return StringUtils.hasText(properties.malwareScanCommand());
    }

    public int malwareScanTimeoutSeconds() {
        return properties.malwareScanTimeoutSeconds() <= 0
                ? DEFAULT_MALWARE_SCAN_TIMEOUT_SECONDS
                : properties.malwareScanTimeoutSeconds();
    }

    public int malwareScanMaxConcurrentProcesses() {
        return properties.malwareScanMaxConcurrentProcesses() <= 0
                ? DEFAULT_MALWARE_SCAN_MAX_CONCURRENT_PROCESSES
                : properties.malwareScanMaxConcurrentProcesses();
    }

    public int pdfMaxPages() {
        return properties.pdfMaxPages();
    }

    public long pdfMaxParseMillis() {
        return properties.pdfMaxParseMillis();
    }

    public long documentBinaryMaxBytes() {
        return properties.documentBinaryMaxBytes() <= 0
                ? DEFAULT_BINARY_MAX_BYTES
                : properties.documentBinaryMaxBytes();
    }
}
