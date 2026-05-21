package com.songhg.veri.agent.management.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "veri-agent.management")
public class ManagementProperties {

    private boolean environmentConnectivityCheckEnabled = true;
    private int environmentConnectivityTimeoutMs = 3000;

    public boolean isEnvironmentConnectivityCheckEnabled() {
        return environmentConnectivityCheckEnabled;
    }

    public void setEnvironmentConnectivityCheckEnabled(boolean environmentConnectivityCheckEnabled) {
        this.environmentConnectivityCheckEnabled = environmentConnectivityCheckEnabled;
    }

    public int getEnvironmentConnectivityTimeoutMs() {
        return Math.max(200, Math.min(environmentConnectivityTimeoutMs, 30000));
    }

    public void setEnvironmentConnectivityTimeoutMs(int environmentConnectivityTimeoutMs) {
        this.environmentConnectivityTimeoutMs = environmentConnectivityTimeoutMs;
    }
}
