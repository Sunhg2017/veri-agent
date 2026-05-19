package com.songhg.veri.agent.documentinput.application;

import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.documentinput.domain.DocumentSourceType;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
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
        String content = dataUrl("image/png", """
                OCR invoice requirement
                Priority: HIGH
                Acceptance Criteria:
                - invoice image is parsed
                """.getBytes(StandardCharsets.UTF_8));

        String text = extractor.extract(DocumentSourceType.OCR, content).text();

        assertThat(text).contains("OCR invoice requirement", "invoice image is parsed");
    }

    @Test
    void failsScannedPdfWhenOcrCommandIsMissing() {
        DocumentContentExtractor extractor = new DocumentContentExtractor(properties(""));
        String content = dataUrl("image/png", new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a});

        assertThatThrownBy(() -> extractor.extract(DocumentSourceType.PDF, content))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("WP4_OCR_COMMAND");
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
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
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
            document.save(output);
            return output.toByteArray();
        }
    }

    private static String dataUrl(String mimeType, byte[] bytes) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static DocumentInputProperties properties(String ocrCommand) {
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
                Map.of()
        );
    }
}
