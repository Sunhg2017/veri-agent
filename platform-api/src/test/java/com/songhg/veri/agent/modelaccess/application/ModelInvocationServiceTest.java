package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.common.api.PageQuery;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.application.command.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.port.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.port.PlatformContextClient;
import com.songhg.veri.agent.modelaccess.application.port.PlatformInvocationPolicy;
import com.songhg.veri.agent.modelaccess.application.query.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.view.ModelInvocationResult;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import com.songhg.veri.agent.modelaccess.domain.ChatMessage;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import com.songhg.veri.agent.modelaccess.infrastructure.InMemoryModelAccessRepository;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;










class ModelInvocationServiceTest {

    private InMemoryModelAccessRepository repository;
    private RecordingPlatformContextClient platformContextClient;
    private ModelAccessMetrics metrics;
    private int providerCalls;
    private ModelInvocationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryModelAccessRepository();
        platformContextClient = new RecordingPlatformContextClient();
        metrics = mock(ModelAccessMetrics.class);
        providerCalls = 0;
        ModelAccessProperties properties = properties();
        service = new ModelInvocationService(
                repository,
                List.of(client()),
                platformContextClient,
                new SensitiveContentGuard(),
                new PromptRenderer(),
                properties,
                metrics,
                new ProviderResilienceManager(properties, new InMemoryProviderResilienceStateStore())
        );
    }

    @Test
    void invokesProviderAndPersistsAuditedInvocationRecord() {
        ModelInvocationResult response = service.invoke(
                request("project-invoke", "生成调用编排验证文本", false),
                new ServicePrincipal("wp4-document-input", "user-1")
        );

        assertThat(response.providerName()).isEqualTo("local-echo-primary");
        assertThat(response.modelName()).isEqualTo("test-local-model");
        assertThat(response.content()).isEqualTo("unit response: user: 生成调用编排验证文本");
        assertThat(response.inputTokens()).isEqualTo(8);
        assertThat(response.outputTokens()).isEqualTo(4);
        assertThat(providerCalls).isEqualTo(1);

        List<InvocationRecord> records = invocations("project-invoke");
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.id()).isEqualTo(response.invocationId());
            assertThat(record.status()).isEqualTo(InvocationStatus.SUCCEEDED);
            assertThat(record.sensitivityLevel()).isEqualTo("CONFIDENTIAL");
            assertThat(record.routingRuleName()).isEqualTo("default-priority");
            assertThat(record.modelCapability()).isEqualTo("CHAT");
            assertThat(record.actorService()).isEqualTo("wp4-document-input");
            assertThat(record.responsePreview()).isEqualTo("unit response: user: 生成调用编排验证文本");
        });
        assertThat(platformContextClient.auditRecords).hasSize(1);
        verify(metrics).recordInvocation(any(InvocationRecord.class), eq(ProviderType.LOCAL_ECHO));
    }

    @Test
    void blocksSensitiveContentBeforeProviderCallAndPersistsBlockedRecord() {
        assertThatThrownBy(() -> service.invoke(
                request("project-sensitive", "password=PlainSecret123 请直接使用", false),
                new ServicePrincipal("wp4-document-input", "user-1")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SENSITIVE_CONTENT_BLOCKED);

        assertThat(providerCalls).isZero();
        List<InvocationRecord> records = invocations("project-sensitive");
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.status()).isEqualTo(InvocationStatus.BLOCKED);
            assertThat(record.errorCode()).isEqualTo("SENSITIVE_CONTENT_BLOCKED");
            assertThat(record.requestPreview()).isEqualTo("user: password=*** 请直接使用");
            assertThat(record.providerId()).isNull();
        });
        assertThat(platformContextClient.auditRecords).hasSize(1);
        verify(metrics).recordInvocation(any(InvocationRecord.class), eq(null));
    }

    private ModelInvocationCommand request(String projectId, String content, boolean allowPublicModel) {
        return new ModelInvocationCommand(
                projectId,
                "app-1",
                "env-1",
                null,
                null,
                List.of(new ChatMessage("user", content)),
                null,
                null,
                allowPublicModel,
                "INTERNAL",
                null
        );
    }

    private List<InvocationRecord> invocations(String projectId) {
        return repository.invocations(new InvocationQuery(
                projectId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PageQuery.of(0, 10)
        ));
    }

    private ModelProviderClient client() {
        return new ModelProviderClient() {
            @Override
            public boolean supports(ModelProviderConfig provider) {
                return provider.providerType() == ProviderType.LOCAL_ECHO;
            }

            @Override
            public ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request) {
                providerCalls++;
                return new ProviderCallResult("unit response: " + request.messageText(), 8, 4);
            }
        };
    }

    private ModelAccessProperties properties() {
        return new ModelAccessProperties(
                "test-model-token",
                "test-local-model",
                4000,
                null,
                null,
                256,
                "UTC",
                10000,
                0,
                1,
                1000,
                1000,
                new BigDecimal("0.8"),
                0,
                1,
                0,
                1,
                0,
                null,
                "BLOCK",
                List.of()
        );
    }

    private static class RecordingPlatformContextClient implements PlatformContextClient {

        private final List<InvocationRecord> auditRecords = new ArrayList<>();

        @Override
        public PlatformInvocationPolicy verifyInvocationContext(ModelInvocationCommand request, ServicePrincipal principal) {
            return new PlatformInvocationPolicy("CONFIDENTIAL", false);
        }

        @Override
        public void writeInvocationAudit(InvocationRecord record) {
            auditRecords.add(record);
        }
    }
}
