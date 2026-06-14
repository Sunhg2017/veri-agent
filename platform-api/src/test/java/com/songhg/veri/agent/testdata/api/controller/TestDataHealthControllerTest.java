package com.songhg.veri.agent.testdata.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!",
        "veri-agent.test-data.record-max-count=5",
        "veri-agent.test-data.record-summary-max-bytes=128",
        "veri-agent.test-data.default-lease-ttl-seconds=60",
        "veri-agent.test-data.max-lease-ttl-seconds=120"
})
@AutoConfigureMockMvc
class TestDataHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesControlPlaneHealthWithoutSensitiveValues() throws Exception {
        mockMvc.perform(get("/api/v1/test-data/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.traceId", startsWith("trc_")))
                .andExpect(jsonPath("$.data.service").value("test-data"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.cleanupEnabled").value(false))
                .andExpect(jsonPath("$.data.exportEnabled").value(true))
                .andExpect(jsonPath("$.data.recordMaxCount").value(5))
                .andExpect(jsonPath("$.data.recordSummaryMaxBytes").value(128))
                .andExpect(jsonPath("$.data.defaultLeaseTtlSeconds").value(60))
                .andExpect(jsonPath("$.data.maxLeaseTtlSeconds").value(120))
                .andExpect(jsonPath("$.data.policy.foundationReady").value(true))
                .andExpect(jsonPath("$.data.policy.permissionSeedReady").value(true))
                .andExpect(jsonPath("$.data.policy.dataSetSchemaReady").value(true))
                .andExpect(jsonPath("$.data.policy.accountPoolSchemaReady").value(true))
                .andExpect(jsonPath("$.data.policy.leaseSchemaReady").value(true))
                .andExpect(jsonPath("$.data.policy.cleanupTaskSchemaReady").value(true))
                .andExpect(jsonPath("$.data.policy.auditEventDictionaryReady").value(true))
                .andExpect(jsonPath("$.data.policy.crossWpReferenceSchemaReady").value(true))
                .andExpect(jsonPath("$.data.policy.businessCrudApiReady").value(true))
                .andExpect(jsonPath("$.data.policy.leaseApiReady").value(true))
                .andExpect(jsonPath("$.data.policy.cleanupTaskApiReady").value(true))
                .andExpect(jsonPath("$.data.policy.cleanupWorkerReady").value(false))
                .andExpect(jsonPath("$.data.policy.destructiveCleanupDefaultDisabled").value(true))
                .andExpect(jsonPath("$.data.policy.secretPlaintextStored").value(false))
                .andExpect(jsonPath("$.data.policy.secretRefPlaintextExported").value(false))
                .andExpect(jsonPath("$.data.policy.rawRecordPayloadStored").value(false))
                .andExpect(jsonPath("$.data.policy.productionDataCopyAllowed").value(false))
                .andExpect(jsonPath("$.data.policy.automaticBusinessAccountProvisioningReady").value(false))
                .andExpect(jsonPath("$.data.policy.supportedDataSetStatuses[1]").value("READY"))
                .andExpect(jsonPath("$.data.policy.supportedAccountStatuses[0]").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.policy.supportedLeaseStatuses[0]").value("ACTIVE"))
                .andExpect(jsonPath("$.data.policy.supportedTaskTypes[2]").value("CLEANUP"));
    }
}
