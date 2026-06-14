package com.songhg.veri.agent.apiautomation.application;

import com.songhg.veri.agent.apiautomation.application.port.ApiAutomationRepository;
import com.songhg.veri.agent.apiautomation.domain.ApiAutomationScriptBundle;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApiAutomationBundleScopeService {

    private final ApiAutomationRepository repository;

    public ApiAutomationBundleScopeService(ApiAutomationRepository repository) {
        this.repository = repository;
    }

    public Optional<ApiAutomationBundleScope> bundleScope(UUID bundleId) {
        return repository.scriptBundle(bundleId)
                .map(this::toScope);
    }

    private ApiAutomationBundleScope toScope(ApiAutomationScriptBundle bundle) {
        return new ApiAutomationBundleScope(bundle.id(), bundle.projectId(), bundle.status());
    }
}
