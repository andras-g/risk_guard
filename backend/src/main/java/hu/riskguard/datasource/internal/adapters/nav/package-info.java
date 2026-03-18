/**
 * Future NAV Online Számla API integration.
 *
 * <p>See ADR-6 in architecture.md for the design decision to use the NAV Online Számla
 * XML API v3 as the primary data source for partner screening and EPR compliance.
 *
 * <p>Implementation is deferred until NAV technical user credentials are available via
 * accountant registration (estimated 1-2 months from Sprint 2 start).
 *
 * <p>This package contains stub interfaces and record definitions that define the NAV client
 * contract. When credentials are available, implement {@link NavOnlineSzamlaClient} backed
 * by JAXB-generated classes from the NAV XSD schemas.
 *
 * <p><strong>API endpoints (v3.0):</strong>
 * <ul>
 *   <li>Production: {@code https://api.onlineszamla.nav.gov.hu/invoiceService/v3/{operation}}</li>
 *   <li>Test: {@code https://api-test.onlineszamla.nav.gov.hu/invoiceService/v3/{operation}}</li>
 * </ul>
 *
 * <p><strong>Authentication:</strong> SHA-512 password hash + SHA3-512 request signature
 * using technical user credentials (login, password, signing key, tax number).
 *
 * @see <a href="https://github.com/nav-gov-hu/Online-Invoice">NAV Online Invoice API on GitHub</a>
 */
package hu.riskguard.datasource.internal.adapters.nav;
