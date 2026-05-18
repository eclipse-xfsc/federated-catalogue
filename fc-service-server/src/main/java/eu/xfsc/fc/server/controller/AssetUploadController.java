package eu.xfsc.fc.server.controller;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.server.service.AssetUploadService;
import eu.xfsc.fc.server.service.UploadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manual controller for non-RDF asset uploads via multipart/form-data and
 * application/octet-stream. These content types are not handled by the
 * generated delegate pattern (which only supports application/json).
 */
@Slf4j
@RestController
@RequestMapping("${openapi.eclipseXFSCFederatedCatalogue.base-path:}")
@RequiredArgsConstructor
public class AssetUploadController {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final AssetUploadService assetUploadService;

    @PostMapping(value = "/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addAssetMultipart(
            @RequestPart("file") MultipartFile file) {
        log.debug("addAssetMultipart.enter; filename: {}, contentType: {}, size: {}",
                file.getOriginalFilename(), file.getContentType(), file.getSize());

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new ServerException("Failed to read uploaded file", ex);
        }

        String contentType = file.getContentType() != null ? file.getContentType() : DEFAULT_CONTENT_TYPE;
        return buildUploadResponse(assetUploadService.processUpload(content, contentType, file.getOriginalFilename()));
    }

    @PostMapping(value = "/assets", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addAssetOctetStream(
            @RequestBody byte[] content,
            @RequestHeader(value = "Content-Type", defaultValue = DEFAULT_CONTENT_TYPE) String contentType) {
        log.debug("addAssetOctetStream.enter; contentType: {}, size: {}", contentType, content.length);

        return buildUploadResponse(assetUploadService.processUpload(content, contentType, null));
    }

    private ResponseEntity<?> buildUploadResponse(UploadResult result) {
        return switch (result) {
            case UploadResult.AssetEnriched ae -> {
                log.debug("buildUploadResponse; enrichment completed with {} triples added",
                        ae.response().getTriplesAdded());
                yield ResponseEntity.ok(ae.response());
            }
            case UploadResult.AssetCreated ac -> {
                // encodePathSegment encodes colons (:) and slashes (/) in IRIs like urn:uuid:... or http://...
                // so the Location header contains a valid single path segment after /assets/
                log.debug("buildUploadResponse; asset created with hash: {}", ac.metadata().getAssetHash());
                yield ResponseEntity.created(
                        URI.create("/assets/" + UriUtils.encodePathSegment(
                                ac.metadata().getId(), StandardCharsets.UTF_8)))
                    .body(ac.metadata());
            }
        };
    }
}
