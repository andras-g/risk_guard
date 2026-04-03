package hu.riskguard.datasource.internal.adapters.nav;

import hu.riskguard.datasource.internal.DataSourceException;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * XML marshaller/unmarshaller for NAV Online Számla JAXB-generated classes.
 *
 * <p>Single {@link JAXBContext} instance is created at startup via {@code @PostConstruct}
 * (expensive to create; thread-safe to use).
 *
 * <p>GZIP compression is applied for request bodies exceeding 512 bytes per NAV spec §1.7.
 */
@Component
public class XmlMarshaller {

    private static final Logger log = LoggerFactory.getLogger(XmlMarshaller.class);
    private static final int GZIP_THRESHOLD_BYTES = 512;

    private JAXBContext jaxbContext;

    @PostConstruct
    public void init() throws JAXBException {
        log.info("Initialising JAXBContext for NAV Online Számla schemas");
        jaxbContext = JAXBContext.newInstance(
                "hu.riskguard.datasource.internal.generated.nav"
        );
    }

    /**
     * Marshals a JAXB object to a UTF-8 XML string.
     *
     * @param request any JAXB-annotated object from the generated nav package
     * @return XML string
     * @throws DataSourceException if marshalling fails
     */
    public String marshal(Object request) {
        try {
            StringWriter writer = new StringWriter();
            var marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_ENCODING, "UTF-8");
            marshaller.setProperty(jakarta.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, false);
            marshaller.marshal(request, writer);
            return writer.toString();
        } catch (JAXBException e) {
            log.error("Failed to marshal NAV request object: {}", e.getMessage());
            throw new DataSourceException("nav-online-szamla", "XML marshalling failed: " + e.getMessage(), e);
        }
    }

    /**
     * Unmarshals an XML string to the specified JAXB type.
     *
     * @param xml  the XML response from NAV API
     * @param type the expected result type
     * @return unmarshalled object
     * @throws DataSourceException if unmarshalling fails
     */
    @SuppressWarnings("unchecked")
    public <T> T unmarshal(String xml, Class<T> type) {
        try {
            var unmarshaller = jaxbContext.createUnmarshaller();
            return (T) unmarshaller.unmarshal(new StringReader(xml));
        } catch (JAXBException e) {
            log.error("Failed to unmarshal NAV response to {}: {}", type.getSimpleName(), e.getMessage());
            throw new DataSourceException("nav-online-szamla", "XML unmarshalling failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the body bytes for a request — GZIP-compressed if over 512 bytes per NAV spec §1.7.
     * The returned byte array is ready to be sent as an HTTP request body.
     * If compressed, the caller must set Content-Encoding: gzip.
     *
     * @param xml the XML string to potentially compress
     * @return raw or GZIP-compressed bytes
     */
    public byte[] toRequestBytes(String xml) {
        byte[] raw = xml.getBytes(StandardCharsets.UTF_8);
        if (raw.length <= GZIP_THRESHOLD_BYTES) {
            return raw;
        }
        try (var out = new ByteArrayOutputStream();
             var gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
            gzip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            log.warn("GZIP compression failed for NAV request; sending uncompressed: {}", e.getMessage());
            return raw;
        }
    }

    /**
     * Returns true if the given bytes represent a GZIP-compressed payload.
     */
    public boolean isGzipCompressed(byte[] bytes) {
        return bytes.length >= 2 && bytes[0] == (byte) 0x1F && bytes[1] == (byte) 0x8B;
    }
}
