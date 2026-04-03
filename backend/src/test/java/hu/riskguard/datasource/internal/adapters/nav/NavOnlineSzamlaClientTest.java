package hu.riskguard.datasource.internal.adapters.nav;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.datasource.internal.DataSourceException;
import hu.riskguard.datasource.internal.generated.nav.BasicHeaderType;
import hu.riskguard.datasource.internal.generated.nav.BasicResultType;
import hu.riskguard.datasource.internal.generated.nav.FunctionCodeType;
import hu.riskguard.datasource.internal.generated.nav.IncorporationType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceCategoryType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceDataResultType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceDigestResultType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceDigestType;
import hu.riskguard.datasource.internal.generated.nav.ManageInvoiceOperationType;
import hu.riskguard.datasource.internal.generated.nav.QueryInvoiceDataResponse;
import hu.riskguard.datasource.internal.generated.nav.QueryInvoiceDigestResponse;
import hu.riskguard.datasource.internal.generated.nav.QueryTaxpayerResponse;
import hu.riskguard.datasource.internal.generated.nav.TaxNumberType;
import hu.riskguard.datasource.internal.generated.nav.TaxpayerDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WireMock-based tests for {@link NavOnlineSzamlaClient}.
 * All 3 NAV query operations are covered: queryTaxpayer, queryInvoiceDigest (with pagination),
 * and queryInvoiceData.
 *
 * <p>All WireMock stub bodies are generated programmatically by marshaling Java objects via
 * {@link XmlMarshaller}. This guarantees namespace correctness without depending on static XML files.
 */
class NavOnlineSzamlaClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final NavCredentials TEST_CREDENTIALS = new NavCredentials(
            "testLogin", "PASSWORDHASH123456", "testSigningKey", "testExchangeKey0", "12345678"
    );

    private static XmlMarshaller xmlMarshaller;

    private NavOnlineSzamlaClient client;

    @BeforeAll
    static void initJaxb() throws Exception {
        xmlMarshaller = new XmlMarshaller();
        xmlMarshaller.init();
    }

    @BeforeEach
    void setUp() {
        AuthService authService = mock(AuthService.class);
        when(authService.loadCredentials(TENANT_ID)).thenReturn(TEST_CREDENTIALS);

        RiskGuardProperties.DataSource dataSourceProps = mock(RiskGuardProperties.DataSource.class);
        when(dataSourceProps.getNavApiBaseUrl()).thenReturn("http://localhost:" + wireMock.getPort());
        when(dataSourceProps.getConnectTimeoutMs()).thenReturn(5000);
        when(dataSourceProps.getReadTimeoutMs()).thenReturn(5000);

        RiskGuardProperties props = mock(RiskGuardProperties.class);
        when(props.getDataSource()).thenReturn(dataSourceProps);

        SignatureService signatureService = new SignatureService();
        client = new NavOnlineSzamlaClient(authService, signatureService, xmlMarshaller, props);
        TenantContext.setCurrentTenant(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // -------------------------------------------------------------------------
    // queryTaxpayer
    // -------------------------------------------------------------------------

    @Test
    void queryTaxpayer_success_returnsCompanyInfo() {
        wireMock.stubFor(post(urlPathEqualTo("/queryTaxpayer"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryTaxpayerSuccessResponse())));

        TaxpayerInfo result = client.queryTaxpayer("12345678");

        assertThat(result.companyName()).isEqualTo("Példa Kereskedelmi Kft.");
        assertThat(result.taxNumber()).isEqualTo("12345678");
        assertThat(result.incorporationType()).isEqualTo(TaxpayerInfo.IncorporationType.ORGANIZATION);
    }

    @Test
    void queryTaxpayer_requestContainsSoftwareId() {
        wireMock.stubFor(post(urlPathEqualTo("/queryTaxpayer"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryTaxpayerSuccessResponse())));

        client.queryTaxpayer("12345678");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/queryTaxpayer"))
                .withRequestBody(containing("HU-RISKGUARD-00001")));
    }

    @Test
    void queryTaxpayer_invalidLogin_throwsDataSourceException() {
        wireMock.stubFor(post(urlPathEqualTo("/queryTaxpayer"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryTaxpayerErrorResponse("INVALID_USER_RELATION", "Invalid login credentials"))));

        assertThatThrownBy(() -> client.queryTaxpayer("12345678"))
                .isInstanceOf(DataSourceException.class)
                .hasMessageContaining("INVALID_USER_RELATION");
    }

    @Test
    void queryTaxpayer_http500_throwsDataSourceException() {
        wireMock.stubFor(post(urlPathEqualTo("/queryTaxpayer"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.queryTaxpayer("12345678"))
                .isInstanceOf(DataSourceException.class);
    }

    // -------------------------------------------------------------------------
    // queryInvoiceDigest — pagination
    // -------------------------------------------------------------------------

    @Test
    void queryInvoiceDigest_paginatesAllPages_returnsFiveResults() {
        // Page 1: 3 invoices, availablePage=2
        wireMock.stubFor(post(urlPathEqualTo("/queryInvoiceDigest"))
                .inScenario("pagination")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryInvoiceDigestResponse(1, 2,
                                List.of("INV-2026-001", "INV-2026-002", "INV-2026-003"))))
                .willSetStateTo("page2"));

        // Page 2: 2 invoices, availablePage=2
        wireMock.stubFor(post(urlPathEqualTo("/queryInvoiceDigest"))
                .inScenario("pagination")
                .whenScenarioStateIs("page2")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryInvoiceDigestResponse(2, 2,
                                List.of("INV-2026-004", "INV-2026-005")))));

        List<InvoiceSummary> results = client.queryInvoiceDigest(
                "87654321",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                InvoiceDirection.OUTBOUND
        );

        assertThat(results).hasSize(5);
        assertThat(results.get(0).invoiceNumber()).isEqualTo("INV-2026-001");
        assertThat(results.get(4).invoiceNumber()).isEqualTo("INV-2026-005");
    }

    @Test
    void queryInvoiceDigest_requestContainsSoftwareId() {
        wireMock.stubFor(post(urlPathEqualTo("/queryInvoiceDigest"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryInvoiceDigestResponse(1, 1, List.of()))));

        client.queryInvoiceDigest("87654321", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), InvoiceDirection.OUTBOUND);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/queryInvoiceDigest"))
                .withRequestBody(containing("HU-RISKGUARD-00001")));
    }

    // -------------------------------------------------------------------------
    // queryInvoiceData
    // -------------------------------------------------------------------------

    @Test
    void queryInvoiceData_success_returnsInvoiceDetail() {
        wireMock.stubFor(post(urlPathEqualTo("/queryInvoiceData"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryInvoiceDataResponseXml("TEST-INV-2026-001"))));

        InvoiceDetail result = client.queryInvoiceData("TEST-INV-2026-001");

        assertThat(result).isNotNull();
        assertThat(result.invoiceNumber()).isEqualTo("TEST-INV-2026-001");
    }

    @Test
    void queryInvoiceData_requestContainsSoftwareId() {
        wireMock.stubFor(post(urlPathEqualTo("/queryInvoiceData"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/xml")
                        .withBody(buildQueryInvoiceDataResponseXml("TEST-INV-2026-001"))));

        client.queryInvoiceData("TEST-INV-2026-001");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/queryInvoiceData"))
                .withRequestBody(containing("HU-RISKGUARD-00001")));
    }

    // -------------------------------------------------------------------------
    // response builders — produce namespace-correct XML via XmlMarshaller
    // -------------------------------------------------------------------------

    private BasicHeaderType buildHeader(String requestId) {
        BasicHeaderType header = new BasicHeaderType();
        header.setRequestId(requestId);
        header.setTimestamp(NavXmlUtil.toXmlGregorianCalendar(Instant.now()));
        header.setRequestVersion("3.0");
        header.setHeaderVersion("1.0");
        return header;
    }

    private BasicResultType buildOkResult() {
        BasicResultType result = new BasicResultType();
        result.setFuncCode(FunctionCodeType.OK);
        return result;
    }

    private String buildQueryTaxpayerSuccessResponse() {
        QueryTaxpayerResponse response = new QueryTaxpayerResponse();
        response.setHeader(buildHeader("WIREMOCK001"));
        response.setResult(buildOkResult());
        response.setTaxpayerValidity(true);

        TaxNumberType taxNum = new TaxNumberType();
        taxNum.setTaxpayerId("12345678");
        taxNum.setVatCode("2");
        taxNum.setCountyCode("41");

        TaxpayerDataType data = new TaxpayerDataType();
        data.setTaxpayerName("Példa Kereskedelmi Kft.");
        data.setTaxNumberDetail(taxNum);
        data.setIncorporation(IncorporationType.ORGANIZATION);
        response.setTaxpayerData(data);

        return xmlMarshaller.marshal(response);
    }

    private String buildQueryTaxpayerErrorResponse(String errorCode, String message) {
        QueryTaxpayerResponse response = new QueryTaxpayerResponse();
        response.setHeader(buildHeader("WIREMOCK002"));

        BasicResultType result = new BasicResultType();
        result.setFuncCode(FunctionCodeType.ERROR);
        result.setErrorCode(errorCode);
        result.setMessage(message);
        response.setResult(result);

        return xmlMarshaller.marshal(response);
    }

    private String buildQueryInvoiceDigestResponse(int currentPage, int availablePage, List<String> invoiceNumbers) {
        QueryInvoiceDigestResponse response = new QueryInvoiceDigestResponse();
        response.setHeader(buildHeader("WIREMOCK00" + currentPage));
        response.setResult(buildOkResult());

        InvoiceDigestResultType digestResult = new InvoiceDigestResultType();
        digestResult.setCurrentPage(currentPage);
        digestResult.setAvailablePage(availablePage);

        for (String invoiceNumber : invoiceNumbers) {
            InvoiceDigestType digest = new InvoiceDigestType();
            digest.setInvoiceNumber(invoiceNumber);
            digest.setInvoiceOperation(ManageInvoiceOperationType.CREATE);
            digest.setInvoiceCategory(InvoiceCategoryType.NORMAL);
            digest.setInvoiceIssueDate(NavXmlUtil.toXmlGregorianCalendar(
                    LocalDate.of(2026, 3, 1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            digest.setSupplierTaxNumber("12345678");
            digest.setSupplierName("Teszt Szállító Kft.");
            digest.setCustomerTaxNumber("87654321");
            digest.setCustomerName("Teszt Vevő Kft.");
            digest.setCurrency("HUF");
            digest.setInvoiceNetAmount(BigDecimal.valueOf(100000));
            digestResult.getInvoiceDigest().add(digest);
        }

        response.setInvoiceDigestResult(digestResult);
        return xmlMarshaller.marshal(response);
    }

    /**
     * Builds a {@code QueryInvoiceDataResponse} where invoiceData contains a base64-encoded
     * minimal InvoiceData XML (no invoiceMain — mapInvoiceDetail handles null gracefully).
     */
    private String buildQueryInvoiceDataResponseXml(String invoiceNumber) {
        // Inner invoice XML: root element in 'data' ns, children in package ns (api)
        String innerXml = "<d:InvoiceData xmlns=\"http://schemas.nav.gov.hu/OSA/3.0/api\""
                + " xmlns:d=\"http://schemas.nav.gov.hu/OSA/3.0/data\">"
                + "<invoiceNumber>" + invoiceNumber + "</invoiceNumber>"
                + "</d:InvoiceData>";
        byte[] innerBytes = innerXml.getBytes(StandardCharsets.UTF_8);

        QueryInvoiceDataResponse response = new QueryInvoiceDataResponse();
        response.setHeader(buildHeader("WIREMOCKDATA01"));
        response.setResult(buildOkResult());

        InvoiceDataResultType dataResult = new InvoiceDataResultType();
        dataResult.setInvoiceData(innerBytes);
        dataResult.setCompressedContentIndicator(false);
        response.setInvoiceDataResult(dataResult);

        return xmlMarshaller.marshal(response);
    }
}
