package eu.xfsc.fc.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import eu.xfsc.fc.client.ParticipantClient;
import eu.xfsc.fc.client.RoleClient;
import eu.xfsc.fc.client.SessionClient;
import eu.xfsc.fc.client.UserClient;

/**
 * Defines additional configuration fot the web demo-application.
 * */
@Configuration
public class ClientConfig {
    
  @Bean
  public ParticipantClient participantClient(@Value("${federated-catalogue.base-uri}") final String baseUri, WebClient webClient) {
      return new ParticipantClient(baseUri, webClient);
  }

  @Bean
  public RoleClient roleClient(@Value("${federated-catalogue.base-uri}") final String baseUri, WebClient webClient) {
      return new RoleClient(baseUri, webClient);
  }
  
  @Bean
  public SessionClient sessionClient(@Value("${federated-catalogue.base-uri}") final String baseUri, WebClient webClient) {
      return new SessionClient(baseUri, webClient);
  }
  
  @Bean
  public UserClient userClient(@Value("${federated-catalogue.base-uri}") final String baseUri, WebClient webClient) {
      return new UserClient(baseUri, webClient);
  }
  
  /**
   * Defines the web client bean used to connect to the Federated Catalogue Server.
   *
   * @param fcUri Federated Catalog URI.
   */ 
  @Bean(name = "fcServer")
  public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager,
          @Value("${federated-catalogue.base-uri}") final String fcUri) {
      
      ObjectMapper mapper = new ObjectMapper()
              //.findAndRegisterModules()   // 
              .registerModule(new ParameterNamesModule())
              .registerModule(new JavaTimeModule())
              .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      
      ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
      oauth2Client.setDefaultOAuth2AuthorizedClient(true);
      //oauth2Client.setDefaultClientRegistrationId("fc-client-oidc");

      return WebClient.builder()
        .apply(oauth2Client.oauth2Configuration())
        .baseUrl(fcUri)
        .codecs(configurer -> {
            configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
            configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
        })
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
  
  //@Bean(name = "fcServer")
  //WebClient webClient(ReactiveClientRegistrationRepository clientRegistrations,
  //        ServerOAuth2AuthorizedClientRepository authorizedClients) {
  //    ServerOAuth2AuthorizedClientExchangeFilterFunction oauth =
  //            new ServerOAuth2AuthorizedClientExchangeFilterFunction(clientRegistrations, authorizedClients);
      // (optional) explicitly opt into using the oauth2Login to provide an access token implicitly
      // oauth.setDefaultOAuth2AuthorizedClient(true);
      // (optional) set a default ClientRegistration.registrationId
      // oauth.setDefaultClientRegistrationId("client-registration-id");
  //    return WebClient.builder()
  //            .filter(oauth)
  //            .build();
  //}  
  
  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
          OAuth2AuthorizedClientRepository authorizedClientRepository) {

      OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
          .authorizationCode()
          .refreshToken()
          .build();
      DefaultOAuth2AuthorizedClientManager authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
        clientRegistrationRepository, authorizedClientRepository);
      authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

      return authorizedClientManager;
  }  
}
