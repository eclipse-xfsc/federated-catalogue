package eu.xfsc.fc.server.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.AnnotatedStatement;
import eu.xfsc.fc.api.generated.model.QueryInfo;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.api.generated.model.Results;
import eu.xfsc.fc.client.QueryClient;
import eu.xfsc.fc.server.config.QueryProperties;
import eu.xfsc.fc.server.generated.controller.QueryApiDelegate;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for query the catalogue. Implementation of the {@link QueryApiDelegate} .
 */
@Slf4j
@Service
public class QueryService implements QueryApiDelegate {
	
  private static int DEFAULT_LIMIT = 100;	
  
  @Autowired
  private GraphStore graphStore;
  @Autowired
  private ObjectMapper jsonMapper;
  @Autowired
  private ResourceLoader resourceLoader;
  @Autowired
  private QueryLanguageValidator queryLanguageValidator;

  @Autowired
  private QueryProperties queryProps;

  @Autowired
  private HttpServletRequest httpServletRequest;

  private List<QueryClient> queryClients;
  
  @PostConstruct
  public void initClients() {
	log.debug("initClients.enter; props are: {}", queryProps);
	if (!queryProps.getPartners().isEmpty()) {
		queryClients = queryProps.getPartners().stream().map(pAddr -> new QueryClient(pAddr, webClient(pAddr))).collect(Collectors.toList());
	}
	log.debug("initClients.exit; initiated clients: {}", queryClients);
  }
  
  private WebClient webClient(String fcUri) {
      
      return WebClient.builder()
        .baseUrl(fcUri)
        .codecs(configurer -> {
            configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(jsonMapper, MediaType.APPLICATION_JSON));
            configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(jsonMapper, MediaType.APPLICATION_JSON));
        })
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
  
  /** W3C SPARQL 1.1 Results JSON media type — selected via the {@code Accept} header. */
  public static final String SPARQL_RESULTS_JSON_VALUE = "application/sparql-results+json";
  private static final MediaType SPARQL_RESULTS_JSON =
      MediaType.parseMediaType(SPARQL_RESULTS_JSON_VALUE);

  /**
   * Get List of results from catalogue for provided raw query text. The query language is
   * determined from the {@code Content-Type} header; the response shape is negotiated via
   * the {@code Accept} header.
   *
   * <p>When the client lists {@code application/sparql-results+json} in the {@code Accept}
   * header and the active graph store is SPARQL-capable, the response is the W3C SPARQL
   * 1.1 Results JSON document ({@code head.vars} / {@code results.bindings} with typed
   * value entries) as defined in https://www.w3.org/TR/sparql11-results-json/.
   *
   * <p>Otherwise the legacy {@link Results} envelope ({@code totalCount} + flat
   * {@code items} list of variable→value maps) is returned under
   * {@code application/json}. If the W3C representation is requested against a
   * non-SPARQL backend, the response is {@code 406 Not Acceptable}.
   *
   * @param body raw query text
   * @param timeout query timeout in seconds
   * @param withTotalCount whether to include total count (ignored for the W3C envelope)
   * @return the negotiated response envelope; the runtime body type is {@link Results}
   *         for the legacy envelope and {@link String} for the W3C envelope
   */
  @Override
  @SuppressWarnings("unchecked") // Generic body widened at runtime to honour Accept negotiation:
                                 // the W3C path returns a String body, the legacy path Results.
  public ResponseEntity<Results> query(String body, Integer timeout, Boolean withTotalCount) {
    String contentType = httpServletRequest.getContentType();
    String acceptHeader = httpServletRequest.getHeader(HttpHeaders.ACCEPT);
    QueryLanguage queryLanguage = QueryLanguageProperties.fromContentType(contentType);
    log.debug("query.enter; got contentType: {}, accept: {}, queryLanguage: {}, timeout: {}, withTotalCount: {}, body: {}",
        contentType, acceptHeader, queryLanguage, timeout, withTotalCount, body);
    queryLanguageValidator.validateLanguageSupport(queryLanguage);
    String queryText = body;
    if (checkIfLimitAbsent(queryText)) {
      queryText = queryText + " LIMIT " + DEFAULT_LIMIT;
    }
    GraphQuery graphQuery = new GraphQuery(queryText, null, queryLanguage, timeout, withTotalCount);

    if (clientPrefersSparqlResultsJson(acceptHeader)) {
      Optional<String> sparqlResultsJson = graphStore.queryDataAsSparqlResultsJson(graphQuery);
      if (sparqlResultsJson.isEmpty()) {
        return (ResponseEntity) ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new eu.xfsc.fc.api.generated.model.Error("unsupported_accept_header",
                "The active graph store does not support the " + SPARQL_RESULTS_JSON_VALUE
                    + " response format. Use Accept: " + MediaType.APPLICATION_JSON_VALUE
                    + " instead."));
      }
      log.debug("query.exit; returning W3C SPARQL Results JSON envelope");
      return (ResponseEntity) ResponseEntity.ok()
          .contentType(SPARQL_RESULTS_JSON)
          .body(sparqlResultsJson.get());
    }

    PaginatedResults<Map<String, Object>> queryResultList = graphStore.queryData(graphQuery);
    Results result = new Results((int) queryResultList.getTotalCount(), queryResultList.getResults());
    log.debug("query.exit; returning legacy results envelope: {}", result);
    return ResponseEntity.ok(result);
  }

  /**
   * Returns {@code true} when the supplied {@code Accept} header explicitly lists the
   * W3C SPARQL Results JSON media type. Parses comma-separated values with q-weights via
   * {@link MediaType#parseMediaTypes(String)}; the W3C format is selected only when the
   * client mentions it by name (a generic {@code application/json} or {@code *}/{@code *}
   * keeps the legacy envelope as the backward-compatible default).
   *
   * @param acceptHeader the raw {@code Accept} header value (may be {@code null})
   * @return whether the W3C envelope should be returned
   */
  private static boolean clientPrefersSparqlResultsJson(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.isBlank()) {
      return false;
    }
    try {
      return MediaType.parseMediaTypes(acceptHeader).stream()
          .anyMatch(SPARQL_RESULTS_JSON::equalsTypeAndSubtype);
    } catch (InvalidMediaTypeException ex) {
      log.debug("query; ignoring malformed Accept header '{}'", acceptHeader, ex);
      return false;
    }
  }
  
  
  /**
   * {@inheritDoc}
   */
  @Override
  public ResponseEntity<String> querywebsite() {
    log.debug("queryPage.enter");

    final Resource resource = resourceLoader.getResource("classpath:static/query.html");
    String page;
    try {
      Reader reader = new InputStreamReader(resource.getInputStream());
      page = FileCopyUtils.copyToString(reader);
    } catch (IOException e) {
      log.error("queryPage; error in getting file: {}", e, e.getMessage(), e);
      throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.set("Content-Type", "text/html");
    log.debug("queryPage.exit; returning page");
    return ResponseEntity.ok()
        .headers(responseHeaders)
        .body(page);
  }

  /**
   * Returns information about the active query backend including supported language,
   * content type, example query, and documentation link.
   *
   * @return {@link QueryInfo} with backend capabilities
   */
  @Override
  public ResponseEntity<QueryInfo> queryInfo() {
    log.debug("queryInfo.enter");
    Optional<QueryLanguage> supported = graphStore.getSupportedQueryLanguage();
    QueryInfo info = new QueryInfo();
    info.setBackend(graphStore.getBackendType().name());
    info.setEnabled(supported.isPresent());
    supported.ifPresent(lang -> {
      QueryLanguageProperties props = QueryLanguageProperties.of(lang);
      info.setQueryLanguage(QueryLanguage.fromValue(lang.name()));
      info.setContentType(props.contentType());
      info.setExampleQuery(props.exampleQuery());
      info.setDocumentation(props.documentationUrl());
    });
    log.debug("queryInfo.exit; returning: {}", info);
    return ResponseEntity.ok(info);
  }

  /**
   * performs distributed search
   */
  @Override
  public ResponseEntity<Results> search(AnnotatedStatement statement) {
	log.debug("search.enter; got statement: {}", statement);
	if (checkIfLimitAbsent(statement.getStatement())) {
	  statement.setStatement(statement.getStatement() + " limit $limit");
	  statement.putParametersItem("limit", DEFAULT_LIMIT);
	}
	boolean first = statement.getServers() == null || statement.getServers().isEmpty();
	Mono<List<Results>> extra = searchPartners(statement);
	    
	String queryLanguage = getAnnotation(statement, "queryLanguage", QueryLanguage.OPENCYPHER.name());
	Integer timeout = getAnnotation(statement, "timeout", GraphQuery.QUERY_TIMEOUT);
	Boolean withTotalCount = getAnnotation(statement, "withTotalCount", true);

	queryLanguageValidator.validateLanguageSupport(QueryLanguage.valueOf(queryLanguage));
	PaginatedResults<Map<String, Object>> queryResultList = graphStore.queryData(new GraphQuery(statement.getStatement(),
	        statement.getParameters(), QueryLanguage.valueOf(queryLanguage), timeout, withTotalCount));
	Results result = new Results((int) queryResultList.getTotalCount(), queryResultList.getResults());
	if (extra != null) {
	  //extra.subscribe();
	  result = mergePartnerResults(first, result, extra.block());
	}
	log.debug("search.exit; returning results: {}", result);
	return ResponseEntity.ok(result);
  }
  
  private <T> T getAnnotation(AnnotatedStatement statement, String name, T defaultValue) {
	if (statement.getAnnotations() == null) {
	  return defaultValue;
	}
	T value = (T) statement.getAnnotations().get(name);
	if (value == null) {
	  return defaultValue;
	}
	return value;
  }

  /**
   * Check if limit is present or not in query.
   *
   * @param statement Query Statement
   * @return boolean match status
   */
  private boolean checkIfLimitAbsent(String query) {
	if (query.toLowerCase().indexOf("return") > 0) {
      String subItem = "limit";
      String pattern = "(?m)(^|\\s)" + subItem + "(\\s|$)";
      Pattern p = Pattern.compile(pattern);
      Matcher m = p.matcher(query.toLowerCase());
      return !m.find();
	}
	return false;
  }
  
  private Mono<List<Results>> searchPartners(AnnotatedStatement statement) {
	if (queryClients != null) {
	  Set<String> route = new HashSet<>();
	  if (statement.getServers() == null) {
		statement.setServers(new HashSet<>());  
	  } else {
		route.addAll(statement.getServers());
	  }
	  route.add(queryProps.getSelf());
	  statement.getServers().addAll(queryProps.getPartners());
	  statement.addServersItem(queryProps.getSelf());
	  
	  return Flux.fromIterable(queryClients).flatMap(c -> {
		  	  if (!route.contains(c.getUrl())) {
		  	    return c.searchAsync(statement);
		  	  }
		  	  return Mono.empty();
	  	  })
		  .onErrorContinue((ex, o) -> {
		      log.debug("queryPartners.error; with object: {}", o, ex);
	  	  })
		  .collectList();
	} 
	return null;
  }
  
  private Results mergePartnerResults(boolean first, Results local, List<Results> extra) {
	Results results = new Results(0, new ArrayList<>());	
	if (!extra.isEmpty()) {
	  Set<String> urls = new HashSet<>(extra.size());
	  extra.stream().forEach(r -> {
	    // check extra keys for duplicate urls..
		r.getItems().stream().forEach(m -> {
			String server = (String) m.get("server");
			List<Map<String, Object>> items = (List<Map<String, Object>>) m.get("items");
			if (server != null && items != null && !urls.contains(server)) {
				urls.add(server);
				Integer total = (Integer) m.get("total");
				if (first) {
					results.getItems().addAll(items);
				} else {
					results.addItemsItem(m);
				}
				results.setTotalCount(results.getTotalCount() + total);
			}
		});
	  }); 
	}
	if (first) {
		results.getItems().addAll(local.getItems());
	} else {
		results.addItemsItem(Map.of("server", queryProps.getSelf(), "total", local.getTotalCount(), "items", local.getItems()));
	}
	results.setTotalCount(results.getTotalCount() + local.getTotalCount());
    return results;
  }
    
}