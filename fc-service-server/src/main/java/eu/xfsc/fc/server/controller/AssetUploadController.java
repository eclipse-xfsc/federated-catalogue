package eu.xfsc.fc.server.controller;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.server.service.AssetUploadService;
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
    public ResponseEntity<Asset> addAssetMultipart(
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
        AssetMetadata result = assetUploadService.processUpload(content, contentType, file.getOriginalFilename());
        return ResponseEntity.created(URI.create("/assets/" + result.getAssetHash())).body(result);
    }

    @PostMapping(value = "/assets", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Asset> addAssetOctetStream(
            @RequestBody byte[] content,
            @RequestHeader(value = "Content-Type", defaultValue = DEFAULT_CONTENT_TYPE) String contentType) {
        log.debug("addAssetOctetStream.enter; contentType: {}, size: {}", contentType, content.length);

        AssetMetadata result = assetUploadService.processUpload(content, contentType, null);
        return ResponseEntity.created(URI.create("/assets/" + result.getAssetHash())).body(result);
    }
}