package hu.riskguard.epr.producer.domain;

import hu.riskguard.epr.producer.api.dto.ProducerProfileUpsertRequest;
import hu.riskguard.epr.producer.internal.ProducerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProducerProfileService}.
 */
@ExtendWith(MockitoExtension.class)
class ProducerProfileServiceTest {

    @Mock
    private ProducerProfileRepository repository;

    private ProducerProfileService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProducerProfileService(repository);
    }

    @Test
    void get_returnsValidProfile() {
        ProducerProfile profile = buildCompleteProfile();
        when(repository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(profile));

        ProducerProfile result = service.get(TENANT_ID);

        assertThat(result.legalName()).isEqualTo("Test GmbH");
    }

    @Test
    void get_throws412WhenProfileMissing() {
        when(repository.findByTenantId(TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TENANT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                });
    }

    @Test
    void get_throws412WhenLegalNameMissing() {
        ProducerProfile profile = buildProfileWithout("legalName");
        when(repository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.get(TENANT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    assertThat(rse.getReason()).contains("legalName");
                });
    }

    @Test
    void get_throws412WhenTaxNumberMissing() {
        ProducerProfile profile = buildProfileWithout("taxNumber");
        when(repository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.get(TENANT_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
                    assertThat(rse.getReason()).contains("taxNumber");
                });
    }

    @Test
    void getForDisplay_returnsProfileWithoutValidation() {
        // Profile missing required fields — getForDisplay should NOT throw 412
        ProducerProfile incomplete = buildProfileWithout("legalName");
        when(repository.findByTenantId(TENANT_ID)).thenReturn(Optional.of(incomplete));

        Optional<ProducerProfile> result = service.getForDisplay(TENANT_ID);

        assertThat(result).isPresent();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private ProducerProfile buildCompleteProfile() {
        return new ProducerProfile(
                UUID.randomUUID(), TENANT_ID,
                "Test GmbH",       // legalName
                "HU",              // addressCountryCode
                "Budapest",        // addressCity
                "1234",            // addressPostalCode
                "Fő utca",         // addressStreetName
                "utca",            // addressStreetType
                "1",               // addressHouseNumber
                null,              // kshStatisticalNumber
                null,              // companyRegistrationNumber
                "Test Contact",    // contactName
                "Manager",         // contactTitle
                "HU",              // contactCountryCode
                "1234",            // contactPostalCode
                "Budapest",        // contactCity
                "Fő utca",         // contactStreetName
                "+36 1 234 5678",  // contactPhone
                "test@test.hu",    // contactEmail
                null,              // okirClientId
                true,              // isManufacturer
                false,             // isIndividualPerformer
                false,             // isSubcontractor
                false,             // isConcessionaire
                "12345678-1-41"    // taxNumber
        );
    }

    private ProducerProfile buildProfileWithout(String field) {
        return new ProducerProfile(
                UUID.randomUUID(), TENANT_ID,
                "legalName".equals(field) ? null : "Test GmbH",
                "HU",
                "Budapest",
                "1234",
                "Fő utca",
                null, null, null, null,
                "Test Contact",
                "Manager",
                "HU",
                "1234",
                "Budapest",
                "Fő utca",
                "+36 1 234 5678",
                "test@test.hu",
                null,
                true, false, false, false,
                "taxNumber".equals(field) ? null : "12345678-1-41"
        );
    }
}
