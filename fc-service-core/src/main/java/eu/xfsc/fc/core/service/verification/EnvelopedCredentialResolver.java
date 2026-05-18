package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.DATA_URI_PREFIX;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.EVC_TYPE;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.EVP_TYPE;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.JWT_PREFIX;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VERIFIABLE_CREDENTIAL_KEY;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VP_TYPE;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.danubetech.verifiablecredentials.VerifiableCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.core.util.FormatDetectionConstants;
import foundation.identity.jsonld.JsonLDObject;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Owns all W3C VC 2.0 Enveloped Credential plumbing:
 * <ul>
 *   <li>detecting and unwrapping outer {@code EnvelopedVerifiableCredential} /
 *       {@code EnvelopedVerifiablePresentation} wrappers,</li>
 *   <li>rewriting a VP's {@code verifiableCredential} array so inner EVC entries become
 *       inline JSON-LD VCs (for downstream claim extraction),</li>
 *   <li>unwrapping nested JWT VCs and EVC entries to {@link VerifiableCredential}s, and</li>
 *   <li>verifying signatures of inner VC entries when the outer is a VP JWT.</li>
 * </ul>
 *
 * <p>Format-specific JWT unwrapping is delegated to the matching
 * {@link CredentialFormatProcessor#unwrapNested}; this resolver only handles the EVC/JWT
 * envelope mechanics.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnvelopedCredentialResolver {

  private final ObjectMapper objectMapper;
  private final CredentialFormatDetector formatDetector;
  private final JwtSignatureVerifier jwtSignatureVerifier;
  private final List<CredentialFormatProcessor> formatProcessors;

  private Map<CredentialFormat, CredentialFormatProcessor> processorsByFormat;

  @PostConstruct
  void indexProcessors() {
    processorsByFormat = new EnumMap<>(CredentialFormat.class);
    for (CredentialFormatProcessor p : formatProcessors) {
      processorsByFormat.put(p.getFormat(), p);
    }
  }

  /**
   * Resolves the {@code type} or {@code @type} value from a JSON-LD map.
   * @param map the JSON-LD map to resolve from
   * @return the resolved type value, or null if neither key is present
   */
  public static @Nullable Object resolveType(Map<?, ?> map) {
    Object val = map.get("type");
    return val != null ? val : map.get("@type");
  }

  /**
   * Resolves the {@code id} or {@code @id} value from a JSON-LD map.
   * @param map the JSON-LD map to resolve from
   * @return the resolved ID value, or null if neither key is present
   */
  public static @Nullable Object resolveId(Map<?, ?> map) {
    Object val = map.get("id");
    return val != null ? val : map.get("@id");
  }

  /**
   * Returns true if the given JSON-LD entry is an {@code EnvelopedVerifiableCredential} with
   * the VC 2.0 context. The context check avoids false-positives from other vocabularies that
   * happen to use the same type name.
   *
   * @param typeObj the value of the entry's {@code type} or {@code @type} field
   * @param contextObj the value of the entry's {@code @context} field
   * @return true if the entry is an EVC with the correct context, false otherwise
   */
  public static boolean isEnvelopedVerifiableCredential(Object typeObj, Object contextObj) {
    boolean hasType = false;
    if (typeObj instanceof String typeStr) {
      hasType = EVC_TYPE.equals(typeStr);
    } else if (typeObj instanceof List<?> types) {
      hasType = types.contains(EVC_TYPE);
    }
    if (!hasType) {
      return false;
    }
    if (contextObj instanceof String ctx) {
      return VC_20_CONTEXT.equals(ctx);
    }
    if (contextObj instanceof List<?> contexts) {
      return contexts.contains(VC_20_CONTEXT);
    }
    return false;
  }

  /**
   * If {@code body} is a VC 2.0 EnvelopedVerifiableCredential or EnvelopedVerifiablePresentation
   * JSON-LD wrapper, extracts and returns the inner JWT from the {@code id} data: URI.
   * Returns {@code null} otherwise.
   *
   * @param body the JSON-LD string to inspect and extract from
   * @return the extracted JWT string if the body is a valid EVC/EVP wrapper, or null if not
   * @throws ClientException if the body is a valid wrapper but its data: URI is malformed
   */
  @SuppressWarnings("unchecked") // objectMapper.readValue with Map.class returns raw Map type
  public @Nullable String extractEnvelopedJwt(String body) {
    if (!body.startsWith(FormatDetectionConstants.JSON_LD_PREFIX)) {
      return null;
    }
    try {
      Map<String, Object> json = objectMapper.readValue(body, Map.class);
      Object typeObj = resolveType(json);
      boolean hasEnvelopedType;
      if (typeObj instanceof String s) {
        hasEnvelopedType = EVC_TYPE.equals(s) || EVP_TYPE.equals(s);
      } else if (typeObj instanceof List<?> types) {
        hasEnvelopedType = types.contains(EVC_TYPE) || types.contains(EVP_TYPE);
      } else {
        return null;
      }
      if (!hasEnvelopedType) {
        return null;
      }
      Object ctxObj = json.get(RDF_CONTEXT_KEY);
      boolean hasVc20Context = (ctxObj instanceof String ctx && VC_20_CONTEXT.equals(ctx))
          || (ctxObj instanceof List<?> ctxs && ctxs.contains(VC_20_CONTEXT));
      if (!hasVc20Context) {
        return null;
      }
      String resolvedType = typeObj instanceof String s
          ? (EVC_TYPE.equals(s) ? EVC_TYPE : EVP_TYPE)
          : (((List<?>) typeObj).contains(EVC_TYPE) ? EVC_TYPE : EVP_TYPE);
      Object idObj = resolveId(json);
      if (!(idObj instanceof String id) || !id.startsWith(DATA_URI_PREFIX)) {
        throw new ClientException(
            resolvedType + ": 'id' must be a data: URI; got: " + idObj);
      }
      return extractJwtFromDataUri(id, resolvedType);
    } catch (ClientException e) {
      throw e;
    } catch (Exception e) {
      log.debug("extractEnvelopedJwt; not a valid EVC/EVP wrapper: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * Resolves {@code EnvelopedVerifiableCredential} entries inside a VP's
   * {@code verifiableCredential} array. Each EVC's {@code data:} URI is parsed,
   * the inner JWT payload is extracted, and the EVC entry is replaced with the
   * unwrapped VC JSON-LD. Non-VP payloads and VPs without EVCs pass through unchanged.
   *
   * @param payload the incoming credential content to inspect and potentially rewrite
   * @return a ContentAccessor with EVC entries resolved to inline JSON-LD, or
   * the original payload if no EVC entries are found or if the payload is not a VP JSON-LD document
   * @throws ClientException if any EVC entries are malformed (e.g. missing data: URI or invalid JWT)
   * @throws VerificationException if any inner JWT VCs are malformed or fail to parse as valid VC JSON-LD documents
   * @throws RuntimeException for unexpected errors during JSON parsing or processing
   */
  @SuppressWarnings("unchecked") // objectMapper.readValue with Map.class returns raw Map type
  public ContentAccessor resolveInnerEnvelopedCredentials(ContentAccessor payload) {
    String json = payload.getContentAsString();
    if (!json.startsWith(FormatDetectionConstants.JSON_LD_PREFIX)) {
      return payload;
    }
    try {
      Map<String, Object> root = objectMapper.readValue(json, Map.class);
      Object typeObj = resolveType(root);
      boolean isVp = typeObj instanceof List<?> types
          ? types.contains(VP_TYPE) : VP_TYPE.equals(typeObj);
      if (!isVp) {
        return payload;
      }

      Object vcObj = root.get(VERIFIABLE_CREDENTIAL_KEY);
      if (!(vcObj instanceof List<?> vcList) || vcList.isEmpty()) {
        return payload;
      }

      boolean modified = false;
      List<Object> resolved = new ArrayList<>(vcList.size());
      for (Object entry : vcList) {
        if (entry instanceof Map<?, ?> vcMap) {
          Object entryType = resolveType(vcMap);
          Object entryCtx = vcMap.get(RDF_CONTEXT_KEY);
          if (isEnvelopedVerifiableCredential(entryType, entryCtx)) {
            Object idObj = resolveId(vcMap);
            Map<String, Object> unwrapped = unwrapEnvelopedVcJwt(idObj);
            if (unwrapped != null) {
              resolved.add(unwrapped);
              modified = true;
              continue;
            }
          }
        }
        resolved.add(entry);
      }
      if (!modified) {
        return payload;
      }
      root.put(VERIFIABLE_CREDENTIAL_KEY, resolved);
      return new ContentAccessorDirect(objectMapper.writeValueAsString(root));
    } catch (Exception e) {
      log.debug("resolveInnerEnvelopedCredentials; pass-through: {}", e.getMessage(), e);
      return payload;
    }
  }

  /**
   * Attempts to unwrap a compact JWT VC string to a {@link VerifiableCredential} for
   * semantic processing. Inner is always a compact JWT; falls back to the VC 2.0 unwrap
   * when the format is UNKNOWN so legacy/unrecognised JWTs can still be parsed.
   * Returns {@code null} when unwrapping fails.
   *
   * @param jwtStr the compact JWT string to unwrap
   * @param docLoader the DocumentLoader to use for JSON-LD processing of the inner VC
   * @return the unwrapped VerifiableCredential, or null if unwrapping fails
   * @throws ClientException if the JWT is malformed or the unwrapped content is not a valid VC JSON-LD document
   */
  public @Nullable VerifiableCredential tryUnwrapJwtVc(String jwtStr, DocumentLoader docLoader) {
    if (!jwtStr.startsWith(JWT_PREFIX)) {
      return null;
    }
    try {
      ContentAccessor jwtContent = new ContentAccessorDirect(jwtStr);
      CredentialFormat innerFormat = formatDetector.detect(jwtContent);
      CredentialFormatProcessor innerProcessor = processorsByFormat.get(innerFormat);
      ContentAccessor unwrapped = innerProcessor != null
          ? innerProcessor.unwrapNested(jwtContent)
          : processorsByFormat.get(CredentialFormat.VC2_DANUBETECH).unwrapNested(jwtContent);
      VerifiableCredential vc = VerifiableCredential.fromJson(unwrapped.getContentAsString());
      vc.setDocumentLoader(docLoader);
      return vc;
    } catch (Exception ex) {
      log.warn("tryUnwrapJwtVc; failed to unwrap JWT VC: {}", ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Attempts to unwrap an {@code EnvelopedVerifiableCredential} (data: URI carrying a JWT)
   * to a {@link VerifiableCredential}. Returns {@code null} when unwrapping fails.
   *
   *  @param entryMap the JSON-LD map representing the EVC entry
   *  @param docLoader the DocumentLoader to use for JSON-LD processing of the inner VC
   *  @return the unwrapped VerifiableCredential, or null if unwrapping fails
   * @throws ClientException if the entry is a valid EVC but its data: URI is malformed
   */
  public @Nullable VerifiableCredential tryUnwrapEnvelopedVc(Map<String, Object> entryMap, DocumentLoader docLoader) {
    Object idObj = resolveId(entryMap);
    if (!(idObj instanceof String idStr) || !idStr.startsWith(DATA_URI_PREFIX)) {
      log.debug("tryUnwrapEnvelopedVc; no data: URI in EnvelopedVerifiableCredential");
      return null;
    }
    try {
      int commaIdx = idStr.indexOf(',');
      if (commaIdx < 0) {
        int semiIdx = idStr.indexOf(';');
        if (semiIdx < 0) {
          return null;
        }
        commaIdx = semiIdx;
      }
      String jwtStr = idStr.substring(commaIdx + 1);
      return tryUnwrapJwtVc(jwtStr, docLoader);
    } catch (Exception ex) {
      log.warn("tryUnwrapEnvelopedVc; failed to unwrap: {}", ex.getMessage(), ex);
      return null;
    }
  }

  /**
   * Verifies inner VC credentials inside a VP JWT. Handles compact JWT strings and
   * {@code EnvelopedVerifiableCredential} entries; inline JSON-LD VCs are rejected because
   * LD-proof verification is not supported.
   *
   * @param ld the JSON-LD object representing the VP payload
   * @return a list of Validators for the inner VC credentials; empty if no inner VC credentials are found
   * @throws ClientException if any inner VC credentials are malformed (e.g. invalid JWT format or malformed EVC data: URI)
   * @throws VerificationException if any inner JWT credentials fail signature verification or if any EVC entries are malformed
   * or fail to parse as valid VC JSON-LD documents
   */
  public List<Validator> verifyInnerVcCredentials(JsonLDObject ld) {
    Object vcArrayObj = ld.getJsonObject().get(VERIFIABLE_CREDENTIAL_KEY);
    if (vcArrayObj == null) {
      return List.of();
    }

    List<Object> vcEntries;
    if (vcArrayObj instanceof List<?> list) {
      @SuppressWarnings("unchecked")
      // the list is untyped due to JSON parsing, but we only read it as Object entries and never write to it, so this is safe
      List<Object> cast = (List<Object>) list;
      vcEntries = cast;
    } else {
      vcEntries = List.of(vcArrayObj);
    }

    List<Validator> validators = new ArrayList<>();
    for (Object entry : vcEntries) {
      if (entry instanceof String jwtStr && jwtStr.startsWith(JWT_PREFIX)) {
        validators.add(jwtSignatureVerifier.verify(jwtStr));
      } else if (entry instanceof Map<?, ?> vcMap) {
        if (isEnvelopedVerifiableCredential(resolveType(vcMap), vcMap.get(RDF_CONTEXT_KEY))) {
          Object idObj = resolveId(vcMap);
          if (!(idObj instanceof String idStr)) {
            throw new ClientException(EVC_TYPE + " missing 'id' field");
          }
          validators.add(jwtSignatureVerifier.verifyFromDataUrl(idStr));
        } else {
          throw new VerificationException(
              "Signatures error; Linked Data proof verification is not supported."
                  + " Use JWT or Enveloped Credential format.");
        }
      }
    }
    return validators;
  }

  @SuppressWarnings("unchecked") // objectMapper.readValue with Map.class returns raw Map type
  private @Nullable Map<String, Object> unwrapEnvelopedVcJwt(Object idObj) {
    if (!(idObj instanceof String id) || !id.startsWith(DATA_URI_PREFIX)) {
      return null;
    }
    try {
      String jwt = extractJwtFromDataUri(id, EVC_TYPE);
      SignedJWT signedJwt = SignedJWT.parse(jwt);
      String innerJson = signedJwt.getPayload().toString();
      return objectMapper.readValue(innerJson, Map.class);
    } catch (Exception e) {
      log.debug("unwrapEnvelopedVcJwt; failed to parse inner JWT: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * Extracts the compact JWT string from a {@code data:} URI by splitting at the first comma.
   *
   * @throws ClientException if the URI has no comma separator or the payload is empty
   */
  private static String extractJwtFromDataUri(String dataUri, String typeName) {
    int comma = dataUri.indexOf(',');
    if (comma < 0) {
      throw new ClientException(typeName + ": malformed data: URI (no comma separator)");
    }
    String jwt = dataUri.substring(comma + 1);
    if (jwt.isBlank()) {
      throw new ClientException(typeName + ": data: URI payload is empty");
    }
    return jwt;
  }
}
