package eu.xfsc.fc.core.service.verification;

import com.danubetech.verifiablecredentials.CredentialSubject;
import com.danubetech.verifiablecredentials.VerifiableCredential;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import eu.xfsc.fc.core.exception.VerificationException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Value object holding the typed credentials parsed from a VP or VC payload.
 * Pure data: no service dependencies, no Spring wiring.
 */
@Slf4j
@RequiredArgsConstructor
class TypedCredentials {

  private final VerifiablePresentation presentation;
  private final Map<VerifiableCredential, TrustFrameworkBaseClass> credentials;

  private VerifiableCredential getFirstVC() {
    return credentials.isEmpty() ? null : credentials.keySet().iterator().next();
  }

  Collection<TrustFrameworkBaseClass> getBaseClasses() {
    return credentials.values().stream()
        .filter(bc -> bc != TrustFrameworkBaseClass.UNKNOWN).distinct().toList();
  }

  Collection<VerifiableCredential> getCredentials() {
    return credentials.keySet();
  }

  String getHolder() {
    if (presentation == null) {
      return null;
    }
    URI holder = presentation.getHolder();
    if (holder == null) {
      return null;
    }
    return holder.toString();
  }

  String getID() {
    VerifiableCredential first = getFirstVC();
    if (first == null) {
      return null;
    }

    List<CredentialSubject> subjects = getSubjects(first);
    if (subjects.isEmpty()) {
      return getID(first.getJsonObject());
    }
    return getID(subjects.getFirst().getJsonObject());
  }

  String getIssuer() {
    VerifiableCredential first = getFirstVC();
    if (first == null) {
      return null;
    }
    URI issuer = first.getIssuer();
    if (issuer == null) {
      return null;
    }
    return issuer.toString();
  }

  Instant getIssuanceDate() {
    VerifiableCredential first = getFirstVC();
    if (first == null) {
      return null;
    }
    Date issDate = first.getIssuanceDate();
    if (issDate == null) {
      // VC 2.0 uses "validFrom" instead of "issuanceDate"
      Object validFrom = first.getJsonObject().get("validFrom");
      if (validFrom != null) {
        return Instant.parse(validFrom.toString());
      }
      return null;
    }
    return issDate.toInstant();
  }

  VerifiablePresentation getPresentation() {
    return presentation;
  }

  boolean hasClasses() {
    return credentials.values().stream().anyMatch(bc -> bc != TrustFrameworkBaseClass.UNKNOWN);
  }

  private List<CredentialSubject> getSubjects(VerifiableCredential credential) {
    Object obj = credential.getJsonObject().get("credentialSubject");
    return switch (obj) {
      case null -> Collections.emptyList();
      case List<?> list -> {
        List<CredentialSubject> result = new ArrayList<>(list.size());
        int idx = 0;
        for (Object item : list) {
          Map<String, Object> subjectMap = toStringKeyMap(item, "credentialSubject[" + idx + "]");
          result.add(CredentialSubject.fromMap(subjectMap));
          idx++;
        }
        yield result;
      }
      case Map<?, ?> map -> {
        Map<String, Object> subjectMap = toStringKeyMap(map, "credentialSubject");
        yield List.of(CredentialSubject.fromMap(subjectMap));
      }
      default -> throw new VerificationException(
          "Semantic error: credentialSubject must be an object or array of objects");
    };
  }

  private Map<String, Object> toStringKeyMap(Object value, String context) {
    if (!(value instanceof Map<?, ?> rawMap)) {
      throw new VerificationException("Semantic error: " + context + " must be an object");
    }
    Map<String, Object> map = new LinkedHashMap<>(rawMap.size());
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new VerificationException("Semantic error: " + context + " must use string keys");
      }
      map.put(key, entry.getValue());
    }
    return map;
  }

  private String getID(Map<String, Object> map) {
    Object id = map.get("id");
    if (id != null) {
      return id.toString();
    }
    id = map.get("@id");
    if (id != null) {
      return id.toString();
    }
    return null;
  }
}
