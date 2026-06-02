package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceCredentials;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult.IssuerResolutionStatusEnum;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.provenance.ProvenanceCredentialRepository;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.dao.provenance.ProvenanceRecord;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business-logic implementation for provenance credential operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceServiceImpl implements ProvenanceService {

  private final VerificationService verificationService;
  private final ProvenanceCredentialRepository repository;
  private final AssetDao assetDao;
  private final GraphStore graphStore;
  private final ProtectedNamespaceFilter namespaceFilter;
  private final ProvenanceCredentialParser parser;
  private final ProvenanceModelMapper mapper;

  /**
   * {@inheritDoc}
   *
   * <p>Validates, stores, and optionally mirrors the provenance credential as a PROV-O triple.</p>
   */
  @Override
  @Transactional
  public ProvenanceCredential add(String assetId, Integer version, String rawVc, String format) {
    log.debug("add; assetId={}, version={}, format={}", assetId, version, format);

    int resolvedVersion = resolveVersion(assetId, version);
    String expectedSubjectId = assetId + ":v" + resolvedVersion;

    CredentialVerificationResult verificationResult = parser.parseAndValidateVc(rawVc, format);
    ProvenanceCredentialInfo credentialInfo = parser.extractCredentialInfo(rawVc);

    String subjectId = verificationResult.getId();
    String graphSubject = resolveGraphSubject(subjectId, expectedSubjectId, credentialInfo);

    if (credentialInfo.credentialId() != null
        && repository.existsByCredentialId(credentialInfo.credentialId())) {
      throw new ConflictException(
          "Provenance credential already exists: credentialId=" + credentialInfo.credentialId());
    }

    List<RdfClaim> provTriples = ProvOTripleBuilder.buildAll(graphSubject, credentialInfo.facts());
    FilteredClaims filtered = namespaceFilter.filterClaims(provTriples, "provenance add");
    if (filtered.hasWarning()) {
      throw new ClientException(
          "Provenance credential uses the protected CAT namespace. Accepted namespace: "
              + "http://www.w3.org/ns/prov#. Details: " + filtered.warning());
    }

    ProvenanceRecord entity = ProvenanceRecord.builder()
        .assetId(assetId)
        .assetVersion(resolvedVersion)
        .credentialId(credentialInfo.credentialId())
        .issuer(verificationResult.getIssuer())
        .issuedAt(verificationResult.getIssuedDateTime())
        .provenanceType(credentialInfo.primary().type())
        .credentialContent(rawVc)
        .credentialFormat(credentialInfo.formatLabel())
        .build();

    try {
      entity = repository.save(entity);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "Provenance credential already exists: credentialId=" + credentialInfo.credentialId());
    }
    log.info("add; stored provenance credential id={} for asset={} version={}",
        entity.getId(), assetId, resolvedVersion);

    graphStore.addClaims(provTriples, graphSubject);

    return mapper.toModel(entity);
  }

  /**
   * Pick the IRI that anchors the projected PROV-O triples for this credential.
   *
   * <p>Two shapes are accepted:
   * <ul>
   *   <li><b>Entity-centric</b> — {@code credentialSubject.id == expectedSubjectId}
   *       ({@code assetId:vN}); the versioned-asset IRI is the graph subject. This is the
   *       backwards-compatible shape used by the simple "fact about a version" credential.</li>
   *   <li><b>Activity-centric</b> — {@code credentialSubject.id} is the activity's own IRI; the
   *       credential must declare {@code prov:generated} or {@code prov:used} (compact or
   *       expanded) pointing at {@code expectedSubjectId} so the activity can be linked back to a
   *       versioned asset. The activity IRI becomes the graph subject and the projected triples
   *       form the activity's star of relations.</li>
   * </ul>
   */
  private String resolveGraphSubject(String subjectId, String expectedSubjectId,
                                      ProvenanceCredentialInfo credentialInfo) {
    if (expectedSubjectId.equals(subjectId)) {
      return expectedSubjectId;
    }
    boolean linksToVersionedAsset = credentialInfo.facts().stream()
        .anyMatch(fact -> (fact.type() == ProvenanceType.GENERATION
                          || fact.type() == ProvenanceType.USAGE)
                          && expectedSubjectId.equals(fact.objectValue()));
    if (!linksToVersionedAsset) {
      throw new ClientException(
          "credentialSubject.id '" + subjectId + "' must either equal '" + expectedSubjectId
              + "' (entity-centric form) or declare prov:generated/prov:used pointing at '"
              + expectedSubjectId + "' (activity-centric form).");
    }
    return subjectId;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public ProvenanceCredentials list(String assetId, Integer version, Pageable pageable) {
    log.debug("list; assetId={}, version={}", assetId, version);
    requireAssetExists(assetId);

    Page<ProvenanceRecord> page;
    if (version != null) {
      page = repository.findByAssetIdAndAssetVersionOrderByIssuedAtDesc(assetId, version, pageable);
    } else {
      page = repository.findByAssetIdOrderByIssuedAtDesc(assetId, pageable);
    }

    List<ProvenanceCredential> items = page.getContent().stream()
        .map(mapper::toModel)
        .toList();

    return new ProvenanceCredentials((int) page.getTotalElements(), items);
  }

  /** {@inheritDoc} */
  @Override
  @Transactional(readOnly = true)
  public ProvenanceCredential get(String assetId, String credentialId) {
    log.debug("get; assetId={}, credentialId={}", assetId, credentialId);
    return mapper.toModel(requireCredentialBelongsToAsset(assetId, credentialId));
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ProvenanceVerificationResult verifyOne(String assetId, String credentialId) {
    log.debug("verifyOne; assetId={}, credentialId={}", assetId, credentialId);
    ProvenanceRecord entity = requireCredentialBelongsToAsset(assetId, credentialId);

    ProvenanceVerificationResult result = verifyEntity(entity);
    persistVerificationResult(entity, result);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  @Transactional
  public ProvenanceVerificationResult verifyAll(String assetId, Integer version) {
    log.debug("verifyAll; assetId={}, version={}", assetId, version);
    requireAssetExists(assetId);

    List<ProvenanceRecord> entities;
    if (version != null) {
      entities = repository.findByAssetIdAndAssetVersionOrderByIssuedAtDesc(
          assetId, version, Pageable.unpaged()).getContent();
    } else {
      entities = repository.findByAssetIdOrderByIssuedAtDesc(assetId, Pageable.unpaged()).getContent();
    }

    boolean allValid = true;
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Instant latestTimestamp = null;

    for (ProvenanceRecord entity : entities) {
      ProvenanceVerificationResult singleResult = verifyEntity(entity);
      mapper.applyVerificationResult(entity, singleResult);

      if (Boolean.FALSE.equals(singleResult.getIsValid())) {
        allValid = false;
        if (singleResult.getErrors() != null) {
          singleResult.getErrors().forEach(e ->
              errors.add("[" + entity.getCredentialId() + "] " + e));
        }
      }
      if (singleResult.getWarnings() != null) {
        singleResult.getWarnings().forEach(w ->
            warnings.add("[" + entity.getCredentialId() + "] " + w));
      }
      Instant ts = singleResult.getVerificationTimestamp();
      if (ts != null && (latestTimestamp == null || ts.isAfter(latestTimestamp))) {
        latestTimestamp = ts;
      }
    }

    repository.saveAll(entities);

    return new ProvenanceVerificationResult()
        .isValid(allValid)
        .verificationTimestamp(latestTimestamp)
        .validatorDids(List.of())
        .errors(errors)
        .warnings(warnings);
  }

  private int resolveVersion(String assetId, Integer requestedVersion) {
    if (requestedVersion != null) {
      Optional<AssetRecord> record = assetDao.selectVersion(assetId, requestedVersion);
      if (record.isEmpty()) {
        throw new NotFoundException("Asset not found: assetId=" + assetId + ", version=" + requestedVersion);
      }
      return requestedVersion;
    }
    int count = assetDao.getVersionCount(assetId);
    if (count == 0) {
      throw new NotFoundException("Asset not found: assetId=" + assetId);
    }
    return count;
  }

  private void requireAssetExists(String assetId) {
    if (assetDao.getVersionCount(assetId) == 0) {
      throw new NotFoundException("Asset not found: assetId=" + assetId);
    }
  }

  private ProvenanceRecord findByCredentialId(String credentialId) {
    return repository.findByCredentialId(credentialId)
        .orElseThrow(() -> new NotFoundException(
            "Provenance credential not found: credentialId=" + credentialId));
  }

  private ProvenanceRecord requireCredentialBelongsToAsset(String assetId, String credentialId) {
    requireAssetExists(assetId);
    ProvenanceRecord entity = findByCredentialId(credentialId);
    if (!entity.getAssetId().equals(assetId)) {
      throw new NotFoundException(
          "Provenance credential not found for asset: credentialId=" + credentialId);
    }
    return entity;
  }

  private ProvenanceVerificationResult verifyEntity(ProvenanceRecord entity) {
    Instant now = Instant.now();
    try {
      ContentAccessorDirect content = new ContentAccessorDirect(entity.getCredentialContent());
      CredentialVerificationResult result = verificationService.verifyCredential(content, false);

      return new ProvenanceVerificationResult()
          .isValid(true)
          .verificationTimestamp(now)
          .issuerResolutionStatus(IssuerResolutionStatusEnum.RESOLVED)
          .issuedDateTime(result.getIssuedDateTime())
          .signatureValid(true)
          .validatorDids(result.getValidatorDids() != null ? result.getValidatorDids() : List.of())
          .errors(List.of())
          .warnings(result.getWarnings() != null ? result.getWarnings() : List.of());

    } catch (VerificationException ex) {
      log.warn("verifyEntity; verification failed for credentialId={}: {}",
          entity.getCredentialId(), ex.getMessage());
      return new ProvenanceVerificationResult()
          .isValid(false)
          .verificationTimestamp(now)
          .issuerResolutionStatus(IssuerResolutionStatusEnum.UNRESOLVABLE)
          .signatureValid(false)
          .validatorDids(List.of())
          .errors(List.of(ex.getMessage()))
          .warnings(List.of());
    }
  }

  private void persistVerificationResult(ProvenanceRecord entity, ProvenanceVerificationResult result) {
    mapper.applyVerificationResult(entity, result);
    repository.save(entity);
  }

  @Override
  @Transactional
  public void deleteByAssetId(String assetId) {
    // Provenance triples are written under the credential's resolved graph subject IRI — either
    // the versioned-asset IRI {assetId}:v{N} (entity-centric credentials) or the activity's own
    // IRI (activity-centric credentials). Cleanup re-parses each stored credential to recover the
    // exact graph subject so both shapes are reachable; the versioned-asset IRI is also deleted
    // unconditionally so any historical entity-centric projection is removed even when no
    // relational row survives to point at it.
    List<ProvenanceRecord> records = repository
        .findByAssetIdOrderByIssuedAtDesc(assetId, Pageable.unpaged()).getContent();
    Set<String> subjectsToClear = new LinkedHashSet<>();
    for (ProvenanceRecord record : records) {
      subjectsToClear.add(assetId + ":v" + record.getAssetVersion());
      try {
        ProvenanceCredentialInfo info = parser.extractCredentialInfo(record.getCredentialContent());
        String subjectId = parser.extractCredentialSubjectId(record.getCredentialContent());
        if (subjectId != null) {
          subjectsToClear.add(subjectId);
        }
        // Also delete the link targets that the credential pointed at — for an activity-centric
        // credential these are the versioned-asset IRIs already covered above, but the parsed
        // facts are the authoritative source if the relational row's version drifts.
        info.facts().forEach(fact -> {
          if (fact.type() == ProvenanceType.GENERATION || fact.type() == ProvenanceType.USAGE) {
            subjectsToClear.add(fact.objectValue());
          }
        });
      } catch (RuntimeException ex) {
        log.warn("deleteByAssetId; could not re-parse stored credential {} — graph cleanup may "
            + "leave triples under the activity IRI", record.getCredentialId(), ex);
      }
    }
    for (String subject : subjectsToClear) {
      graphStore.deleteClaims(subject);
    }
    repository.deleteByAssetId(assetId);
  }

}
