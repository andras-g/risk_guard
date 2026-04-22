package hu.riskguard.epr.registry;

import hu.riskguard.epr.registry.api.DemoResetController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 10.11 AC #15b — production-profile witness for {@link DemoResetController}.
 *
 * <p>The controller MUST stay gated by {@code @Profile({"demo","e2e"})} so the
 * {@code POST /api/v1/registry/demo/reset-packaging} endpoint does not exist (404, not 403)
 * under the production profile. Removing the annotation would silently expose a tenant-wide
 * packaging-wipe button to production traffic.
 *
 * <p>This test asserts the annotation is present and lists exactly the two demo-class profiles.
 * It runs without bootstrapping a Spring context — fast feedback, no Testcontainers required.
 */
class DemoResetControllerProfileGateTest {

    @Test
    void demoResetController_isAnnotatedWithDemoAndE2eProfiles() {
        Profile annotation = DemoResetController.class.getAnnotation(Profile.class);

        assertThat(annotation)
                .as("DemoResetController must remain @Profile-gated — production exposure is a "
                        + "tenant-wide data-wipe regression. See Story 10.11 AC #15b.")
                .isNotNull();

        Set<String> activeIn = Arrays.stream(annotation.value()).collect(Collectors.toSet());
        assertThat(activeIn)
                .as("DemoResetController must be active only under the demo/e2e profiles. "
                        + "Adding more profiles widens the production exposure surface.")
                .containsExactlyInAnyOrder("demo", "e2e");
    }
}
