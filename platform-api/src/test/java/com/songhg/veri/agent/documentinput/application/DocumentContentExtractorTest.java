package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentContentExtractorTest {

    @Test
    void extractsRealDocxTextFromBase64DataUrl() throws Exception {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties(""));

        String text = extractor.extract(DocumentSourceType.WORD, dataUrl(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("Word login requirement", "Priority: HIGH", "Acceptance Criteria:", "- login succeeds")
        )).text();

        assertThat(text).contains("Word login requirement", "Priority: HIGH", "login succeeds");
    }

    @Test
    void extractsRealPdfTextFromBase64DataUrl() throws Exception {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties(""));

        String text = extractor.extract(DocumentSourceType.PDF, dataUrl(
                "application/pdf",
                pdf("PDF refund requirement", "Priority: LOW", "Acceptance Criteria:", "refund succeeds")
        )).text();

        assertThat(text).contains("PDF refund requirement", "Priority: LOW", "refund succeeds");
    }

    @Test
    void runsConfiguredOcrCommandForBinaryOcrInput() {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties("/bin/cat {input}"));
        String content = dataUrl("image/png", withPngMagic("""
                OCR invoice requirement
                Priority: HIGH
                Acceptance Criteria:
                - invoice image is parsed
                """));

        String text = extractor.extract(DocumentSourceType.OCR, content).text();

        assertThat(text).contains("OCR invoice requirement", "invoice image is parsed");
    }

    @Test
    void rejectsForgedPdfMimeWhenValidationIsEnabled() {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties(""));
        String content = dataUrl("image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a});

        assertThatThrownBy(() -> extractor.extract(DocumentSourceType.PDF, content))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 内容类型与实际文件内容不匹配");
    }

    @Test
    void rejectsDeclaredPdfMimeWithDocxContentWhenValidationIsEnabled() throws Exception {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties(""));

        assertThatThrownBy(() -> extractor.extract(DocumentSourceType.PDF, dataUrl(
                "application/pdf",
                docx("Forged document")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 内容类型与实际文件内容不匹配");
    }

    @Test
    void allowsForgedPdfMimeWhenValidationIsDisabledAndContentIsText() {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties("", false, 0, 0));

        String text = extractor.extract(DocumentSourceType.PDF, dataUrl(
                "application/pdf",
                "fallback text requirement".getBytes(StandardCharsets.UTF_8)
        )).text();

        assertThat(text).contains("fallback text requirement");
    }

    @Test
    void rejectsPdfOverConfiguredPageLimit() throws Exception {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties("", true, 1, 0));

        assertThatThrownBy(() -> extractor.extract(DocumentSourceType.PDF, dataUrl(
                "application/pdf",
                pdfPages(2, "Page limited requirement")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 页数超过上限: 1");
    }

    @Test
    void rejectsPdfWhenParseTimeBudgetIsExceeded() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DocumentContentExtractor extractor = new DocumentContentExtractor(
                properties("", true, 0, 1),
                () -> calls.getAndIncrement() == 0 ? 0 : TimeUnit.MILLISECONDS.toNanos(2)
        );

        assertThatThrownBy(() -> extractor.extract(DocumentSourceType.PDF, dataUrl(
                "application/pdf",
                pdf("Timed PDF requirement")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 解析超过时间上限: 1 ms");
    }

    @Test
    void runsConfiguredMalwareScanBeforeBinaryExtraction() {
        DocumentContentExtractor extractor = new DocumentContentExtractor(
                properties("", true, 0, 0, "/bin/sh -c \"echo malware >&2; exit 7\"")
        );

        assertThatThrownBy(() -> extractor.extract(DocumentSourceType.OCR, dataUrl("image/png", withPngMagic("unsafe"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件安全扫描未通过");
    }

    private static byte[] docx(String... paragraphs) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdf(String... lines) throws Exception {
        return pdfPages(1, lines);
    }

    private static byte[] pdfPages(int pages, String... lines) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 740);
                    for (String line : lines) {
                        content.showText(line);
                        content.newLineAtOffset(0, -16);
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] withPngMagic(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[8 + body.length];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4e;
        bytes[3] = 0x47;
        bytes[4] = 0x0d;
        bytes[5] = 0x0a;
        bytes[6] = 0x1a;
        bytes[7] = 0x0a;
        System.arraycopy(body, 0, bytes, 8, body.length);
        return bytes;
    }

    private static String dataUrl(String mimeType, byte[] bytes) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static DocumentInputProperties properties(String ocrCommand) {
        return properties(ocrCommand, true, 0, 0);
    }

    private static DocumentInputProperties properties(
            String ocrCommand,
            boolean binaryMimeValidationEnabled,
            int pdfMaxPages,
            long pdfMaxParseMillis
    ) {
        return properties(ocrCommand, binaryMimeValidationEnabled, pdfMaxPages, pdfMaxParseMillis, "");
    }

    private static DocumentInputProperties properties(
            String ocrCommand,
            boolean binaryMimeValidationEnabled,
            int pdfMaxPages,
            long pdfMaxParseMillis,
            String malwareScanCommand
    ) {
        return new DocumentInputProperties(
                "service-token",
                "default-secret",
                300,
                true,
                true,
                false,
                "wp4-document-requirement-parse",
                "INTERNAL",
                false,
                8000,
                16777216,
                10485760,
                ocrCommand,
                30,
                20000,
                2,
                true,
                262144,
                100,
                3,
                Map.of(),
                "",
                Map.of(),
                "",
                0,
                60,
                binaryMimeValidationEnabled,
                pdfMaxPages,
                pdfMaxParseMillis,
                "LOCAL_COMMAND",
                malwareScanCommand,
                15,
                2,
                2000,
                false,
                90,
                90
        );
    }
}
