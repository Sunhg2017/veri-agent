package com.songhg.veri.agent.documentinput.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DocumentContentExtractor {

    private static final Pattern DATA_URL = Pattern.compile("^data:([^;,]+)?(?:;[^,]*)?;base64,(.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int DEFAULT_BINARY_MAX_BYTES = 10 * 1024 * 1024;
    private static final int DEFAULT_OCR_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_OCR_MAX_OUTPUT_CHARS = 20000;
    private static final int DEFAULT_OCR_MAX_CONCURRENT_PROCESSES = 2;
    private static final int DEFAULT_MALWARE_SCAN_TIMEOUT_SECONDS = 15;
    private static final int DEFAULT_MALWARE_SCAN_MAX_OUTPUT_CHARS = 2000;
    private static final int DEFAULT_MALWARE_SCAN_MAX_CONCURRENT_PROCESSES = 2;

    private final DocumentInputProperties properties;
    private final Semaphore ocrPermits;
    private final Semaphore malwareScanPermits;
    private final LongSupplier nanoTime;
    private final HttpClient ocrHttpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public DocumentContentExtractor(DocumentInputProperties properties) {
        this(properties, System::nanoTime);
    }

    DocumentContentExtractor(DocumentInputProperties properties, LongSupplier nanoTime) {
        this(properties, nanoTime, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(resolveOcrTimeoutSeconds(properties)))
                .build(), new ObjectMapper());
    }

    DocumentContentExtractor(
            DocumentInputProperties properties,
            LongSupplier nanoTime,
            HttpClient ocrHttpClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.ocrPermits = new Semaphore(ocrMaxConcurrentProcesses(), true);
        this.malwareScanPermits = new Semaphore(malwareScanMaxConcurrentProcesses(), true);
        this.nanoTime = nanoTime;
        this.ocrHttpClient = ocrHttpClient;
        this.objectMapper = objectMapper;
    }

    public ExtractedDocumentContent extract(DocumentSourceType sourceType, String content) {
        if (sourceType != DocumentSourceType.WORD
                && sourceType != DocumentSourceType.PDF
                && sourceType != DocumentSourceType.OCR) {
            return new ExtractedDocumentContent(content, "PLAIN_TEXT");
        }
        DocumentPayload payload = decodePayload(content);
        String extracted = payload.binary()
                ? extractBinary(sourceType, payload.bytes(), payload.declaredMimeType())
                : payload.text();
        if (!StringUtils.hasText(extracted)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    sourceType + " 文档未抽取到有效文本。下一步：确认文件包含可复制文本，扫描件请配置 WP4_OCR_COMMAND 后重试，或改用 Word/Markdown 文档。");
        }
        return new ExtractedDocumentContent(normalizeText(extracted), payload.binary() ? sourceType.name() : "PLAIN_TEXT");
    }

    public boolean ocrConfigured() {
        return StringUtils.hasText(properties.ocrCommand())
                || (ocrRemoteWorkerMode() && ocrRemoteWorkerConfigured());
    }

    public int ocrAvailablePermits() {
        return ocrPermits.availablePermits();
    }

    public int malwareScanAvailablePermits() {
        return malwareScanPermits.availablePermits();
    }

    public boolean ocrLocalCommandExecutionAllowed() {
        String mode = resolvedOcrWorkerMode();
        return "LOCAL_COMMAND".equals(mode)
                || ("HTTP_WORKER".equals(mode) || "EXTERNAL_WORKER".equals(mode))
                && properties.ocrLocalCommandFallbackEnabled();
    }

    private String resolvedOcrWorkerMode() {
        String mode = trimToNull(properties.ocrWorkerMode());
        return mode == null ? "LOCAL_COMMAND" : mode.toUpperCase(Locale.ROOT);
    }

    private boolean ocrRemoteWorkerMode() {
        String mode = resolvedOcrWorkerMode();
        return "HTTP_WORKER".equals(mode) || "EXTERNAL_WORKER".equals(mode);
    }

    private boolean ocrRemoteWorkerConfigured() {
        return StringUtils.hasText(properties.ocrWorkerUrl());
    }

    private String extractBinary(DocumentSourceType sourceType, byte[] bytes, String declaredMimeType) {
        validateBinaryMime(sourceType, declaredMimeType, bytes);
        scanForMalware(bytes);
        return switch (sourceType) {
            case WORD -> extractWord(bytes);
            case PDF -> extractPdf(bytes);
            case OCR -> runOcr(bytes);
            default -> throw new BusinessException(ErrorCode.INVALID_STATE, sourceType + " 不需要二进制抽取");
        };
    }

    private DocumentPayload decodePayload(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "content 不能为空");
        }
        String trimmed = content.trim();
        Matcher matcher = DATA_URL.matcher(trimmed);
        if (matcher.matches()) {
            return binaryPayload(decodeBase64(matcher.group(2), "data URL"), matcher.group(1));
        }
        String compact = trimmed.replaceAll("\\s+", "");
        if (looksLikeBase64(compact)) {
            byte[] decoded = tryDecodeBase64(compact);
            if (decoded != null && shouldTreatAsBinary(decoded)) {
                return binaryPayload(decoded, null);
            }
            if (decoded != null && isText(decoded)) {
                return new DocumentPayload(null, decodeUtf8(decoded), false, null);
            }
        }
        return new DocumentPayload(null, content, false, null);
    }

    private DocumentPayload binaryPayload(byte[] bytes, String declaredMimeType) {
        long limit = documentBinaryMaxBytes();
        if (bytes.length > limit) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "文档二进制内容超过上限: " + limit + " bytes。下一步：压缩或拆分文件，或联系管理员调整 WP4_DOCUMENT_BINARY_MAX_BYTES。");
        }
        return new DocumentPayload(bytes, null, true, normalizeMimeType(declaredMimeType));
    }

    private String extractWord(byte[] bytes) {
        try {
            if (isDocx(bytes)) {
                try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            }
            if (isDoc(bytes)) {
                try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
                     WordExtractor extractor = new WordExtractor(document)) {
                    return extractor.getText();
                }
            }
            if (isText(bytes)) {
                return decodeUtf8(bytes);
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "WORD 内容不是可识别的 doc/docx 文档");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "WORD 文档解析失败: " + exception.getMessage());
        }
    }

    private void scanForMalware(byte[] bytes) {
        String command = trimToNull(properties.malwareScanCommand());
        if (!StringUtils.hasText(command)) {
            return;
        }
        if (!malwareScanPermits.tryAcquire()) {
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED, "文件安全扫描并发处理已达到上限");
        }
        Path input = null;
        try {
            input = Files.createTempFile("wp4-scan-", ".bin");
            Files.write(input, bytes);
            List<String> commandLine = templatedCommand(command, input);
            Process process = new ProcessBuilder(commandLine)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(malwareScanTimeoutSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!completed) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.INVALID_STATE, "文件安全扫描执行超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "文件安全扫描未通过: " + trimForError(output, malwareScanMaxOutputChars()));
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "文件安全扫描执行失败: " + exception.getMessage());
        } finally {
            malwareScanPermits.release();
            if (input != null) {
                try {
                    Files.deleteIfExists(input);
                } catch (Exception ignored) {
                    // Temporary scan input cleanup should not hide the parsing result.
                }
            }
        }
    }

    private String extractPdf(byte[] bytes) {
        if (!isPdf(bytes)) {
            if (isText(bytes)) {
                return decodeUtf8(bytes);
            }
            if (ocrConfigured()) {
                return runOcr(bytes);
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "PDF 内容不是可识别的文本 PDF，扫描件需配置 WP4_OCR_COMMAND。下一步：上传文本型 PDF，或让管理员配置 OCR 后重试。");
        }
        long startedAt = nanoTime.getAsLong();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            ensurePdfParseBudget(startedAt);
            int maxPages = pdfMaxPages();
            if (maxPages > 0 && document.getNumberOfPages() > maxPages) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "PDF 页数超过上限: " + maxPages + "。下一步：拆分 PDF 后重试，或联系管理员调整 WP4_PDF_MAX_PAGES。");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            ensurePdfParseBudget(startedAt);
            if (StringUtils.hasText(text)) {
                return text;
            }
            if (ocrConfigured()) {
                return runOcr(bytes);
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "PDF 未抽取到文本，疑似扫描件。下一步：配置 WP4_OCR_COMMAND 后重试，或上传文本型 PDF/Word/Markdown。");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "PDF 文档解析失败: " + exception.getMessage());
        }
    }

    private void validateBinaryMime(DocumentSourceType sourceType, String declaredMimeType, byte[] bytes) {
        if (!binaryMimeValidationEnabled()) {
            return;
        }
        DetectedBinaryType detected = detectBinaryType(bytes);
        if (!isSourceTypeCompatible(sourceType, detected)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    sourceType + " 内容类型与实际文件内容不匹配: " + detected.label());
        }
        if (declaredMimeType == null || isGenericMimeType(declaredMimeType)) {
            return;
        }
        if (!isMimeCompatible(declaredMimeType, detected)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "声明 MIME 与实际文件内容不匹配: " + declaredMimeType + " != " + detected.label());
        }
    }

    private boolean isSourceTypeCompatible(DocumentSourceType sourceType, DetectedBinaryType detected) {
        return switch (sourceType) {
            case PDF -> detected == DetectedBinaryType.PDF;
            case WORD -> detected == DetectedBinaryType.DOCX || detected == DetectedBinaryType.DOC;
            case OCR -> detected.image || detected == DetectedBinaryType.PDF;
            default -> true;
        };
    }

    private boolean isMimeCompatible(String mimeType, DetectedBinaryType detected) {
        return switch (detected) {
            case PDF -> mimeType.equals("application/pdf");
            case DOCX -> mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    || mimeType.equals("application/zip");
            case DOC -> mimeType.equals("application/msword")
                    || mimeType.equals("application/x-ole-storage");
            case PNG -> mimeType.equals("image/png");
            case JPEG -> mimeType.equals("image/jpeg") || mimeType.equals("image/jpg");
            case GIF -> mimeType.equals("image/gif");
            case BMP -> mimeType.equals("image/bmp") || mimeType.equals("image/x-ms-bmp");
            case TIFF -> mimeType.equals("image/tiff");
            case UNKNOWN_BINARY -> false;
        };
    }

    private DetectedBinaryType detectBinaryType(byte[] bytes) {
        if (isPdf(bytes)) {
            return DetectedBinaryType.PDF;
        }
        if (isDocx(bytes)) {
            return DetectedBinaryType.DOCX;
        }
        if (isDoc(bytes)) {
            return DetectedBinaryType.DOC;
        }
        if (startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return DetectedBinaryType.JPEG;
        }
        if (startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47})) {
            return DetectedBinaryType.PNG;
        }
        if (startsWith(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII))) {
            return DetectedBinaryType.GIF;
        }
        if (startsWith(bytes, "BM".getBytes(StandardCharsets.US_ASCII))) {
            return DetectedBinaryType.BMP;
        }
        if (startsWith(bytes, "II*\0".getBytes(StandardCharsets.ISO_8859_1))
                || startsWith(bytes, "MM\0*".getBytes(StandardCharsets.ISO_8859_1))) {
            return DetectedBinaryType.TIFF;
        }
        return DetectedBinaryType.UNKNOWN_BINARY;
    }

    private void ensurePdfParseBudget(long startedAt) {
        long limitMillis = pdfMaxParseMillis();
        if (limitMillis <= 0) {
            return;
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - startedAt);
        if (elapsedMillis > limitMillis) {
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED,
                    "PDF 解析超过时间上限: " + limitMillis + " ms。下一步：拆分 PDF、减少页数，或联系管理员调整 WP4_PDF_MAX_PARSE_MILLIS。");
        }
    }

    private String runOcr(byte[] bytes) {
        if (!ocrPermits.tryAcquire()) {
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED, "OCR 并发处理已达到上限");
        }
        try {
            if (ocrRemoteWorkerMode()) {
                return runRemoteOcrOrFallback(bytes);
            }
            if ("LOCAL_COMMAND".equals(resolvedOcrWorkerMode())) {
                return runLocalOcrCommand(bytes);
            }
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "不支持的 OCR worker mode: " + resolvedOcrWorkerMode()
                            + "。下一步：设置 WP4_OCR_WORKER_MODE=LOCAL_COMMAND 或 HTTP_WORKER。");
        } finally {
            ocrPermits.release();
        }
    }

    private String runRemoteOcrOrFallback(byte[] bytes) {
        if (!ocrRemoteWorkerConfigured()) {
            if (properties.ocrLocalCommandFallbackEnabled()) {
                return runLocalOcrCommand(bytes);
            }
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "OCR worker 已启用但未配置 WP4_OCR_WORKER_URL。下一步：配置隔离 OCR worker endpoint，或在 dev/test 设置 WP4_OCR_LOCAL_COMMAND_FALLBACK_ENABLED=true。");
        }
        try {
            return runRemoteOcrWorker(bytes);
        } catch (BusinessException exception) {
            if (properties.ocrLocalCommandFallbackEnabled()) {
                return runLocalOcrCommand(bytes);
            }
            throw exception;
        }
    }

    private String runLocalOcrCommand(byte[] bytes) {
        String command = trimToNull(properties.ocrCommand());
        if (!StringUtils.hasText(command)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "OCR 解析需要配置 WP4_OCR_COMMAND。下一步：请管理员配置 OCR provider，或上传可复制文本的 PDF/Word/Markdown 文件。");
        }
        if (!ocrLocalCommandExecutionAllowed()) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "OCR 当前配置为 " + resolvedOcrWorkerMode()
                            + "，已禁止 platform-api 本地执行 WP4_OCR_COMMAND。下一步：请接入隔离 OCR worker，或在 dev/test 设置 WP4_OCR_LOCAL_COMMAND_FALLBACK_ENABLED=true。");
        }
        Path input = null;
        try {
            input = Files.createTempFile("wp4-ocr-", ".bin");
            Files.write(input, bytes);
            List<String> commandLine = templatedCommand(command, input);
            Process process = new ProcessBuilder(commandLine)
                    .redirectErrorStream(true)
                    .start();
            boolean completed = process.waitFor(ocrTimeout().toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!completed) {
                process.destroyForcibly();
                throw new BusinessException(ErrorCode.INVALID_STATE, "OCR 命令执行超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "OCR 命令执行失败: " + trimForError(output, 300));
            }
            String normalized = truncateOcrOutput(output);
            if (!StringUtils.hasText(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OCR 未识别到有效文本");
            }
            return normalized;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "OCR 命令执行失败: " + exception.getMessage());
        } finally {
            if (input != null) {
                try {
                    Files.deleteIfExists(input);
                } catch (Exception ignored) {
                    // Temporary OCR input cleanup should not hide the parsing result.
                }
            }
        }
    }

    private String runRemoteOcrWorker(byte[] bytes) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "contentBase64", Base64.getEncoder().encodeToString(bytes),
                    "maxOutputChars", ocrMaxOutputChars(),
                    "timeoutSeconds", ocrTimeoutSeconds()
            ));
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(properties.ocrWorkerUrl().trim()))
                    .timeout(ocrTimeout())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
            String token = trimToNull(properties.ocrWorkerToken());
            if (token != null) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = ocrHttpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException(ErrorCode.INVALID_STATE,
                        "OCR worker 返回失败: HTTP " + response.statusCode()
                                + "。下一步：检查隔离 worker 健康、队列和资源配额。");
            }
            String text = parseRemoteOcrText(response.body());
            String normalized = truncateOcrOutput(text);
            if (!StringUtils.hasText(normalized)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "OCR worker 未识别到有效文本");
            }
            return normalized;
        } catch (BusinessException exception) {
            throw exception;
        } catch (HttpTimeoutException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "OCR worker 调用超时。下一步：检查隔离 worker 资源、队列堆积或调大 WP4_OCR_TIMEOUT_SECONDS。");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INVALID_STATE, "OCR worker 调用被中断");
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "WP4_OCR_WORKER_URL 配置不合法。下一步：配置 http(s) OCR worker endpoint。");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "OCR worker 调用失败: " + trimForError(exception.getMessage(), 160));
        }
    }

    private String parseRemoteOcrText(String body) throws IOException {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode text = root.path("text");
        if (text.isMissingNode()) {
            text = root.path("content");
        }
        if (text.isMissingNode()) {
            text = root.path("data").path("text");
        }
        return text.isTextual() ? text.asText() : "";
    }

    private List<String> templatedCommand(String template, Path input) {
        String command = template.contains("{input}")
                ? template.replace("{input}", input.toAbsolutePath().toString())
                : template + " " + input.toAbsolutePath();
        List<String> tokens = splitCommand(command);
        if (tokens.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "命令不能为空");
        }
        return tokens;
    }

    private List<String> splitCommand(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (Character.isWhitespace(ch) && !quoted) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private Duration ocrTimeout() {
        return Duration.ofSeconds(ocrTimeoutSeconds());
    }

    private static int resolveOcrTimeoutSeconds(DocumentInputProperties properties) {
        return properties.ocrTimeoutSeconds() <= 0
                ? DEFAULT_OCR_TIMEOUT_SECONDS
                : properties.ocrTimeoutSeconds();
    }

    private String truncateOcrOutput(String output) {
        if (output == null) {
            return "";
        }
        int limit = ocrMaxOutputChars();
        String normalized = normalizeText(output);
        return normalized.length() <= limit ? normalized : normalized.substring(0, limit);
    }

    private int malwareScanMaxOutputChars() {
        return properties.malwareScanMaxOutputChars() <= 0
                ? DEFAULT_MALWARE_SCAN_MAX_OUTPUT_CHARS
                : properties.malwareScanMaxOutputChars();
    }

    private String normalizeText(String value) {
        return value == null ? null : value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private boolean looksLikeBase64(String value) {
        return value.length() >= 16
                && value.length() % 4 == 0
                && value.matches("^[A-Za-z0-9+/]+={0,2}$");
    }

    private byte[] tryDecodeBase64(String value) {
        try {
            return decodeBase64(value, "base64");
        } catch (BusinessException exception) {
            return null;
        }
    }

    private byte[] decodeBase64(String value, String label) {
        try {
            return Base64.getDecoder().decode(value.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, label + " 内容不是合法 base64");
        }
    }

    private boolean shouldTreatAsBinary(byte[] bytes) {
        return isPdf(bytes) || isDocx(bytes) || isDoc(bytes) || hasBinaryMagic(bytes) || !isText(bytes);
    }

    private boolean isPdf(byte[] bytes) {
        return startsWith(bytes, "%PDF".getBytes(StandardCharsets.US_ASCII));
    }

    private boolean isDocx(byte[] bytes) {
        return startsWith(bytes, new byte[]{0x50, 0x4b, 0x03, 0x04})
                || startsWith(bytes, new byte[]{0x50, 0x4b, 0x05, 0x06})
                || startsWith(bytes, new byte[]{0x50, 0x4b, 0x07, 0x08});
    }

    private boolean isDoc(byte[] bytes) {
        return startsWith(bytes, new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0});
    }

    private boolean hasBinaryMagic(byte[] bytes) {
        return startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})
                || startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47})
                || startsWith(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII))
                || startsWith(bytes, "BM".getBytes(StandardCharsets.US_ASCII))
                || startsWith(bytes, "II*\0".getBytes(StandardCharsets.ISO_8859_1))
                || startsWith(bytes, "MM\0*".getBytes(StandardCharsets.ISO_8859_1));
    }

    private String normalizeMimeType(String mimeType) {
        String normalized = trimToNull(mimeType);
        if (normalized == null) {
            return null;
        }
        int semicolon = normalized.indexOf(';');
        String base = semicolon >= 0 ? normalized.substring(0, semicolon) : normalized;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isGenericMimeType(String mimeType) {
        return mimeType.equals("application/octet-stream") || mimeType.equals("binary/octet-stream");
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isText(byte[] bytes) {
        try {
            decodeUtf8(bytes);
            int checked = Math.min(bytes.length, 4096);
            int controls = 0;
            for (int i = 0; i < checked; i++) {
                int value = bytes[i] & 0xff;
                if (value < 0x20 && value != '\n' && value != '\r' && value != '\t') {
                    controls++;
                }
            }
            return checked == 0 || controls <= Math.max(2, checked / 20);
        } catch (BusinessException exception) {
            return false;
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文档内容不是有效 UTF-8 文本");
        }
    }

    private String trimForError(String output, int limit) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String normalized = output.trim().replaceAll("\\s+", " ");
        int safeLimit = Math.max(1, limit);
        return normalized.length() <= safeLimit ? normalized : normalized.substring(0, safeLimit);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record ExtractedDocumentContent(String text, String extractionSource) {
    }

    private enum DetectedBinaryType {
        PDF("application/pdf", false),
        DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
        DOC("application/msword", false),
        PNG("image/png", true),
        JPEG("image/jpeg", true),
        GIF("image/gif", true),
        BMP("image/bmp", true),
        TIFF("image/tiff", true),
        UNKNOWN_BINARY("unknown-binary", false);

        private final String label;
        private final boolean image;

        DetectedBinaryType(String label, boolean image) {
            this.label = label;
            this.image = image;
        }

        String label() {
            return label;
        }
    }

    private record DocumentPayload(byte[] bytes, String text, boolean binary, String declaredMimeType) {
    }
}
