package hu.riskguard.datasource.internal.adapters.nav;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.core.security.TenantContext;
import hu.riskguard.datasource.internal.DataSourceException;
import hu.riskguard.datasource.internal.generated.nav.BasicHeaderType;
import hu.riskguard.datasource.internal.generated.nav.CryptoType;
import hu.riskguard.datasource.internal.generated.nav.FunctionCodeType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceDirectionType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceDigestType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceNumberQueryType;
import hu.riskguard.datasource.internal.generated.nav.InvoiceQueryParamsType;
import hu.riskguard.datasource.internal.generated.nav.MandatoryQueryParamsType;
import hu.riskguard.datasource.internal.generated.nav.DateIntervalParamType;
import hu.riskguard.datasource.internal.generated.nav.QueryInvoiceDataRequest;
import hu.riskguard.datasource.internal.generated.nav.QueryInvoiceDataResponse;
import hu.riskguard.datasource.internal.generated.nav.QueryInvoiceDigestRequest;
import hu.riskguard.datasource.internal.generated.nav.QueryInvoiceDigestResponse;
import hu.riskguard.datasource.internal.generated.nav.QueryTaxpayerRequest;
import hu.riskguard.datasource.internal.generated.nav.QueryTaxpayerRequestType;
import hu.riskguard.datasource.internal.generated.nav.QueryTaxpayerResponse;
import hu.riskguard.datasource.internal.generated.nav.UserHeaderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Spring {@code @Component} implementation of NAV Online Számla API v3 client.
 *
 * <p>Replaces the stub interface (Story 2.2.2). Implements all 3 query operations:
 * {@code queryTaxpayer}, {@code queryInvoiceDigest}, and {@code queryInvoiceData}.
 *
 * <p>Authentication: every request is signed with SHA3-512 via {@link SignatureService}.
 * Credentials are loaded per-tenant via {@link AuthService}.
 *
 * <p>Pagination: {@code queryInvoiceDigest} automatically fetches all pages and aggregates.
 */
@Component
public class NavOnlineSzamlaClient {

    private static final Logger log = LoggerFactory.getLogger(NavOnlineSzamlaClient.class);
    static final String ADAPTER_NAME = "nav-online-szamla";

    private final AuthService authService;
    private final SignatureService signatureService;
    private final XmlMarshaller xmlMarshaller;
    private final RiskGuardProperties riskGuardProperties;

    public NavOnlineSzamlaClient(
            AuthService authService,
            SignatureService signatureService,
            XmlMarshaller xmlMarshaller,
            RiskGuardProperties riskGuardProperties
    ) {
        this.authService = authService;
        this.signatureService = signatureService;
        this.xmlMarshaller = xmlMarshaller;
        this.riskGuardProperties = riskGuardProperties;
    }

    /**
     * Queries taxpayer information for the given 8-digit tax number.
     * This operation is NOT representation-bound — any technical user can query any tax number.
     *
     * @param taxNumber 8-digit Hungarian tax number
     * @return taxpayer identification and registration data
     * @throws DataSourceException on NAV error or HTTP failure
     */
    public TaxpayerInfo queryTaxpayer(String taxNumber) {
        UUID tenantId = TenantContext.getCurrentTenant();
        NavCredentials credentials = authService.loadCredentials(tenantId);

        String requestId = AuthService.generateRequestId();
        Instant timestamp = Instant.now();
        String signature = signatureService.computeRequestSignature(requestId, timestamp, credentials.signingKey());

        QueryTaxpayerRequest request = buildQueryTaxpayerRequest(requestId, timestamp, signature, credentials, taxNumber);
        QueryTaxpayerResponse response = post("/queryTaxpayer", request, QueryTaxpayerResponse.class);

        if (response.getResult() == null || response.getResult().getFuncCode() == FunctionCodeType.ERROR) {
            String errorCode = response.getResult() != null ? response.getResult().getErrorCode() : "UNKNOWN";
            String message = response.getResult() != null ? response.getResult().getMessage() : "No result";
            throw new DataSourceException(ADAPTER_NAME, "NAV error [" + errorCode + "]: " + message);
        }

        if (response.getTaxpayerData() == null) {
            throw new DataSourceException(ADAPTER_NAME, "NAV queryTaxpayer returned no taxpayer data");
        }

        var data = response.getTaxpayerData();
        String taxId = data.getTaxNumberDetail() != null ? data.getTaxNumberDetail().getTaxpayerId() : taxNumber.substring(0, Math.min(taxNumber.length(), 8));
        String vatCode = data.getTaxNumberDetail() != null ? String.valueOf(data.getTaxNumberDetail().getVatCode()) : null;
        String address = buildAddress(data.getTaxpayerAddressList());

        TaxpayerInfo.IncorporationType incType = mapIncorporationType(data.getIncorporation());

        return new TaxpayerInfo(
                data.getTaxpayerName(),
                data.getTaxpayerShortName(),
                taxId,
                vatCode,
                address,
                incType,
                data.getVatGroupMembership() != null && !"NONE".equals(data.getVatGroupMembership())
        );
    }

    /**
     * Queries invoice digests (summaries) for a company, paginating all available pages.
     *
     * @param taxNumber 8-digit tax number of the counterparty to filter by
     * @param from      start of issue date range (inclusive)
     * @param to        end of issue date range (inclusive)
     * @param direction OUTBOUND (sales) or INBOUND (purchases)
     * @return aggregated list of invoice summaries across all pages
     */
    public List<InvoiceSummary> queryInvoiceDigest(String taxNumber, LocalDate from, LocalDate to, InvoiceDirection direction) {
        UUID tenantId = TenantContext.getCurrentTenant();
        NavCredentials credentials = authService.loadCredentials(tenantId);

        List<InvoiceSummary> allSummaries = new ArrayList<>();
        int page = 1;
        int availablePages;

        do {
            String requestId = AuthService.generateRequestId();
            Instant timestamp = Instant.now();
            String signature = signatureService.computeRequestSignature(requestId, timestamp, credentials.signingKey());

            QueryInvoiceDigestRequest request = buildQueryInvoiceDigestRequest(
                    requestId, timestamp, signature, credentials, taxNumber, from, to, direction, page);

            QueryInvoiceDigestResponse response = post("/queryInvoiceDigest", request, QueryInvoiceDigestResponse.class);

            if (response.getResult() == null || response.getResult().getFuncCode() == FunctionCodeType.ERROR) {
                String errorCode = response.getResult() != null ? response.getResult().getErrorCode() : "UNKNOWN";
                throw new DataSourceException(ADAPTER_NAME, "NAV queryInvoiceDigest error [" + errorCode + "]");
            }

            var digestResult = response.getInvoiceDigestResult();
            if (digestResult != null && digestResult.getInvoiceDigest() != null) {
                for (InvoiceDigestType digest : digestResult.getInvoiceDigest()) {
                    allSummaries.add(mapInvoiceSummary(digest, direction));
                }
                availablePages = digestResult.getAvailablePage();
            } else {
                availablePages = 0;
            }
            page++;
        } while (page <= availablePages);

        return allSummaries;
    }

    /**
     * Queries full invoice data for the given invoice number (OUTBOUND direction for EPR use case).
     *
     * @param invoiceNumber the NAV invoice identifier
     * @return full invoice with line items
     */
    public InvoiceDetail queryInvoiceData(String invoiceNumber) {
        UUID tenantId = TenantContext.getCurrentTenant();
        NavCredentials credentials = authService.loadCredentials(tenantId);

        String requestId = AuthService.generateRequestId();
        Instant timestamp = Instant.now();
        String signature = signatureService.computeRequestSignature(requestId, timestamp, credentials.signingKey());

        QueryInvoiceDataRequest request = buildQueryInvoiceDataRequest(
                requestId, timestamp, signature, credentials, invoiceNumber);

        QueryInvoiceDataResponse response = post("/queryInvoiceData", request, QueryInvoiceDataResponse.class);

        if (response.getResult() == null || response.getResult().getFuncCode() == FunctionCodeType.ERROR) {
            String errorCode = response.getResult() != null ? response.getResult().getErrorCode() : "UNKNOWN";
            throw new DataSourceException(ADAPTER_NAME, "NAV queryInvoiceData error [" + errorCode + "]");
        }

        var dataResult = response.getInvoiceDataResult();
        if (dataResult == null || dataResult.getInvoiceData() == null) {
            throw new DataSourceException(ADAPTER_NAME, "NAV queryInvoiceData returned no invoice data");
        }

        // The invoiceData byte[] is the Base64-decoded invoice XML (possibly GZIP compressed)
        byte[] rawInvoiceBytes = dataResult.getInvoiceData();
        String invoiceXml;
        if (dataResult.isCompressedContentIndicator()) {
            invoiceXml = decompressGzip(rawInvoiceBytes);
        } else {
            invoiceXml = new String(rawInvoiceBytes, StandardCharsets.UTF_8);
        }

        hu.riskguard.datasource.internal.generated.nav.InvoiceData invoiceData =
                xmlMarshaller.unmarshal(invoiceXml,
                        hu.riskguard.datasource.internal.generated.nav.InvoiceData.class);

        return mapInvoiceDetail(invoiceNumber, invoiceData);
    }

    // --- HTTP helper ---

    private <T> T post(String endpoint, Object requestObj, Class<T> responseType) {
        try {
            String baseUrl = NavXmlUtil.getBaseUrl(riskGuardProperties);
            String xml = xmlMarshaller.marshal(requestObj);
            byte[] body = xmlMarshaller.toRequestBytes(xml);
            boolean gzipped = xmlMarshaller.isGzipCompressed(body);

            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/xml")
                    .timeout(Duration.ofMillis(riskGuardProperties.getDataSource().getReadTimeoutMs()));
            if (gzipped) {
                builder.header("Content-Encoding", "gzip");
            }
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(body));

            var httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(riskGuardProperties.getDataSource().getConnectTimeoutMs()))
                    .build();

            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new DataSourceException(ADAPTER_NAME,
                        "NAV API returned HTTP " + response.statusCode() + " for " + endpoint);
            }

            return xmlMarshaller.unmarshal(response.body(), responseType);

        } catch (DataSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new DataSourceException(ADAPTER_NAME, "HTTP call to " + endpoint + " failed: " + e.getMessage(), e);
        }
    }

    // --- Request builders ---

    private QueryTaxpayerRequest buildQueryTaxpayerRequest(
            String requestId, Instant timestamp, String signature,
            NavCredentials credentials, String taxNumber
    ) {
        QueryTaxpayerRequest request = new QueryTaxpayerRequest();
        request.setHeader(buildHeader(requestId, timestamp));
        request.setUser(buildUser(signature, credentials));
        request.setSoftware(NavXmlUtil.buildSoftwareBlock());
        request.setTaxNumber(taxNumber.substring(0, Math.min(taxNumber.length(), 8)));
        return request;
    }

    private QueryInvoiceDigestRequest buildQueryInvoiceDigestRequest(
            String requestId, Instant timestamp, String signature,
            NavCredentials credentials, String taxNumber,
            LocalDate from, LocalDate to, InvoiceDirection direction, int page
    ) {
        DateIntervalParamType dateInterval = new DateIntervalParamType();
        dateInterval.setDateFrom(NavXmlUtil.toXmlGregorianCalendar(from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)));
        dateInterval.setDateTo(NavXmlUtil.toXmlGregorianCalendar(to.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC)));

        MandatoryQueryParamsType mandatory = new MandatoryQueryParamsType();
        mandatory.setInvoiceIssueDate(dateInterval);

        var additional = new hu.riskguard.datasource.internal.generated.nav.AdditionalQueryParamsType();
        additional.setTaxNumber(taxNumber.substring(0, Math.min(taxNumber.length(), 8)));

        InvoiceQueryParamsType queryParams = new InvoiceQueryParamsType();
        queryParams.setMandatoryQueryParams(mandatory);
        queryParams.setAdditionalQueryParams(additional);

        // QueryInvoiceDigestRequest extends QueryInvoiceDigestRequestType which has page/direction/queryParams
        QueryInvoiceDigestRequest request = new QueryInvoiceDigestRequest();
        request.setHeader(buildHeader(requestId, timestamp));
        request.setUser(buildUser(signature, credentials));
        request.setSoftware(NavXmlUtil.buildSoftwareBlock());
        request.setPage(page);
        request.setInvoiceDirection(direction == InvoiceDirection.OUTBOUND
                ? InvoiceDirectionType.OUTBOUND : InvoiceDirectionType.INBOUND);
        request.setInvoiceQueryParams(queryParams);
        return request;
    }

    private QueryInvoiceDataRequest buildQueryInvoiceDataRequest(
            String requestId, Instant timestamp, String signature,
            NavCredentials credentials, String invoiceNumber
    ) {
        InvoiceNumberQueryType invoiceNumberQuery = new InvoiceNumberQueryType();
        invoiceNumberQuery.setInvoiceNumber(invoiceNumber);
        invoiceNumberQuery.setInvoiceDirection(InvoiceDirectionType.OUTBOUND);

        QueryInvoiceDataRequest request = new QueryInvoiceDataRequest();
        request.setHeader(buildHeader(requestId, timestamp));
        request.setUser(buildUser(signature, credentials));
        request.setSoftware(NavXmlUtil.buildSoftwareBlock());
        request.setInvoiceNumberQuery(invoiceNumberQuery);
        return request;
    }

    private BasicHeaderType buildHeader(String requestId, Instant timestamp) {
        BasicHeaderType header = new BasicHeaderType();
        header.setRequestId(requestId);
        header.setTimestamp(NavXmlUtil.toXmlGregorianCalendar(timestamp));
        header.setRequestVersion("3.0");
        header.setHeaderVersion("1.0");
        return header;
    }

    private UserHeaderType buildUser(String requestSignature, NavCredentials credentials) {
        CryptoType passwordCrypto = new CryptoType();
        passwordCrypto.setCryptoType("SHA-512");
        passwordCrypto.setValue(credentials.passwordHash());

        CryptoType signatureCrypto = new CryptoType();
        signatureCrypto.setCryptoType("SHA3-512");
        signatureCrypto.setValue(requestSignature);

        UserHeaderType user = new UserHeaderType();
        user.setLogin(credentials.login());
        user.setPasswordHash(passwordCrypto);
        user.setTaxNumber(credentials.taxNumber());
        user.setRequestSignature(signatureCrypto);
        return user;
    }

    // --- Mapping helpers ---

    private TaxpayerInfo.IncorporationType mapIncorporationType(
            hu.riskguard.datasource.internal.generated.nav.IncorporationType navType
    ) {
        if (navType == null) return TaxpayerInfo.IncorporationType.ORGANIZATION;
        return switch (navType) {
            case SELF_EMPLOYED -> TaxpayerInfo.IncorporationType.SELF_EMPLOYED;
            case TAXABLE_PERSON -> TaxpayerInfo.IncorporationType.TAXABLE_PERSON;
            default -> TaxpayerInfo.IncorporationType.ORGANIZATION;
        };
    }

    private String buildAddress(hu.riskguard.datasource.internal.generated.nav.TaxpayerAddressListType addressList) {
        if (addressList == null || addressList.getTaxpayerAddressItem() == null
                || addressList.getTaxpayerAddressItem().isEmpty()) {
            return null;
        }
        // getTaxpayerAddress() returns DetailedAddressType directly (not an AddressType wrapper)
        var addr = addressList.getTaxpayerAddressItem().get(0).getTaxpayerAddress();
        if (addr == null) return null;
        return String.join(" ", nullSafe(addr.getPostalCode()), nullSafe(addr.getCity()),
                nullSafe(addr.getStreetName()), nullSafe(addr.getPublicPlaceCategory()),
                nullSafe(addr.getNumber())).trim();
    }

    private String nullSafe(String s) { return s != null ? s : ""; }

    private InvoiceSummary mapInvoiceSummary(InvoiceDigestType digest, InvoiceDirection direction) {
        LocalDate issueDate = digest.getInvoiceIssueDate() != null
                ? digest.getInvoiceIssueDate().toGregorianCalendar().toZonedDateTime().toLocalDate() : null;
        LocalDate deliveryDate = digest.getInvoiceDeliveryDate() != null
                ? digest.getInvoiceDeliveryDate().toGregorianCalendar().toZonedDateTime().toLocalDate() : null;

        return new InvoiceSummary(
                digest.getInvoiceNumber(),
                digest.getInvoiceOperation() != null ? digest.getInvoiceOperation().value() : null,
                digest.getSupplierTaxNumber(),
                digest.getSupplierName(),
                digest.getCustomerTaxNumber(),
                digest.getCustomerName(),
                issueDate,
                deliveryDate,
                digest.getInvoiceNetAmount(),
                digest.getCurrency(),
                direction
        );
    }

    private InvoiceDetail mapInvoiceDetail(
            String invoiceNumber,
            hu.riskguard.datasource.internal.generated.nav.InvoiceData invoiceData
    ) {
        if (invoiceData.getInvoiceMain() == null || invoiceData.getInvoiceMain().getInvoice() == null) {
            return new InvoiceDetail(invoiceNumber, null, null, null, null, null,
                    null, null, BigDecimal.ZERO, "HUF", InvoiceDirection.OUTBOUND,
                    List.of(), null, Map.of());
        }

        var invoice = invoiceData.getInvoiceMain().getInvoice();
        var head = invoice.getInvoiceHead();
        var lines = invoice.getInvoiceLines();

        String supplierTaxNumber = null;
        String supplierName = null;
        String customerTaxNumber = null;
        String customerName = null;
        String paymentMethod = null;
        LocalDate issueDate = invoiceData.getInvoiceIssueDate() != null
                ? invoiceData.getInvoiceIssueDate().toGregorianCalendar().toZonedDateTime().toLocalDate() : null;

        if (head != null) {
            if (head.getSupplierInfo() != null) {
                supplierTaxNumber = head.getSupplierInfo().getSupplierTaxNumber() != null
                        ? head.getSupplierInfo().getSupplierTaxNumber().getTaxpayerId() : null;
                supplierName = head.getSupplierInfo().getSupplierName();
            }
            if (head.getCustomerInfo() != null) {
                customerName = head.getCustomerInfo().getCustomerName();
                // CustomerInfoType.getCustomerVatData().getCustomerTaxNumber() extends TaxNumberType
                if (head.getCustomerInfo().getCustomerVatData() != null
                        && head.getCustomerInfo().getCustomerVatData().getCustomerTaxNumber() != null) {
                    customerTaxNumber = head.getCustomerInfo().getCustomerVatData()
                            .getCustomerTaxNumber().getTaxpayerId();
                }
            }
            if (head.getInvoiceDetail() != null && head.getInvoiceDetail().getPaymentMethod() != null) {
                paymentMethod = head.getInvoiceDetail().getPaymentMethod().value();
            }
        }

        List<InvoiceLineItem> lineItems = new ArrayList<>();
        if (lines != null && lines.getLine() != null) {
            for (var line : lines.getLine()) {
                String vtszCode = null;
                String productCodeCategory = null;
                String productCodeValue = null;
                if (line.getProductCodes() != null && line.getProductCodes().getProductCode() != null) {
                    for (var pc : line.getProductCodes().getProductCode()) {
                        if ("VTSZ".equals(pc.getProductCodeCategory() != null ? pc.getProductCodeCategory().value() : null)) {
                            vtszCode = pc.getProductCodeValue();
                            productCodeCategory = "VTSZ";
                            productCodeValue = pc.getProductCodeValue();
                            break;
                        }
                        if (productCodeCategory == null && pc.getProductCodeCategory() != null) {
                            productCodeCategory = pc.getProductCodeCategory().value();
                            productCodeValue = pc.getProductCodeValue();
                        }
                    }
                }
                BigDecimal lineNetAmount = BigDecimal.ZERO;
                BigDecimal lineNetAmountHUF = BigDecimal.ZERO;
                if (line.getLineAmountsNormal() != null && line.getLineAmountsNormal().getLineNetAmountData() != null) {
                    lineNetAmount = line.getLineAmountsNormal().getLineNetAmountData().getLineNetAmount();
                    lineNetAmountHUF = line.getLineAmountsNormal().getLineNetAmountData().getLineNetAmountHUF();
                }
                lineItems.add(new InvoiceLineItem(
                        line.getLineNumber() != null ? line.getLineNumber().intValue() : 0,
                        line.getLineDescription(),
                        line.getQuantity(),
                        line.getUnitOfMeasure() != null ? line.getUnitOfMeasure().value() : line.getUnitOfMeasureOwn(),
                        line.getUnitPrice(),
                        lineNetAmount,
                        lineNetAmountHUF,
                        vtszCode,
                        productCodeCategory,
                        productCodeValue
                ));
            }
        }

        return new InvoiceDetail(
                invoiceNumber,
                null, // operation
                supplierTaxNumber,
                supplierName,
                customerTaxNumber,
                customerName,
                issueDate,
                null, // delivery date
                BigDecimal.ZERO, // net amount (would need to parse from summary)
                "HUF",
                InvoiceDirection.OUTBOUND,
                lineItems,
                paymentMethod,
                Map.of()
        );
    }

    private String decompressGzip(byte[] compressed) {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new DataSourceException(ADAPTER_NAME, "Failed to decompress GZIP invoice data: " + e.getMessage(), e);
        }
    }
}
