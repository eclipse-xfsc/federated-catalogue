package eu.xfsc.fc.server.util;


import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.server.model.GraphRebuildRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Slf4j
@Component
@RestControllerEndpoint(id = "graph-rebuild")
@Validated
public class GraphRebuilderEndPoint {

  @Autowired
  private GraphRebuildService graphRebuildService;

  @RequestMapping(method = RequestMethod.POST)
  public ResponseEntity<String> startGraphRebuild(@RequestBody @Valid GraphRebuildRequest grRequest) {
    log.debug("startGraphRebuild.enter; got request: {}", grRequest);
    boolean started = graphRebuildService.triggerRebuild(
        grRequest.getChunkCount(), grRequest.getChunkId(),
        grRequest.getThreads(), grRequest.getBatchSize());
    if (started) {
      return ResponseEntity.ok("graph-rebuild started successfully");
    } else {
      return ResponseEntity.status(HttpStatus.CONFLICT).body("graph-rebuild already in progress");
    }
  }
}

//2023-01-11 10:53:06.212  WARN 1 --- [main] o.s.boot.actuate.endpoint.EndpointId     : Endpoint ID 'graph-rebuild' contains invalid characters, please migrate to a valid format.