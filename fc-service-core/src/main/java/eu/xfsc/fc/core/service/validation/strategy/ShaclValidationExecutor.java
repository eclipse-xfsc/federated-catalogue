package eu.xfsc.fc.core.service.validation.strategy;

import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.TimeoutException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.validation.ValidationUtil;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Executes TopBraid SHACL validation with a configurable timeout.
 *
 * <p>Owns an application-scoped thread pool to isolate SHACL execution from the calling
 * thread and enforce a hard timeout via {@link Future#get(long, TimeUnit)}.</p>
 */
@Slf4j
@Service
public class ShaclValidationExecutor {

  @Value("${federated-catalogue.validation.shacl.timeout-seconds:10}")
  private int shaclTimeoutSeconds;

  @Value("${federated-catalogue.validation.shacl.pool-size:4}")
  private int shaclPoolSize;

  // Application-scoped pool — avoids per-call thread creation; threads are reused across requests.
  private ExecutorService executorService;

  @PostConstruct
  void init() {
    executorService = Executors.newFixedThreadPool(shaclPoolSize);
  }

  @PreDestroy
  void shutdown() throws InterruptedException {
    executorService.shutdown();
    if (!executorService.awaitTermination(shaclTimeoutSeconds + 5L, TimeUnit.SECONDS)) {
      executorService.shutdownNow();
    }
  }

  /**
   * Validates {@code dataModel} against {@code shapesModel} using TopBraid SHACL.
   *
   * @param dataModel   the RDF data graph to validate
   * @param shapesModel the SHACL shapes graph
   * @return the {@code sh:ValidationReport} resource
   * @throws TimeoutException if validation exceeds the configured timeout
   * @throws ServerException  on execution failure or thread interruption
   */
  public Resource validate(Model dataModel, Model shapesModel) {
    Future<Resource> future = executorService.submit(
        () -> ValidationUtil.validateModel(dataModel, shapesModel, true));
    try {
      return future.get(shaclTimeoutSeconds, TimeUnit.SECONDS);
    } catch (java.util.concurrent.TimeoutException e) {
      future.cancel(true);
      throw new TimeoutException(
          "SHACL validation timed out after " + shaclTimeoutSeconds + " seconds");
    } catch (ExecutionException e) {
      throw new ServerException(
          "SHACL validation failed: " + e.getCause().getMessage(), e.getCause());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ServerException("SHACL validation interrupted", e);
    }
  }
}
