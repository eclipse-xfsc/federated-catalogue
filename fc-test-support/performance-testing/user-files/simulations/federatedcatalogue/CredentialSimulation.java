package federatedcatalogue;

import static federatedcatalogue.CredentialSigner.signCredential;
import static federatedcatalogue.SimulationHelper.addRandomId;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import federatedcatalogue.common.CommonSimulation;
import io.gatling.javaapi.core.*;
import java.time.Duration;

public class CredentialSimulation extends CommonSimulation {
  private static final String USERS_NUMBER_PARAM = "fc-performance-testing.asset.users";
  private static final String USERS_NUMBER_RAMP_TIME_PARAM = "fc-performance-testing.asset.rampTime";
  private static final String DURING_TIME_PARAM = "fc-performance-testing.asset.duringTime";

  ChainBuilder get = exec(http("Get credential")
      .get(session -> "/assets/" + session.get(ASSET_ID_PARAM))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token").toString())
      .check(status().is(200)));

  ChainBuilder search = exec(http("Search credential")
      .get(session -> "/assets?statuses=ACTIVE,DEPRECATED,REVOKED,EOL&hashes=" + session.get(ASSET_HASH_PARAM))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ChainBuilder add = exec(http("Add credential")
      .post("/assets")
      .body(StringBody(session -> signCredential(addRandomId(getCredentialContent()))))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().saveAs(IS_ADDED_PARAM))
      .check(status().is(201))
      .check(jsonPath("$..assetHash").saveAs(ASSET_HASH_PARAM))
      .check(jsonPath("$..id").saveAs(ASSET_ID_PARAM)));

  ChainBuilder revoke = exec(http("Revoke credential")
      .post(session -> "/assets/" + session.get(ASSET_HASH_PARAM).toString() + "/revoke")
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ChainBuilder delete = exec(http("Delete credential")
      .delete(session -> "/assets/" + session.get(ASSET_HASH_PARAM).toString())
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().is(200)));

  ScenarioBuilder manipulateCredentials = scenario("Manipulate credentials")
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
        manipulateCredentials.injectOpen(rampUsers(Integer.parseInt(System.getProperty(USERS_NUMBER_PARAM)))
                .during(Integer.parseInt(System.getProperty(USERS_NUMBER_RAMP_TIME_PARAM))))
            .protocols(getHttpProtocol()));
  }
}
