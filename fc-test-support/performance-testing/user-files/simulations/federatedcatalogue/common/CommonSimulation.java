package federatedcatalogue.common;

import static federatedcatalogue.SimulationHelper.getResourcePath;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public abstract class CommonSimulation extends Simulation {
  private static final String APP_PROPERTIES_FILENAME = "application.properties";
  private static final String GRANT_TYPE_KEY = "grant_type";
  private static final String CLIENT_ID_KEY = "client_id";
  private static final String CLIENT_SECRET_KEY = "client_secret";
  private static final String USERNAME_KEY = "username";
  private static final String PASSWORD_KEY = "password";
  private static final String VC_FILENAME = "vc.json";
  public static final String IS_ADDED_PARAM = "isAdded";
  public static final String SD_HASH_PARAM = "sdHash";

  private String sdContent;

  {
    try {
      sdContent = Files.readString(Path.of(getResourcePath() + VC_FILENAME));
    } catch (FileNotFoundException exception) {
      System.out.println("Can't find " + VC_FILENAME + " file in resources: " + exception);
    } catch (IOException exception) {
      System.out.println("Can't parse " + VC_FILENAME + " file: " + exception);
    }
  }

  public String getSdContent() {
    return sdContent;
  }

  static {
    try {
      FileInputStream propFile = new FileInputStream(getResourcePath() + APP_PROPERTIES_FILENAME);
      Properties p = new Properties(System.getProperties());
      p.load(propFile);
      System.setProperties(p);
    } catch (FileNotFoundException exception) {
      System.out.println("Can't find " + APP_PROPERTIES_FILENAME + " file in resources: " + exception);
    } catch (IOException exception) {
      System.out.println("Can't parse " + APP_PROPERTIES_FILENAME + " file: " + exception);
    }
  }

  HttpProtocolBuilder httpProtocol = http
      .baseUrl(System.getProperty("federated-catalogue.serverUrl"))
      .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      .acceptLanguageHeader("en-US,en;q=0.5")
      .acceptEncodingHeader("gzip, deflate")
      .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0");

  public HttpProtocolBuilder getHttpProtocol() {
    return this.httpProtocol;
  }

  public ChainBuilder getAccessToken(String username, String password) {
    return exec(
        http("Auth Token").post(System.getProperty("federated-catalogue.auth-server.token-url"))
            .formParam(GRANT_TYPE_KEY, PASSWORD_KEY)
            .formParam(CLIENT_ID_KEY, System.getProperty("federated-catalogue.auth-server.client-id"))
            .formParam(CLIENT_SECRET_KEY, System.getProperty("federated-catalogue.auth-server.client-secret"))
            .formParam(USERNAME_KEY, username)
            .formParam(PASSWORD_KEY, password)
            .check(status().is(200))
            .check(jsonPath("$..access_token").saveAs("access_token"))
    );
  }
}
