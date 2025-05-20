package eu.xfsc.fc.server.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
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
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.api.generated.model.Results;
import eu.xfsc.fc.api.generated.model.Statement;
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
  private QueryProperties queryProps;
  
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
  
  /**
   * Get List of results from catalogue for provided {@link Statement}.
   *
   * @param queryLanguage  (required) Language for query the results like openCypher etc.
   * @param statement JSON object to send queries. Use \&quot;application/json\&quot; for openCypher queries.
   *                   A Catalogue may also support the other content types depending on its supported query languages
   *                   but only \&quot;application/json\&quot; is mandatory. (optional)
   * @return List of {@link Results}
   */
  @Override
  public ResponseEntity<Results> query(QueryLanguage queryLanguage, Integer timeout, Boolean withTotalCount, Statement statement) {
    log.debug("query.enter; got queryLanguage: {}, timeout: {}, withTotalCount: {}, statement: {}", queryLanguage, timeout, withTotalCount, statement);
    if (checkIfLimitAbsent(statement.getStatement())) {
      addDefaultLimit(statement);
    }
    PaginatedResults<Map<String, Object>> queryResultList = graphStore.queryData(new GraphQuery(statement.getStatement(), 
            statement.getParameters(), queryLanguage, timeout, withTotalCount));
    Results result = new Results((int) queryResultList.getTotalCount(), queryResultList.getResults());
    log.debug("query.exit; returning results: {}", result);
    return ResponseEntity.ok(result);
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
      log.error("queryPage; error in getting file: {}", e);
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
   * Adding default limit for the query if not present.
   *
   * @param statement Query Statement
   */
  private void addDefaultLimit(Statement statement) {
    String appendLimit = " limit $limit";
    statement.setStatement(statement.getStatement().concat(appendLimit));
    if (null == statement.getParameters()) {
      statement.setParameters(Map.of("limit", DEFAULT_LIMIT));
    } else {
      statement.getParameters().putIfAbsent("limit", DEFAULT_LIMIT);
    }
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