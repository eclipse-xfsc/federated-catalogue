package eu.xfsc.fc.server.util;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class SessionUtilsTest {
    private static final String TEST_PARTICIPANT_ID = "http://example.org/test-provider";

    @Test
    @WithMockJwtAuth(claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
                    {@StringClaim(name = "participant_id", value = TEST_PARTICIPANT_ID)})))
    public void testGetParticipantIdUtilMethod() {
        assertEquals(SessionUtils.getSessionParticipantId(), TEST_PARTICIPANT_ID);
    }
}
