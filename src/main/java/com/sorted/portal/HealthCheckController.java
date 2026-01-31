package com.sorted.portal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Simple health-check endpoint for the Portal Service.
 *
 * <p>Returns a minimal JSON payload so that load-balancers or uptime probes can verify the service is running
 * without requiring any authentication headers or tokens.</p>
 */
@RestController
public class HealthCheckController {

    /**
     * Health-check endpoint at root path "/".
     *
     * @return JSON object with status and current timestamp
     */
    @GetMapping("/")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
