package com.songhg.veri.agent.testdesign.application;

import com.songhg.veri.agent.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestDesignTaskReportServiceTest {

    @Test
    void allowsAggregateOnlyTaskReportRows() {
        assertDoesNotThrow(() -> TestDesignTaskReportExportGovernance.validateExportSafety("""
                recordType,section,metric,label,value
                metadata,exportGovernance,fieldPolicy,,aggregateOnly
                metadata,task,promptKey,,wp5-test-design-v1
                summary,candidateQuality,metric,publishable,2
                """));
    }

    @Test
    void blocksRawPromptMarkersAndUnredactedSecrets() {
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,task,rawPrompt,,secret"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,publish,error,,Bearer abcdefgh123"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,model,error,,sk_live_12345678"));
        assertThrows(BusinessException.class,
                () -> TestDesignTaskReportExportGovernance.validateExportSafety("metadata,model,error,,token=secret-value"));
    }
}
