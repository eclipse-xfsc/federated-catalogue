package eu.xfsc.fc.server.util;


import eu.xfsc.fc.core.util.GraphRebuilder;
import eu.xfsc.fc.server.model.GraphRebuildRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
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
  private GraphRebuilder graphRebuilder;

  @RequestMapping(method = RequestMethod.POST)
  public ResponseEntity<String> startGraphRebuild(@RequestBody @Valid GraphRebuildRequest grRequest) {
    log.debug("startGraphRebuild.enter; got request: {}", grRequest);
    graphRebuilder.rebuildGraphDb(grRequest.getChunkCount(), grRequest.getChunkId(), grRequest.getThreads(), grRequest.getBatchSize());
    // TODO: return 201 with some address where we could monitor rebuild status later on
    return ResponseEntity.ok("graph-rebuild started successfully");
  }
}

//2023-01-11 10:53:06.212  WARN 1 --- [main] o.s.boot.actuate.endpoint.EndpointId     : Endpoint ID 'graph-rebuild' contains invalid characters, please migrate to a valid format.