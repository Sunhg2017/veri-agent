package com.songhg.veri.agent.common.web;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class PlatformHealthController {

    @GetMapping
    public Map<String, Object> health() {
        return Map.of(
                "service", "platform-api",
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}

