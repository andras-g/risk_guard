package hu.riskguard.datasource.internal;

/**
 * Data source-specific runtime exception for adapter failures.
 * Thrown when a data source fetch fails due to I/O or parsing errors.
 * Resilience4j fallback methods catch this to return SOURCE_UNAVAILABLE.
 */
public class DataSourceException extends RuntimeException {

    private final String adapterName;

    public DataSourceException(String adapterName, Throwable cause) {
        super("Data source fetch failed for adapter: " + adapterName, cause);
        this.adapterName = adapterName;
    }

    public DataSourceException(String adapterName, String message) {
        super("Data source fetch failed for adapter: " + adapterName + " — " + message);
        this.adapterName = adapterName;
    }

    public DataSourceException(String adapterName, String message, Throwable cause) {
        super("Data source fetch failed for adapter: " + adapterName + " — " + message, cause);
        this.adapterName = adapterName;
    }

    public String getAdapterName() {
        return adapterName;
    }
}
