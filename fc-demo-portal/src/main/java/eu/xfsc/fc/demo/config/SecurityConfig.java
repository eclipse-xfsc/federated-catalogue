package eu.xfsc.fc.demo.config;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;


/**
 * Specifies the application's security configuration.
 */
@Configuration
@EnableWebSecurity //(debug = true)
public class SecurityConfig { 

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
          .csrf(c -> c.disable())
          .authorizeHttpRequests(authorization -> authorization
        		  .requestMatchers(antMatcher("/")).permitAll()
        		  .requestMatchers(antMatcher("/index.html")).permitAll()
        		  .requestMatchers(antMatcher("/css/**")).permitAll()
        		  .requestMatchers(antMatcher("/js/**")).permitAll()
        		  .anyRequest().authenticated()
       	  )
          .oauth2Login(oauth2Login ->
              oauth2Login
                  .loginPage("/oauth2/authorization/fc-client-oidc")
          )
          .oauth2Client(Customizer.withDefaults())
          		.logout(c -> c.logoutRequestMatcher(antMatcher(HttpMethod.POST, "/logout"))
          			.logoutSuccessUrl("/index.html")
          			.clearAuthentication(true)
          			.invalidateHttpSession(true)
          			.deleteCookies("JSESSIONID","XSRF-TOKEN"));
        return http.build();
    }
       
}