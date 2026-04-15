package eu.xfsc.fc.core.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
// Wires SecurityAuditorAware to populate createdBy/modifiedBy from JWT subject on every JPA write
@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware")
@EnableJpaRepositories(basePackages = "eu.xfsc.fc.core.dao")
@RequiredArgsConstructor
public class DatabaseConfig {

  private final DataSource dataSource;

  @Bean
  public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
    LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
    emf.setDataSource(dataSource);
    emf.setPackagesToScan("eu.xfsc.fc.core.dao");
    HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
    vendorAdapter.setGenerateDdl(false);
    emf.setJpaVendorAdapter(vendorAdapter);
    emf.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "validate");
    emf.getJpaPropertyMap().put("org.hibernate.envers.audit_table_suffix", "_aud");
    emf.getJpaPropertyMap().put("org.hibernate.envers.revision_field_name", "rev");
    emf.getJpaPropertyMap().put("org.hibernate.envers.revision_type_field_name", "revtype");
    emf.getJpaPropertyMap().put("org.hibernate.envers.store_data_at_delete", "true");
    return emf;
  }

  @Bean
  public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }
}
