package eu.xfsc.fc.server.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
public class DevPingController {

    /**
     * Security filter chain for development environment, allowing unrestricted access to /dev/** endpoints for testing the local setup.
     * This is intended for development purposes only and will not be used in production environments.
     */
    @Bean
    @Order(0) // Ensure this filter chain is evaluated before any other security configurations
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/dev/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @GetMapping("/dev/ping")
    public String ping() {
        return "pong-v1";
    }
}