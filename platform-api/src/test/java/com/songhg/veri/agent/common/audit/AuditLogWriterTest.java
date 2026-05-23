package com.songhg.veri.agent.common.audit;

import com.songhg.veri.agent.common.audit.mapper.AuditMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditLogWriterTest {

    @Test
    void deniedShortcutKeepsTargetNameExplicitlyEmpty() {
        AuditLogWriter.AuditRecord record = AuditLogWriter.denied(
                null,
                "权限校验",
                "permission",
                "asset:manage",
                "缺少权限"
        );

        assertThat(record.resourceId()).isEqualTo("asset:manage");
        assertThat(record.targetName()).isNull();
        assertThat(record.reason()).isEqualTo("缺少权限");
    }

    @Test
    void postgresWriterRecordsSystemActorMetadataWhenActorIsMissing() {
        AuditMapper mapper = mock(AuditMapper.class);
        PostgresAuditLogWriter writer = new PostgresAuditLogWriter(mapper);
        ArgumentCaptor<String> afterJson = ArgumentCaptor.forClass(String.class);

        writer.record(AuditLogWriter.denied(
                null,
                "权限校验",
                "permission",
                "asset:manage",
                "缺少权限"
        ));

        verify(mapper).insertAuditLog(
                any(),
                eq("SYSTEM"),
                eq(null),
                eq("权限校验"),
                eq("permission"),
                eq("asset:manage"),
                eq("DENIED"),
                eq(null),
                afterJson.capture(),
                eq(null),
                eq("缺少权限")
        );
        assertThat(afterJson.getValue())
                .contains("\"resourceId\":\"asset:manage\"")
                .contains("\"actorType\":\"SYSTEM\"")
                .doesNotContain("\"name\":\"asset:manage\"");
    }
}
