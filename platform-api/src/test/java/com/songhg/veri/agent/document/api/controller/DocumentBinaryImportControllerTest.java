package com.songhg.veri.agent.document.api.controller;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.document-input.service-token=test-document-input-token",
        "veri-agent.asset.service-token=test-asset-token"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DocumentBinaryImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${veri-agent.document-input.import-max-content-bytes:16777216}")
    private long importMaxContentBytes;

    @Test
    void importsRealDocxAsRequirementCandidate() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "WORD",
                                  "sourceRef": "DOCX-REQ-1",
                                  "content": "%s"
                                }
                                """.formatted(dataUrl(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                docx("Word login requirement", "Priority: HIGH", "Acceptance Criteria:", "- login succeeds")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.totalParsed").value(0))
                .andReturn();

        awaitCandidate(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"),
                "Word login requirement", "HIGH");
    }

    @Test
    void returnsActionableMessageForOversizedMultipartUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large-upload.pdf",
                "application/pdf",
                new byte[(int) importMaxContentBytes + 1]
        );

        mockMvc.perform(multipart("/api/v1/document-input/imports/multipart")
                        .file(file)
                        .headers(documentInputHeaders())
                        .param("projectId", "project-wp4")
                        .param("sourceType", "PDF")
                        .param("sourceRef", "UPLOAD-LARGE-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("上传文件超过上限")))
                .andExpect(jsonPath("$.message", containsString("下一步")))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")));
    }

    @Test
    void importsRealPdfAsRequirementCandidate() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/document-input/imports")
                        .headers(documentInputHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "project-wp4",
                                  "sourceType": "PDF",
                                  "sourceRef": "PDF-REQ-1",
                                  "content": "%s"
                                }
                                """.formatted(dataUrl("application/pdf",
                                pdf("PDF refund requirement", "Priority: LOW", "Acceptance Criteria:", "refund succeeds")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.totalParsed").value(0))
                .andReturn();

        awaitCandidate(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"),
                "PDF refund requirement", "LOW");
    }

    @Test
    void importsRealDocxFromMultipartUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "upload-login.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("Multipart upload requirement", "Priority: HIGH", "Acceptance Criteria:", "- uploaded file is parsed")
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/document-input/imports/multipart")
                        .file(file)
                        .headers(documentInputHeaders())
                        .param("projectId", "project-wp4")
                        .param("sourceType", "WORD")
                        .param("sourceRef", "UPLOAD-DOCX-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("MODEL_PARSE_QUEUED"))
                .andExpect(jsonPath("$.data.title").value("upload-login.docx"))
                .andExpect(jsonPath("$.data.totalParsed").value(0))
                .andReturn();

        awaitCandidate(JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"),
                "Multipart upload requirement", "HIGH");
    }

    private void awaitCandidate(String importId, String title, String priority) throws Exception {
        AssertionError lastError = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                mockMvc.perform(get("/api/v1/document-input/imports/{id}/candidates", importId)
                                .headers(documentInputHeaders()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.total").value(1))
                        .andExpect(jsonPath("$.data.items[0].title").value(title))
                        .andExpect(jsonPath("$.data.items[0].priority").value(priority));
                return;
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(50);
            }
        }
        throw lastError == null ? new AssertionError("候选需求未生成") : lastError;
    }

    private HttpHeaders documentInputHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-document-input-token");
        headers.set("X-Caller-Service", "wp4-document-input");
        headers.set("X-Delegated-User-Id", "user-001");
        return headers;
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
}
