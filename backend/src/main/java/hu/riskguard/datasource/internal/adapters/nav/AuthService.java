package hu.riskguard.datasource.internal.adapters.nav;

import hu.riskguard.core.config.RiskGuardProperties;
import hu.riskguard.datasource.internal.AesFieldEncryptor;
import hu.riskguard.datasource.internal.AdapterHealthRepository;
import hu.riskguard.datasource.internal.DataSourceException;
import hu.riskguard.datasource.internal.NavTenantCredentialRepository;
import hu.riskguard.datasource.internal.generated.nav.BasicHeaderType;
import hu.riskguard.datasource.internal.generated.nav.CryptoType;
import hu.riskguard.datasource.internal.generated.nav.ObjectFactory;
import hu.riskguard.datasource.internal.generated.nav.SoftwareOperationType;
import hu.riskguard.datasource.internal.generated.nav.SoftwareType;
import hu.riskguard.datasource.internal.generated.nav.TokenExchangeRequest;
import hu.riskguard.datasource.internal.generated.nav.TokenExchangeResponse;
import hu.riskguard.datasource.internal.generated.nav.UserHeaderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Handles NAV Online Számla credential loading, hashing, and verification.
 *
 * <p>Credential verification uses the {@code /tokenExchange} endpoint as a ping test:
 * if the AES-128 ECB decode of the returned {@code encodedExchangeToken} succeeds,
 * the credentials are valid.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final String ADAPTER_NAME = "nav-online-szamla";

    private final NavTenantCredentialRepository credentialRepository;
    private final AesFieldEncryptor aesFieldEncryptor;
    private final AdapterHealthRepository adapterHealthRepository;
    private final XmlMarshaller xmlMarshaller;
    private final SignatureService signatureService;
    private final RiskGuardProperties riskGuardProperties;

    public AuthService(
            NavTenantCredentialRepository credentialRepository,
            AesFieldEncryptor aesFieldEncryptor,
            AdapterHealthRepository adapterHealthRepository,
            XmlMarshaller xmlMarshaller,
            SignatureService signatureService,
            RiskGuardProperties riskGuardProperties
    ) {
        this.credentialRepository = credentialRepository;
        this.aesFieldEncryptor = aesFieldEncryptor;
        this.adapterHealthRepository = adapterHealthRepository;
        this.xmlMarshaller = xmlMarshaller;
        this.signatureService = signatureService;
        this.riskGuardProperties = riskGuardProperties;
    }

    /**
     * Loads and decrypts credentials for the given tenant.
     *
     * @throws DataSourceException if no credentials are found for the tenant
     */
    public NavCredentials loadCredentials(UUID tenantId) {
        var row = credentialRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new DataSourceException(ADAPTER_NAME,
                        "No NAV credentials configured for tenant " + tenantId));
        return new NavCredentials(
                aesFieldEncryptor.decrypt(row.loginEncrypted()),
                row.passwordHash(),
                aesFieldEncryptor.decrypt(row.signingKeyEnc()),
                aesFieldEncryptor.decrypt(row.exchangeKeyEnc()),
                row.taxNumber()
        );
    }

    /**
     * Verifies the given credentials by calling the NAV {@code /tokenExchange} endpoint.
     * Decodes the returned {@code encodedExchangeToken} using AES-128 ECB with the
     * exchangeKey — if decode succeeds without exception, the credentials are valid.
     *
     * @return true if credentials are valid, false otherwise
     */
    public boolean verifyCredentials(NavCredentials credentials) {
        try {
            String baseUrl = NavXmlUtil.getBaseUrl(riskGuardProperties);
            String requestId = generateRequestId();
            Instant timestamp = Instant.now();
            String requestSignature = signatureService.computeRequestSignature(
                    requestId, timestamp, credentials.signingKey());

            TokenExchangeRequest request = buildTokenExchangeRequest(
                    requestId, timestamp, requestSignature, credentials);

            String xml = xmlMarshaller.marshal(request);
            byte[] body = xmlMarshaller.toRequestBytes(xml);
            boolean gzipped = xmlMarshaller.isGzipCompressed(body);

            var reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tokenExchange"))
                    .header("Content-Type", "application/xml")
                    .header("Accept", "application/xml")
                    .header("Accept-Encoding", "gzip")
                    .timeout(Duration.ofMillis(riskGuardProperties.getDataSource().getReadTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (gzipped) {
                reqBuilder.header("Content-Encoding", "gzip");
            }
            var httpRequest = reqBuilder.build();

            var httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(riskGuardProperties.getDataSource().getConnectTimeoutMs()))
                    .build();

            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.warn("NAV tokenExchange returned HTTP {}", response.statusCode());
                return false;
            }

            var tokenResponse = xmlMarshaller.unmarshal(response.body(), TokenExchangeResponse.class);
            if (tokenResponse.getResult() == null || !"OK".equals(tokenResponse.getResult().getFuncCode().value())) {
                log.info("NAV tokenExchange funcCode: {}", tokenResponse.getResult() != null
                        ? tokenResponse.getResult().getFuncCode() : "null");
                return false;
            }

            // Decode the exchangeToken with AES-128 ECB using the raw exchangeKey bytes
            byte[] encodedToken = tokenResponse.getEncodedExchangeToken();
            return decodeExchangeToken(encodedToken, credentials.exchangeKey());

        } catch (DataSourceException e) {
            log.warn("NAV credential verification failed (DataSourceException): {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("NAV credential verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Computes SHA-512 of the raw password and returns the uppercase hex string.
     * This is the {@code passwordHash} sent in NAV API requests.
     */
    public String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-512 hash failed", e);
        }
    }

    private boolean decodeExchangeToken(byte[] encodedToken, String exchangeKey) {
        try {
            // NAV spec: AES/ECB/PKCS5Padding, key = raw exchangeKey bytes (16 bytes = AES-128)
            byte[] keyBytes = exchangeKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length != 16) {
                log.warn("Exchange key must be exactly 16 bytes (AES-128), got {} bytes", keyBytes.length);
                return false;
            }
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            cipher.doFinal(encodedToken); // if this succeeds without exception, credentials are valid
            return true;
        } catch (Exception e) {
            log.debug("Exchange token decode failed — likely invalid credentials: {}", e.getMessage());
            return false;
        }
    }

    static String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private TokenExchangeRequest buildTokenExchangeRequest(
            String requestId,
            Instant timestamp,
            String requestSignature,
            NavCredentials credentials
    ) {
        ObjectFactory factory = new ObjectFactory();

        BasicHeaderType header = factory.createBasicHeaderType();
        header.setRequestId(requestId);
        header.setTimestamp(NavXmlUtil.toXmlGregorianCalendar(timestamp));
        header.setRequestVersion("3.0");
        header.setHeaderVersion("1.0");

        CryptoType passwordCrypto = factory.createCryptoType();
        passwordCrypto.setCryptoType("SHA-512");
        passwordCrypto.setValue(credentials.passwordHash());

        CryptoType signatureCrypto = factory.createCryptoType();
        signatureCrypto.setCryptoType("SHA3-512");
        signatureCrypto.setValue(requestSignature);

        UserHeaderType user = factory.createUserHeaderType();
        user.setLogin(credentials.login());
        user.setPasswordHash(passwordCrypto);
        user.setTaxNumber(credentials.taxNumber());
        user.setRequestSignature(signatureCrypto);

        SoftwareType software = NavXmlUtil.buildSoftwareBlock();

        TokenExchangeRequest request = new TokenExchangeRequest();
        request.setHeader(header);
        request.setUser(user);
        request.setSoftware(software);
        return request;
    }
}
