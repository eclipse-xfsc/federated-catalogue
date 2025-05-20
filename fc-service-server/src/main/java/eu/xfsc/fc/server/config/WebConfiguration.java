package eu.xfsc.fc.server.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UrlPathHelper;

/**
 * Defines callback methods to customize the Java-based configuration for Spring MVC.
 * Custom implementation of the {@link WebMvcConfigurer}.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
  /**
   * Defines cross-origin resource sharing.
   */
  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins("*")
          .allowedMethods("GET", "PUT", "POST", "PATCH", "DELETE", "OPTIONS");
      }
    };
  }

  /**
   * Defines firewall settings.
   */
  @Bean
  public HttpFirewall configureFirewall() {
    StrictHttpFirewall strictHttpFirewall = new StrictHttpFirewall();
    strictHttpFirewall.setAllowUrlEncodedDoubleSlash(true);
    strictHttpFirewall.setAllowUrlEncodedSlash(true);
    strictHttpFirewall.setAllowUrlEncodedPercent(true);
    strictHttpFirewall.setAllowUrlEncodedPeriod(true);
    strictHttpFirewall.setAllowSemicolon(true);
    return strictHttpFirewall;
  }

  /**
   * Defines RegistrationsHandlerMapping settings.
   */
  @Bean
  public WebMvcRegistrations webMvcRegistrationsHandlerMapping() {
    return new WebMvcRegistrations() {
        @Override
        public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
            RequestMappingHandlerMapping mapper = new RequestMappingHandlerMapping();
            mapper.setUrlDecode(false);
            //mapper.setUrlPathHelper(UrlPathHelper.defaultInstance.setUrlDecode(false));
            return mapper;
        }
    };
  }
  
  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
      //log.info("Configuring Tomcat to allow encoded slashes.");
      return factory -> factory.addConnectorCustomizers(connector -> connector.setEncodedSolidusHandling(
              EncodedSolidusHandling.DECODE.getValue()));
  }  
    
}