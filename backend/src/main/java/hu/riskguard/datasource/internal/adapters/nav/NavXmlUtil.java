package hu.riskguard.datasource.internal.adapters.nav;

import hu.riskguard.datasource.internal.generated.nav.SoftwareOperationType;
import hu.riskguard.datasource.internal.generated.nav.SoftwareType;

import hu.riskguard.core.config.RiskGuardProperties;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Shared XML/NAV utilities (software block constants, XMLGregorianCalendar conversion).
 * Not a Spring component — used as a static helper by the NAV client classes.
 */
final class NavXmlUtil {

    // NAV spec §1.3.3 — softwareId must be exactly 18 chars, pattern [0-9A-Z\-]{18}
    static final String SOFTWARE_ID = "HU-RISKGUARD-00001";
    static final String SOFTWARE_NAME = "RiskGuard";
    static final String SOFTWARE_MAIN_VERSION = "1.0";
    static final String SOFTWARE_DEV_NAME = "RiskGuard Development";
    static final String SOFTWARE_DEV_CONTACT = "dev@riskguard.hu";
    static final String SOFTWARE_DEV_COUNTRY_CODE = "HU";

    private NavXmlUtil() {}

    /**
     * Builds the required software block for every NAV API request.
     */
    static SoftwareType buildSoftwareBlock() {
        SoftwareType software = new SoftwareType();
        software.setSoftwareId(SOFTWARE_ID);
        software.setSoftwareName(SOFTWARE_NAME);
        software.setSoftwareOperation(SoftwareOperationType.ONLINE_SERVICE);
        software.setSoftwareMainVersion(SOFTWARE_MAIN_VERSION);
        software.setSoftwareDevName(SOFTWARE_DEV_NAME);
        software.setSoftwareDevContact(SOFTWARE_DEV_CONTACT);
        software.setSoftwareDevCountryCode(SOFTWARE_DEV_COUNTRY_CODE);
        return software;
    }

    /**
     * Returns the NAV API base URL — uses the override from properties if set,
     * otherwise resolves to production or test URL based on the data source mode.
     */
    static String getBaseUrl(RiskGuardProperties properties) {
        String override = properties.getDataSource().getNavApiBaseUrl();
        if (override != null && !override.isEmpty()) return override;
        String mode = properties.getDataSource().getMode();
        return "live".equals(mode)
                ? "https://api.onlineszamla.nav.gov.hu/invoiceService/v3"
                : "https://api-test.onlineszamla.nav.gov.hu/invoiceService/v3";
    }

    /**
     * Converts an {@link Instant} to an {@link XMLGregorianCalendar} in UTC.
     */
    static XMLGregorianCalendar toXmlGregorianCalendar(Instant instant) {
        try {
            ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
            GregorianCalendar gcal = GregorianCalendar.from(zdt);
            gcal.setTimeZone(TimeZone.getTimeZone("UTC"));
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert Instant to XMLGregorianCalendar", e);
        }
    }
}
