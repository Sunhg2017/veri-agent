package com.songhg.veri.agent.documentinput.api.request;

import com.songhg.veri.agent.common.api.BasePageRequest;
import com.songhg.veri.agent.documentinput.application.query.DocumentParseFeedbackQuery;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class DocumentParseFeedbackPageRequest extends BasePageRequest {
    private UUID candidateId;
    private UUID importId;
    private String projectId;
    private String parseSource;
    private String curationStatus;

    public UUID getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(UUID candidateId) {
        this.candidateId = candidateId;
    }

    public UUID getImportId() {
        return importId;
    }

    public void setImportId(UUID importId) {
        this.importId = importId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getParseSource() {
        return parseSource;
    }

    public void setParseSource(String parseSource) {
        this.parseSource = parseSource;
    }

    public String getCurationStatus() {
        return curationStatus;
    }

    public void setCurationStatus(String curationStatus) {
        this.curationStatus = curationStatus;
    }

    public DocumentParseFeedbackQuery toQuery() {
        return new DocumentParseFeedbackQuery(
                candidateId,
                importId,
                trimToNull(projectId),
                trimToNull(parseSource),
                trimToNull(curationStatus),
                toPageQuery()
        );
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
