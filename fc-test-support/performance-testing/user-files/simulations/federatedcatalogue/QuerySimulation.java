package federatedcatalogue;

import static federatedcatalogue.SelfDescriptionSigner.signSd;
import static federatedcatalogue.SimulationHelper.addRandomId;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import federatedcatalogue.common.CommonSimulation;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;

public class QuerySimulation extends CommonSimulation {
  private static final String USERS_NUMBER_PARAM = "fc-performance-testing.query.users";
  private static final String USERS_NUMBER_RAMP_TIME_PARAM = "fc-performance-testing.query.rampTime";
  private static final String DURING_TIME_PARAM = "fc-performance-testing.query.duringTime";

  private static final String IS_ADDED_PARAM = "isAdded";

  ChainBuilder add = exec(http("Add SD")
      .post("/self-descriptions")
      .body(StringBody(session -> signSd(addRandomId(getSdContent()))))
      .header("Content-Type", "application/json")
      .header("Authorization", session -> "Bearer " + session.get("access_token"))
      .check(status().saveAs(IS_ADDED_PARAM))
      .check(status().is(201))
      .check(jsonPath("$..sdHash").saveAs(SD_HASH_PARAM)));

  private ChainBuilder getQuery(String query) {
    return exec(http("Query")
        .post("/query")
        .body(StringBody(session -> query))
        .header("Content-Type", "application/json")
        .header("Authorization", session -> "Bearer " + session.get("access_token"))
        .check(status().is(200)));
  }

  ScenarioBuilder manipulateQueries = scenario("Manipulate Queries")
      .exec(getAccessToken("catalog-admin", "catalog-admin"))
      .pause(Duration.ofMillis(100))
      .during(Duration.ofSeconds(Integer.parseInt(System.getProperty(DURING_TIME_PARAM))))
      .on(
          exec(getQuery("{\"statement\": \"MATCH (n) RETURN n\", \"parameters\": null}"))
              .pause(Duration.ofMillis(100))
              .exec(getQuery("{\"statement\": \"MATCH (n:ServiceOffering) RETURN n LIMIT 25\", \"parameters\": null}"))
              .pause(Duration.ofMillis(100))
              .exec(getQuery(
                  "{\"statement\": \"MATCH (n)-[:termsAndConditions]->(m) where m.hash= $hash RETURN n LIMIT 25\",  \"parameters\": { \"hash\": \"1234\"}}"))
              .pause(Duration.ofMillis(100))
              .exec(getQuery(
                  "{\"statement\": \"MATCH (n:ServiceOffering) where n.uri IS NOT NULL RETURN n.uri LIMIT 150\", \"parameters\": null}"))
              .pause(Duration.ofMillis(100))
              .exec(getQuery(
                  "{\"statement\": \"MATCH (n)-[:mySoftwareOffering]->(m) where m.formatType = $formatType RETURN n \", \"parameters\": { \"formatType\": \"format type\"}}"))
              .pause(Duration.ofMillis(100))
              .exec(getQuery(
                  "{\"statement\": \"MATCH (n)-[:mySoftwareOffering]->(m) where m.requestType = $requestType RETURN m LIMIT 25\", \"parameters\": { \"requestType\": \"request type\"}}"))
              .pause(Duration.ofMillis(100))
      );

  {
    setUp(manipulateQueries
        .injectOpen(rampUsers(Integer.parseInt(System.getProperty(USERS_NUMBER_PARAM)))
            .during(Integer.parseInt(System.getProperty(USERS_NUMBER_RAMP_TIME_PARAM))))
    ).protocols(getHttpProtocol());
  }
}
