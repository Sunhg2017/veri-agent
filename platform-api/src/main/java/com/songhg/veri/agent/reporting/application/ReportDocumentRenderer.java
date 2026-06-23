package com.songhg.veri.agent.reporting.application;

import com.songhg.veri.agent.common.util.SensitiveTextSanitizer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Renders aggregate-only report snapshots into downloadable HTML, Excel, Word and PDF files.
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

    public byte[] renderExcel(Map<String, Object> exportContent) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = excelHeaderStyle(workbook);
            CellStyle valueStyle = excelValueStyle(workbook);
            CellStyle wrappedStyle = excelWrappedStyle(workbook);

            writeSummarySheet(workbook.createSheet("Summary"), exportContent, headerStyle, valueStyle);
            writeEvidenceSheet(workbook.createSheet("Evidence"), exportContent, headerStyle, wrappedStyle);
            writeDiagnosisSheet(workbook.createSheet("Diagnosis"), exportContent, headerStyle, wrappedStyle);
            writeDefectDraftSheet(workbook.createSheet("DefectDrafts"), exportContent, headerStyle, wrappedStyle);
            writePolicySheet(workbook.createSheet("RedactionPolicy"), exportContent, headerStyle, valueStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to render Excel report export", exception);
        }
    }

    public String renderHtml(Map<String, Object> exportContent) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>WP10 Complete Report</title>")
                .append("<style>")
                .append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;margin:24px;background:#f5f7fb;color:#172033;}")
                .append("h1,h2{margin:0 0 12px;}h2{margin-top:24px;font-size:18px;}")
                .append(".meta,.card-grid{display:grid;gap:12px;}")
                .append(".meta{grid-template-columns:repeat(auto-fit,minmax(220px,1fr));}")
                .append(".card-grid{grid-template-columns:repeat(auto-fit,minmax(280px,1fr));}")
                .append(".card{background:#fff;border:1px solid #d7deea;border-radius:8px;padding:14px;}")
                .append(".label{display:block;font-size:12px;color:#5e6a84;margin-bottom:4px;}")
                .append(".value{font-size:14px;font-weight:600;word-break:break-word;}")
                .append("table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #d7deea;border-radius:8px;overflow:hidden;}")
                .append("th,td{padding:10px 12px;border-bottom:1px solid #e5eaf3;text-align:left;vertical-align:top;font-size:13px;word-break:break-word;}")
                .append("th{background:#eef3fb;font-weight:700;color:#22304b;}tr:last-child td{border-bottom:0;}")
                .append("ul{margin:0;padding-left:20px;}code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:12px;}")
                .append("</style></head><body>");
        builder.append("<h1>WP10 Complete Report</h1>");
        appendHtmlMeta(builder, "Report ID", mapValue(exportContent.get("report")).get("id"));
        appendHtmlMeta(builder, "Project", mapValue(exportContent.get("report")).get("projectId"));
        appendHtmlMeta(builder, "Execution Run", mapValue(exportContent.get("report")).get("executionRunId"));
        appendHtmlMeta(builder, "Status", mapValue(exportContent.get("report")).get("status"));
        appendHtmlMeta(builder, "Run Status", mapValue(exportContent.get("summary")).get("runStatus"));
        appendHtmlMeta(builder, "Generated At", exportContent.get("exportedAt"));
        appendHtmlMeta(builder, "Evidence Count", mapValue(exportContent.get("summary")).get("evidenceManifestCount"));
        appendHtmlMeta(builder, "Diagnosis Status", mapValue(exportContent.get("latestDiagnosis")).get("status"));
        builder.append("</div>");
        appendHtmlKeyValueSection(builder, "Report", reportLines(exportContent));
        appendHtmlTableSection(builder, "Evidence Manifests", List.of("Source WP", "Source Type", "Source Ref Digest", "Summary Keys", "Evidence Summary"),
                evidenceRows(exportContent));
        appendHtmlTableSection(builder, "Latest Diagnosis", List.of("Field", "Value"), twoColumnRows(diagnosisLines(exportContent)));
        appendHtmlTableSection(builder, "Defect Drafts", List.of("Field", "Value"), twoColumnRows(defectDraftLines(exportContent)));
        appendHtmlTableSection(builder, "Redaction Policy", List.of("Field", "Value"), twoColumnRows(redactionPolicyLines(exportContent)));
        builder.append("</body></html>");
        return builder.toString();
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

    private void writeSummarySheet(
            Sheet sheet,
            Map<String, Object> exportContent,
            CellStyle headerStyle,
            CellStyle valueStyle
    ) {
        int rowIndex = 0;
        for (String line : summaryLines(exportContent)) {
            rowIndex = writeKeyValueRow(sheet, rowIndex, splitLine(line), headerStyle, valueStyle);
        }
        rowIndex++;
        for (String line : reportLines(exportContent)) {
            writeKeyValueRow(sheet, rowIndex++, splitLine(line), headerStyle, valueStyle);
        }
        autoSize(sheet, 2);
    }

    private void writeEvidenceSheet(
            Sheet sheet,
            Map<String, Object> exportContent,
            CellStyle headerStyle,
            CellStyle wrappedStyle
    ) {
        writeHeaderRow(sheet, 0, headerStyle, "Source WP", "Source Type", "Source Ref Digest", "Summary Keys", "Evidence Summary");
        int rowIndex = 1;
        for (List<String> row : evidenceRows(exportContent)) {
            writeRow(sheet, rowIndex++, wrappedStyle, row.toArray(String[]::new));
        }
        autoSize(sheet, 5);
    }

    private void writeDiagnosisSheet(
            Sheet sheet,
            Map<String, Object> exportContent,
            CellStyle headerStyle,
            CellStyle wrappedStyle
    ) {
        writeHeaderRow(sheet, 0, headerStyle, "Field", "Value");
        int rowIndex = 1;
        for (List<String> row : twoColumnRows(diagnosisLines(exportContent))) {
            writeRow(sheet, rowIndex++, wrappedStyle, row.toArray(String[]::new));
        }
        autoSize(sheet, 2);
    }

    private void writeDefectDraftSheet(
            Sheet sheet,
            Map<String, Object> exportContent,
            CellStyle headerStyle,
            CellStyle wrappedStyle
    ) {
        writeHeaderRow(sheet, 0, headerStyle, "Field", "Value");
        int rowIndex = 1;
        for (List<String> row : twoColumnRows(defectDraftLines(exportContent))) {
            writeRow(sheet, rowIndex++, wrappedStyle, row.toArray(String[]::new));
        }
        autoSize(sheet, 2);
    }

    private void writePolicySheet(
            Sheet sheet,
            Map<String, Object> exportContent,
            CellStyle headerStyle,
            CellStyle valueStyle
    ) {
        writeHeaderRow(sheet, 0, headerStyle, "Field", "Value");
        int rowIndex = 1;
        for (List<String> row : twoColumnRows(redactionPolicyLines(exportContent))) {
            writeRow(sheet, rowIndex++, valueStyle, row.toArray(String[]::new));
        }
        autoSize(sheet, 2);
    }

    private int writeKeyValueRow(
            Sheet sheet,
            int rowIndex,
            String[] values,
            CellStyle headerStyle,
            CellStyle valueStyle
    ) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex);
        Cell left = row.createCell(0);
        left.setCellStyle(headerStyle);
        left.setCellValue(values[0]);
        Cell right = row.createCell(1);
        right.setCellStyle(valueStyle);
        right.setCellValue(values[1]);
        return rowIndex + 1;
    }

    private void writeHeaderRow(Sheet sheet, int rowIndex, CellStyle style, String... labels) {
        writeRow(sheet, rowIndex, style, labels);
    }

    private void writeRow(Sheet sheet, int rowIndex, CellStyle style, String... values) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            Cell cell = row.createCell(index);
            cell.setCellStyle(style);
            cell.setCellValue(values[index]);
        }
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int index = 0; index < columns; index++) {
            sheet.autoSizeColumn(index);
            int currentWidth = sheet.getColumnWidth(index);
            sheet.setColumnWidth(index, Math.min(currentWidth + 512, 40 * 256));
        }
        sheet.createFreezePane(0, 1);
    }

    private CellStyle excelHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle excelValueStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private CellStyle excelWrappedStyle(XSSFWorkbook workbook) {
        CellStyle style = excelValueStyle(workbook);
        style.setWrapText(true);
        return style;
    }

    private List<List<String>> evidenceRows(Map<String, Object> exportContent) {
        Object evidence = exportContent.get("evidenceManifests");
        if (!(evidence instanceof List<?> items) || items.isEmpty()) {
            return List.of(List.of("-", "-", "-", "-", "-"));
        }
        List<List<String>> rows = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> manifest = mapValue(item);
            rows.add(List.of(
                    valueText(manifest.get("sourceWp")),
                    valueText(manifest.get("sourceType")),
                    valueText(manifest.get("sourceRefDigest")),
                    compactList(manifest.get("summaryKeys")),
                    compactMap(manifest.get("evidenceSummary"))
            ));
        }
        return rows;
    }

    private List<List<String>> twoColumnRows(List<String> lines) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : lines) {
            rows.add(List.of(splitLine(line)));
        }
        return rows;
    }

    private String[] splitLine(String line) {
        int separator = line.indexOf(':');
        if (separator < 0) {
            return new String[]{"Value", valueText(line)};
        }
        String left = line.substring(0, separator).trim();
        String right = line.substring(separator + 1).trim();
        return new String[]{StringUtils.hasText(left) ? left : "Field", StringUtils.hasText(right) ? right : "-"};
    }

    private void appendHtmlMeta(StringBuilder builder, String label, Object value) {
        if (builder.indexOf("<div class=\"meta\">") < 0) {
            builder.append("<div class=\"meta\">");
        }
        builder.append("<div class=\"card\"><span class=\"label\">")
                .append(html(label))
                .append("</span><span class=\"value\">")
                .append(html(valueText(value)))
                .append("</span></div>");
    }

    private void appendHtmlKeyValueSection(StringBuilder builder, String title, List<String> lines) {
        builder.append("<h2>").append(html(title)).append("</h2><div class=\"card-grid\">");
        for (String line : lines) {
            String[] pair = splitLine(line);
            builder.append("<div class=\"card\"><span class=\"label\">")
                    .append(html(pair[0]))
                    .append("</span><span class=\"value\">")
                    .append(html(pair[1]))
                    .append("</span></div>");
        }
        builder.append("</div>");
    }

    private void appendHtmlTableSection(
            StringBuilder builder,
            String title,
            List<String> headers,
            List<List<String>> rows
    ) {
        builder.append("<h2>").append(html(title)).append("</h2><table><thead><tr>");
        for (String header : headers) {
            builder.append("<th>").append(html(header)).append("</th>");
        }
        builder.append("</tr></thead><tbody>");
        for (List<String> row : rows) {
            builder.append("<tr>");
            for (String value : row) {
                builder.append("<td>").append(html(value)).append("</td>");
            }
            builder.append("</tr>");
        }
        builder.append("</tbody></table>");
    }

    private String html(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
