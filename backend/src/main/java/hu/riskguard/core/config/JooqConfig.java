package hu.riskguard.core.config;

import hu.riskguard.core.security.TenantAwareDSLContext;
import hu.riskguard.core.security.TenantJooqListener;
import org.jooq.DSLContext;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    @Bean
    public DefaultConfiguration jooqConfiguration(DataSource dataSource, TenantJooqListener tenantJooqListener) {
        DefaultConfiguration config = new DefaultConfiguration();
        config.set(new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(dataSource)));
        config.set(org.jooq.SQLDialect.POSTGRES);
        
        // Add listeners
        config.setVisitListener(tenantJooqListener); // As VisitListener
        config.setRecordListener(tenantJooqListener); // As RecordListener
        
        return config;
    }

    @Bean
    public DSLContext dslContext(DefaultConfiguration config) {
        return new TenantAwareDSLContext(config);
    }
}
