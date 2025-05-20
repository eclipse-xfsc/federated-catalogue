package eu.xfsc.fc.core.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Core Service configs.
 */
@Configuration
@ComponentScan(basePackages = {"eu.xfsc.fc.core"})
public class CoreConfig {    
    
    //@Bean
    //public FileStoreImpl fileStore() {
    //    return new FileStoreImpl();
    //}
}
