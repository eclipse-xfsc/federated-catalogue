package eu.xfsc.fc.server;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * This test ensures that OPTION requests are available application-wide without authentication.
 * Most browsers expect the ability to perform preflight checks to determine the interactions allowed by the server.
 * <br>
 * For more information on the intent:
 * - <a href="https://www.w3.org/TR/2020/SPSD-cors-20200602/#resource-preflight-requests">W3C Specification</a>
 * - <a href="https://en.wikipedia.org/wiki/Cross-origin_resource_sharing">Wikipedia</a>
 */

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class OptionsRequestTest {

    @LocalServerPort
    private int port;
    /**
     * Since the port is dynamic, the URL is generated at runtime.
     */
    private final Supplier<String> url = () -> "http://localhost:" + port + "/participants/2";
    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Takes all allowed headers and combines them to one distinct list of all values.
     *
     * @param allowHeaders Collection of all allow headers from a response
     * @return Distinct collection of all allow headers.
     */
    private static Collection<String> flattenAndDistinctHeaders(@NonNull Collection<String> allowHeaders) {
        return allowHeaders.stream()
                .flatMap(header -> Arrays.stream(StringUtils.split(header, ',')))
                .distinct()
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("WHEN a request is made using the OPTIONS method THEN the call should be permitted")
    public void isRequestWithOptionsAllowed() {
        final var response = restTemplate.exchange(url.get(), OPTIONS, null, String.class);
        final var nonNullAllow = Optional.ofNullable(response.getHeaders().get("Allow"))
                .map(OptionsRequestTest::flattenAndDistinctHeaders)
                .orElse(Collections.emptyList());

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(nonNullAllow).containsExactlyInAnyOrder("GET", "DELETE", "OPTIONS", "HEAD", "PUT");
    }

    @Test
    @DisplayName("WHEN a request is made using any method other than OPTIONS THEN the call requires authentication")
    public void isRequestToNoneOptionsWithAuth() {
        final var response = restTemplate.exchange(url.get(), GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(UNAUTHORIZED);
    }
}