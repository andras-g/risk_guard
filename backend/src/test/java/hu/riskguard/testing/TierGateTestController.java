package hu.riskguard.testing;

import hu.riskguard.core.security.Tier;
import hu.riskguard.core.security.TierRequired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only controller for verifying tier gate interceptor behavior.
 * Each endpoint requires a different tier — used exclusively in integration tests.
 * Placed in the testing package to satisfy ArchUnit's path naming exclusion rule.
 */
@RestController
@RequestMapping("/api/v1/test-tier-gate")
public class TierGateTestController {

    @GetMapping("/alap")
    @TierRequired(Tier.ALAP)
    public ResponseEntity<String> alapEndpoint() {
        return ResponseEntity.ok("ALAP access granted");
    }

    @GetMapping("/pro")
    @TierRequired(Tier.PRO)
    public ResponseEntity<String> proEndpoint() {
        return ResponseEntity.ok("PRO access granted");
    }

    @GetMapping("/pro-epr")
    @TierRequired(Tier.PRO_EPR)
    public ResponseEntity<String> proEprEndpoint() {
        return ResponseEntity.ok("PRO_EPR access granted");
    }

    @GetMapping("/open")
    public ResponseEntity<String> openEndpoint() {
        return ResponseEntity.ok("No tier required");
    }
}
