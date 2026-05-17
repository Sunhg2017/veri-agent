package com.songhg.veri.agent.bootstrap.api;

import com.songhg.veri.agent.bootstrap.application.SuperAdminBootstrapService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bootstrap")
public class SuperAdminBootstrapController {

    private final SuperAdminBootstrapService bootstrapService;

    public SuperAdminBootstrapController(SuperAdminBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @PostMapping("/super-admin")
    public SuperAdminBootstrapResponse bootstrapSuperAdmin(
            @Valid @RequestBody SuperAdminBootstrapRequest request
    ) {
        return bootstrapService.bootstrap(request);
    }
}

