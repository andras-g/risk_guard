package hu.riskguard.epr.report.internal;

import hu.riskguard.epr.producer.domain.ProducerProfile;
import hu.riskguard.epr.report.internal.KgKgyfNeAggregator.KfCodeAggregate;
import hu.riskguard.epr.report.internal.generated.okirkapu.KGKGYFNEACS;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Marshals aggregated EPR data into an OKIRkapu KG:KGYF-NÉ XML byte array.
 *
 * <p>Uses JAXB-generated classes from {@code KG-KGYF-NE-v1.16.xsd}.
 * Validates output against the XSD before returning.
 * Thread-safe: the {@link JAXBContext} is created once at startup via {@link PostConstruct}.
 */
@Component
class KgKgyfNeMarshaller {

    private static final Logger log = LoggerFactory.getLogger(KgKgyfNeMarshaller.class);

    /** OKIRkapu XSD schema version — must match the XSD filename. */
    public static final String XSD_VERSION = "1.16";

    private static final String XSD_CLASSPATH = "xsd/okirkapu/KG-KGYF-NE-v1.16.xsd";

    private JAXBContext jaxbContext;
    private Schema schema;

    @PostConstruct
    void init() {
        try {
            jaxbContext = JAXBContext.newInstance(KGKGYFNEACS.class);
            SchemaFactory sf = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try (InputStream xsdStream = new ClassPathResource(XSD_CLASSPATH).getInputStream()) {
                schema = sf.newSchema(new StreamSource(xsdStream));
            }
            log.info("KgKgyfNeMarshaller initialised — XSD version {}", XSD_VERSION);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise KgKgyfNeMarshaller JAXB context", e);
        }
    }

    /**
     * Marshal a complete KG:KGYF-NÉ XML document to bytes.
     *
     * @param profile     the producer's identity data
     * @param aggregates  KF-code weight aggregates (may be empty — produces zero report)
     * @param periodStart start of the reporting period (inclusive)
     * @param periodEnd   end of the reporting period (inclusive)
     * @return UTF-8 encoded XML bytes, validated against XSD
     * @throws IllegalStateException if XSD validation fails
     */
    byte[] marshal(ProducerProfile profile, List<KfCodeAggregate> aggregates,
                   LocalDate periodStart, LocalDate periodEnd) {
        try {
            KGKGYFNEACS root = buildDocument(profile, aggregates, periodStart, periodEnd);

            Marshaller m = jaxbContext.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            m.setSchema(schema);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            m.marshal(root, out);
            return out.toByteArray();
        } catch (JAXBException e) {
            throw new IllegalStateException("XSD validation or marshalling failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to marshal KG:KGYF-NÉ XML", e);
        }
    }

    // ─── Document construction ───────────────────────────────────────────────

    private KGKGYFNEACS buildDocument(ProducerProfile profile, List<KfCodeAggregate> aggregates,
                                      LocalDate periodStart, LocalDate periodEnd) throws Exception {
        KGKGYFNEACS root = new KGKGYFNEACS();

        // ADATCSOMAG header block
        KGKGYFNEACS.ADATCSOMAG header = new KGKGYFNEACS.ADATCSOMAG();
        header.setLETREHOZVA(toXmlDateTime(OffsetDateTime.now(java.time.ZoneId.of("Europe/Budapest"))));
        header.setKUJ(profile.okirClientId() != null ? profile.okirClientId() : 0);
        header.setEV(periodStart.getYear());
        header.setNE(quarterOf(periodStart));
        root.setADATCSOMAG(header);

        // KG_KGYF_NE producer identity block
        KGKGYFNEACS.KGKGYFNE identity = new KGKGYFNEACS.KGKGYFNE();
        identity.setUGYFELNEVE(profile.legalName());
        identity.setSZEKHELYORSZAGKOD(profile.addressCountryCode() != null ? profile.addressCountryCode() : "HU");
        identity.setSZEKHELYTELEPULES(profile.addressCity());
        identity.setSZEKHELYIRSZAM(profile.addressPostalCode());
        identity.setSZEKHELYUTCA(profile.addressStreetName());
        if (profile.addressStreetType() != null) {
            identity.setSZEKHELYKOZTERULETTIPUS(profile.addressStreetType());
        }
        if (profile.addressHouseNumber() != null) {
            identity.setSZEKHELYHAZSZAM(profile.addressHouseNumber());
        }

        // ADOSZAM: use stored taxNumber (8 or 11 digits)
        if (profile.taxNumber() != null && !profile.taxNumber().isBlank()) {
            identity.setADOSZAM(profile.taxNumber());
        }

        // KSH statistical number decomposition: NNNNNNNN-TTTT-GGG-MM
        if (profile.kshStatisticalNumber() != null && !profile.kshStatisticalNumber().isBlank()) {
            String[] kshParts = profile.kshStatisticalNumber().split("-");
            if (kshParts.length == 4) {
                identity.setKSHTORZSSZAM(kshParts[0]);
                identity.setKSHTEAORKOD(kshParts[1]);
                identity.setKSHGAZDFORMAKOD(kshParts[2]);
                identity.setKSHMEGYEKOD(kshParts[3]);
            }
        }

        // Contact person (KAPCSTARTO_* elements)
        identity.setKAPCSTARTONEV(profile.contactName());
        identity.setKAPCSTARTOBEOSZTAS(profile.contactTitle());
        identity.setKAPCSTARTOORSZAGKOD(profile.contactCountryCode() != null ? profile.contactCountryCode() : "HU");
        identity.setKAPCSTARTOIRSZAM(profile.contactPostalCode());
        identity.setKAPCSTARTOTELEPULES(profile.contactCity());
        identity.setKAPCSTARTOUTCA(profile.contactStreetName());
        identity.setKAPCSTARTOTELEFON(profile.contactPhone());
        identity.setKAPCSTARTOEMAIL(profile.contactEmail());

        // Role flags
        identity.setGYARTO10(profile.isManufacturer() ? 1 : 0);
        identity.setEGYENITELJESITO10(profile.isIndividualPerformer() ? 1 : 0);
        identity.setALVALLALKOZO10(profile.isSubcontractor() ? 1 : 0);
        identity.setKONCESSZOR10(profile.isConcessionaire() ? 1 : 0);

        // Zero report / truth / payment flags
        identity.setNULLASJELENTES10(aggregates.isEmpty() ? 1 : 0);
        identity.setMEGFELELVALOSAGNAK10(1);
        identity.setFIZETESIKOTELEZETTSEG10(aggregates.isEmpty() ? 0 : 1);

        root.setKGKGYFNE(identity);

        // KG_KGYF_NE_TERMEK_FORG data rows (only REGISTRY_MATCH aggregates)
        for (KfCodeAggregate agg : aggregates) {
            String kfCode = agg.kfCode(); // 8-char: e.g. "CS01012B"
            if (kfCode.length() != 8) {
                log.warn("Skipping aggregate with invalid KF code length (expected 8, got {}): '{}'",
                        kfCode.length(), kfCode);
                continue;
            }
            KGKGYFNEACS.KGKGYFNETERMEKFORG row = new KGKGYFNEACS.KGKGYFNETERMEKFORG();
            row.setKFTERMEKARAMKOD(kfCode.substring(0, 2));
            row.setKFANYAGARAMKOD(kfCode.substring(2, 4));
            row.setKFCSOPORTKOD(kfCode.substring(4, 6));
            row.setKFKOTELEZETTSEGKOD(kfCode.substring(6, 7));
            row.setKFSZARMAZASKOD(kfCode.substring(7, 8));
            row.setMENNYISEG(agg.totalWeightKg().doubleValue());
            root.getKGKGYFNETERMEKFORG().add(row);
        }

        return root;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static XMLGregorianCalendar toXmlDateTime(OffsetDateTime dt) throws Exception {
        GregorianCalendar gc = GregorianCalendar.from(dt.atZoneSameInstant(ZoneOffset.UTC));
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(gc);
    }

    static int quarterOf(LocalDate date) {
        return (date.getMonthValue() - 1) / 3 + 1;
    }
}
