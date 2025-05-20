package federatedcatalogue;

import static federatedcatalogue.SelfDescriptionSigner.signSd;
import static federatedcatalogue.SimulationHelper.addRandomId;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import federatedcatalogue.common.CommonSimulation;
import io.gatling.javaapi.core.*;
import java.time.Duration;

public class SelfDescriptionSimulation extends CommonSimulation {
  private static final String USERS_NUMBER_PARAM = "fc-performance-testing.sd.users";
  private static final String USERS_NUMBER_RAMP_TIME_PARAM = "fc-performance-testing.sd.rampTime";
  private static final String DURING_TIME_PARAM = "fc-performance-testing.sd.duringTime";

  ChainBuilder get = exec(http("Get SD")
      .get(session -> "/self-descriptions/" + session.get(SD_HASH_PARAM))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token").toString())
      .check(status().is(200)));

  ChainBuilder search = exec(http("Search SD")
      .get(session -> "/self-descriptions?statuses=ACTIVE,DEPRECATED,REVOKED,EOL&hashes=" + session.get(SD_HASH_PARAM))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ChainBuilder add = exec(http("Add SD")
      .post("/self-descriptions")
      .body(StringBody(session -> signSd(addRandomId(getSdContent()))))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().saveAs(IS_ADDED_PARAM))
      .check(status().is(201))
      .check(jsonPath("$..sdHash").saveAs(SD_HASH_PARAM)));

  ChainBuilder revoke = exec(http("Revoke SD")
      .post(session -> "/self-descriptions/" + session.get(SD_HASH_PARAM).toString() + "/revoke")
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ChainBuilder delete = exec(http("Delete SD")
      .delete(session -> "/self-descriptions/" + session.get(SD_HASH_PARAM).toString())
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ScenarioBuilder manipulateSd = scenario("Manipulate SDs")
      .exec(getAccessToken("catalog-admin", "catalog-admin"))
      .pause(Duration.ofMillis(100))
      .during(Duration.ofSeconds(Integer.parseInt(System.getProperty(DURING_TIME_PARAM)))).on(
          exec(add)
              .pause(Duration.ofMillis(100))
              .doIf(session -> session.get(IS_ADDED_PARAM).toString().equals("201"))
              .then(exec(get).pause(Duration.ofMillis(100))
                  .exec(search)
                  .pause(Duration.ofMillis(100))
                  .exec(revoke)
                  .pause(Duration.ofMillis(100))
                  .exec(delete)
                  .pause(Duration.ofMillis(100))));

  {
    setUp(
        manipulateSd.injectOpen(rampUsers(Integer.parseInt(System.getProperty(USERS_NUMBER_PARAM)))
                .during(Integer.parseInt(System.getProperty(USERS_NUMBER_RAMP_TIME_PARAM))))
            .protocols(getHttpProtocol()));
  }
}
