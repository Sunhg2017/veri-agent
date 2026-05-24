package com.songhg.veri.agent.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.songhg.veri.agent.asset.config.AssetProperties;
import com.songhg.veri.agent.auth.application.AuthProperties;
import com.songhg.veri.agent.common.secret.SecretProviderProperties;
import com.songhg.veri.agent.documentinput.config.DocumentInputProperties;
import com.songhg.veri.agent.modelaccess.config.ModelAccessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "veri-agent.auth.token-secret=test-auth-secret-32-byte-minimum!")
class ApplicationConfigurationLayeringTest {

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private SecretProviderProperties secretProviderProperties;

    @Autowired
    private ModelAccessProperties modelAccessProperties;

    @Autowired
    private AssetProperties assetProperties;

    @Autowired
    private DocumentInputProperties documentInputProperties;

    @Test
    void moduleConfigurationImportsBindDefaultProperties() {
        assertThat(authProperties.accessTokenTtlMinutes()).isEqualTo(30);
        assertThat(secretProviderProperties.localMasterKeyVersion()).isEqualTo("v1");

        assertThat(modelAccessProperties.serviceToken()).isEqualTo("local-model-access-token");
        assertThat(modelAccessProperties.defaultModel()).isEqualTo("local-echo");
        assertThat(modelAccessProperties.safeRoutingRules()).isEmpty();

        assertThat(assetProperties.serviceToken()).isEqualTo("local-asset-token");

        assertThat(documentInputProperties.serviceToken()).isEqualTo("local-document-input-token");
        assertThat(documentInputProperties.webhookSecrets()).isEmpty();
        assertThat(documentInputProperties.webhookMaxPayloadBytes()).isEqualTo(262144);
    }
}
