package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Renders aggregate-only report snapshots into downloadable Word/PDF files.
 *
 * <p>The renderer only consumes already-sanitized export content assembled by WP10. It never loads raw runner
 * artifacts, source evidence bodies, credentials, prompt/response bodies, or external files beyond an optional local
 * system font used to keep Unicode PDF text readable.</p>
 */
@Component
public class ReportDocumentRenderer {

    private static final String TITLE = "WP10 Complete Report";
    private static final int PDF_FONT_SIZE = 10;
    private static final int PDF_TITLE_FONT_SIZE = 16;
    private static final float PDF_MARGIN = 48f;
    private static final float PDF_LEADING = 15f;
    private static final List<String> PDF_FONT_CANDIDATES = List.of(
            "/System/Library/Fonts/Supplemental/NISC18030.ttf",
            "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
            "/Library/Fonts/Arial Unicode.ttf",
            "/Library/Fonts/Arial Unicode MS.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    );

    public byte[] renderWord(Map<String, Object> exportContent) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addWordTitle(document, TITLE);
            addWordSection(document, "Summary", summaryLines(exportContent));
            addWordSection(document, "Report", reportLines(exportContent));
            addWordSection(document, "Evidence Manifests", evidenceLines(exportContent));
            addWordSection(document, "Latest Diagnosis", diagnosisLines(exportContent));
            addWordSection(document, "Defect Drafts", defectDraftLines(exportContent));
            addWordSection(document, "Redaction Policy", redactionPolicyLines(exportContent));
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to render Word report export", exception);
        }
    }

    public byte[] renderPdf(Map<String, Object> exportContent) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = loadPdfFont(document);
            renderPdfDocument(document, font, pdfLines(exportContent, font instanceof PDType1Font));
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to render PDF report export", exception);
        }
    }

    /**
     * Extracts a bounded text view from rendered PDF bytes so export redaction scanning can still inspect text before
     * the binary is persisted.
     */
    public String extractPdfText(byte[] pdfBytes) {
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
            String text = new PDFTextStripper().getText(document);
            return SensitiveTextSanitizer.boundedWithEllipsis(text, 200_000);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to extract PDF export text", exception);
        }
    }

    private void addWordTitle(XWPFDocument document, String title) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setText(title);
    }

    private void addWordSection(XWPFDocument document, String title, List<String> lines) {
        XWPFParagraph heading = document.createParagraph();
        heading.setSpacingBefore(180);
        XWPFRun headingRun = heading.createRun();
        headingRun.setBold(true);
        headingRun.setFontSize(12);
        headingRun.setText(title);

        if (lines.isEmpty()) {
            XWPFParagraph empty = document.createParagraph();
            XWPFRun run = empty.createRun();
            run.setText("- None");
            return;
        }
        for (String line : lines) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setFontSize(10);
            run.setText(line);
        }
    }

    private void renderPdfDocument(PDDocument document, PDFont font, List<String> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDRectangle box = page.getMediaBox();
        float width = box.getWidth() - (PDF_MARGIN * 2);
        float startY = box.getHeight() - PDF_MARGIN;
        float y = startY;

        PDPageContentStream content = new PDPageContentStream(document, page);
        try {
            content.beginText();
            content.setFont(font, PDF_TITLE_FONT_SIZE);
            content.newLineAtOffset(PDF_MARGIN, y);
            content.showText(TITLE);
            y -= (PDF_LEADING * 1.5f);
            content.setFont(font, PDF_FONT_SIZE);
            content.newLineAtOffset(0, -(PDF_LEADING * 1.5f));

            for (String line : lines) {
                List<String> wrapped = wrapText(line, font, PDF_FONT_SIZE, width);
                for (String part : wrapped) {
                    if (y <= PDF_MARGIN) {
                        content.endText();
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        box = page.getMediaBox();
                        y = box.getHeight() - PDF_MARGIN;
                        content = new PDPageContentStream(document, page);
                        content.beginText();
                        content.setFont(font, PDF_FONT_SIZE);
                        content.newLineAtOffset(PDF_MARGIN, y);
                    }
                    content.showText(part);
                    content.newLineAtOffset(0, -PDF_LEADING);
                    y -= PDF_LEADING;
                }
            }
            content.endText();
        } finally {
            content.close();
        }
    }

    private PDFont loadPdfFont(PDDocument document) throws IOException {
        for (String candidate : PDF_FONT_CANDIDATES) {
            File file = new File(candidate);
            if (!file.exists() || !file.isFile()) {
                continue;
            }
            try {
                String lowerCase = candidate.toLowerCase(Locale.ROOT);
                if (lowerCase.endsWith(".ttc")) {
                    try (TrueTypeCollection collection = new TrueTypeCollection(file)) {
                        List<org.apache.fontbox.ttf.TrueTypeFont> fonts = new ArrayList<>();
                        collection.processAllFonts(fonts::add);
                        if (!fonts.isEmpty()) {
                            return PDType0Font.load(document, fonts.get(0), true);
                        }
                    }
                } else {
                    return PDType0Font.load(document, file);
                }
            } catch (IOException ignored) {
                // Try the next candidate.
            }
        }
        return new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    }

    private List<String> pdfLines(Map<String, Object> exportContent, boolean asciiOnly) {
        List<String> lines = new ArrayList<>();
        lines.addAll(summaryLines(exportContent));
        lines.add("");
        lines.add("Report");
        lines.addAll(reportLines(exportContent));
        lines.add("");
        lines.add("Evidence Manifests");
        lines.addAll(evidenceLines(exportContent));
        lines.add("");
        lines.add("Latest Diagnosis");
        lines.addAll(diagnosisLines(exportContent));
        lines.add("");
        lines.add("Defect Drafts");
        lines.addAll(defectDraftLines(exportContent));
        lines.add("");
        lines.add("Redaction Policy");
        lines.addAll(redactionPolicyLines(exportContent));
        if (!asciiOnly) {
            return lines.stream().map(this::fontSafe).toList();
        }
        return lines.stream().map(line -> asciiSafeLine(fontSafe(line))).toList();
    }

    private List<String> summaryLines(Map<String, Object> exportContent) {
        Map<String, Object> report = mapValue(exportContent.get("report"));
        Map<String, Object> summary = mapValue(exportContent.get("summary"));
        return List.of(
                "Summary",
                line("Report ID", report.get("id")),
                line("Project", report.get("projectId")),
                line("Execution Run", report.get("executionRunId")),
                line("Status", report.get("status")),
                line("Run Status", summary.get("runStatus")),
                line("Generated At", exportContent.get("exportedAt")),
                line("Evidence Count", summary.get("evidenceManifestCount")),
                line("Diagnosis Status", mapValue(exportContent.get("latestDiagnosis")).get("status")),
                line("Defect Draft Count", count(exportContent.get("defectDrafts")))
        );
    }

    private List<String> reportLines(Map<String, Object> exportContent) {
        Map<String, Object> report = mapValue(exportContent.get("report"));
        List<String> lines = new ArrayList<>();
        lines.add(line("Schema Version", exportContent.get("schemaVersion")));
        lines.add(line("Field Set Version", exportContent.get("fieldSetVersion")));
        lines.add(line("Source Run Digest", report.get("sourceRunDigest")));
        lines.add(line("Generated By", report.get("generatedBy")));
        lines.add(line("Generated At", report.get("generatedAt")));
        lines.add(line("Trace ID", report.get("traceId")));
        Map<String, Object> summary = mapValue(exportContent.get("summary"));
        lines.add(line("Node Status Counts", compactMap(summary.get("nodeStatusCounts"))));
        lines.add(line("Failure Bucket Counts", compactMap(summary.get("failureBucketCounts"))));
        lines.add(line("Aggregate Only", mapValue(exportContent.get("redactionPolicy")).get("aggregateOnly")));
        return lines;
    }

    private List<String> evidenceLines(Map<String, Object> exportContent) {
        Object evidence = exportContent.get("evidenceManifests");
        if (!(evidence instanceof List<?> items) || items.isEmpty()) {
            return List.of("- None");
        }
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (Object item : items) {
            Map<String, Object> manifest = mapValue(item);
            lines.add(index++ + ". " + valueText(manifest.get("sourceWp")) + " / "
                    + valueText(manifest.get("sourceType")) + " / "
                    + valueText(manifest.get("sourceRefDigest")));
            lines.add("   " + line("Schema", manifest.get("schemaVersion")));
            lines.add("   " + line("Summary Keys", compactList(manifest.get("summaryKeys"))));
            lines.add("   " + line("Evidence Summary", compactMap(manifest.get("evidenceSummary"))));
        }
        return lines;
    }

    private List<String> diagnosisLines(Map<String, Object> exportContent) {
        Map<String, Object> diagnosis = mapValue(exportContent.get("latestDiagnosis"));
        if (diagnosis.isEmpty()) {
            return List.of("- None");
        }
        List<String> lines = new ArrayList<>();
        lines.add(line("Status", diagnosis.get("status")));
        lines.add(line("Classification", compactMap(diagnosis.get("classification"))));
        lines.add(line("Confidence", diagnosis.get("confidence")));
        lines.add(line("Manual Review Required", diagnosis.get("manualReviewRequired")));
        lines.add(line("Model Invocation Digest", diagnosis.get("modelInvocationDigest")));
        Object candidates = diagnosis.get("rootCauseCandidates");
        if (candidates instanceof List<?> items && !items.isEmpty()) {
            int index = 1;
            for (Object item : items) {
                Map<String, Object> candidate = mapValue(item);
                lines.add(index++ + ". " + valueText(candidate.get("category")) + " - "
                        + valueText(candidate.get("summary")));
                lines.add("   " + line("Evidence Refs", compactList(candidate.get("evidenceRefs"))));
                lines.add("   " + line("Next Actions", compactList(candidate.get("nextActions"))));
            }
        } else {
            lines.add("- No diagnosis candidates");
        }
        return lines;
    }

    private List<String> defectDraftLines(Map<String, Object> exportContent) {
        Object drafts = exportContent.get("defectDrafts");
        if (!(drafts instanceof List<?> items) || items.isEmpty()) {
            return List.of("- None");
        }
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (Object item : items) {
            Map<String, Object> draft = mapValue(item);
            lines.add(index++ + ". " + valueText(draft.get("title")));
            lines.add("   " + line("Status", draft.get("status")));
            lines.add("   " + line("Priority", draft.get("prioritySuggestion")));
            lines.add("   " + line("Reproduction Summary", draft.get("reproductionSummary")));
            lines.add("   " + line("Impact Summary", draft.get("impactSummary")));
            lines.add("   " + line("Evidence Refs", compactList(draft.get("evidenceRefs"))));
            lines.add("   " + line("Payload Preview", compactMap(draft.get("payloadPreview"))));
        }
        return lines;
    }

    private List<String> redactionPolicyLines(Map<String, Object> exportContent) {
        Map<String, Object> policy = mapValue(exportContent.get("redactionPolicy"));
        if (policy.isEmpty()) {
            return List.of("- None");
        }
        List<String> lines = new ArrayList<>();
        policy.forEach((key, value) -> lines.add(line(key, value)));
        return lines;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private List<String> wrapText(String text, PDFont font, int fontSize, float maxWidth) throws IOException {
        if (!StringUtils.hasText(text)) {
            return List.of(" ");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String token : splitTokens(text)) {
            String candidate = current.isEmpty() ? token : current + token;
            float width = font.getStringWidth(candidate) / 1000f * fontSize;
            if (width <= maxWidth || current.isEmpty()) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            lines.add(current.toString());
            current.setLength(0);
            current.append(token.stripLeading());
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private List<String> splitTokens(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            token.append(current);
            if (Character.isWhitespace(current) || isCjk(current) || current == '/' || current == ',' || current == ';') {
                tokens.add(token.toString());
                token.setLength(0);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    private boolean isCjk(char value) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(value);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }

    private String compactMap(Object value) {
        Map<String, Object> map = mapValue(value);
        if (map.isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        map.forEach((key, item) -> parts.add(key + "=" + valueText(item)));
        return String.join(", ", parts);
    }

    private String compactList(Object value) {
        if (value instanceof List<?> items && !items.isEmpty()) {
            return items.stream().map(this::valueText).toList().toString();
        }
        return "-";
    }

    private String line(String label, Object value) {
        return label + ": " + valueText(value);
    }

    private String valueText(Object value) {
        String text = SensitiveTextSanitizer.sanitizedEvidenceText(value == null ? "-" : String.valueOf(value), 1024);
        return StringUtils.hasText(text) ? text : "-";
    }

    private int count(Object value) {
        if (value instanceof List<?> items) {
            return items.size();
        }
        return 0;
    }

    private String fontSafe(String line) {
        return line == null ? "" : line;
    }

    /**
     * When PDF falls back to Standard14 fonts we keep output readable by replacing unsupported characters with '?'.
     */
    private String asciiSafeLine(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(line.length());
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            builder.append(current <= 0x00FF ? current : '?');
        }
        return builder.toString();
    }
}
