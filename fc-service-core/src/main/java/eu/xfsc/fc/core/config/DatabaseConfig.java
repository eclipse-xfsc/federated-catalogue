package eu.xfsc.fc.core.config;

import java.util.Properties;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

@Configuration 
@EnableTransactionManagement
public class DatabaseConfig implements TransactionManagementConfigurer {

  @Autowired
  DataSource dataSource;
  
  /**
   * Create the session Factory bean.
   *
   * @return The sessionFactory bean
   */
  @Bean(name="entityManagerFactory")
  public LocalSessionFactoryBean sessionFactory() {
    LocalSessionFactoryBean sessionFactory = new LocalSessionFactoryBean();
    sessionFactory.setDataSource(dataSource);
    sessionFactory.setHibernateProperties(hibernateProperties());
    return sessionFactory;
  }
  
  /**
   * Create the transaction manager.
   * It is still required even when we don't use ORM/Hibernate, in order for Neo4j
   * to participate in common DB transactions
   *
   * @return the transaction manager.
   */
  @Bean
  public PlatformTransactionManager hibernateTransactionManager(SessionFactory sessionFactory) {
    HibernateTransactionManager transactionManager = new HibernateTransactionManager();
    transactionManager.setSessionFactory(sessionFactory);
    return transactionManager;
  }
  
  private Properties hibernateProperties() {
    Properties hibernateProperties = new Properties();
    hibernateProperties.setProperty("hibernate.hbm2ddl.auto", "validate");
    return hibernateProperties;
  }

  @Override
  public TransactionManager annotationDrivenTransactionManager() {
	return hibernateTransactionManager(sessionFactory().getObject());
  }
	
}
