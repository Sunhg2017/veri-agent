package com.songhg.veri.agent.apiautomation.application;

import java.util.UUID;

public record ApiAutomationBundleScope(
        UUID bundleId,
        String projectId,
        String status
) {
}
