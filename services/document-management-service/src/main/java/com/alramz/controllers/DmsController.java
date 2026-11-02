package com.alramz.controllers;

import com.alramz.api.DmsApi;
import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import com.alramz.exception.GlobalExceptionHandler;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.DmsSaveDocumentRequest;
import com.alramz.model.DmsSearchRequest;
import com.alramz.model.GenericResponse;
import com.alramz.service.ConsumerContextResolver;
import com.alramz.service.DmsDocumentContent;
import com.alramz.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * DMS endpoints (generated {@link DmsApi}). Resolves the consumer from the JWT subject and delegates; no
 * business logic here. {@code @Loggable} never logs arguments or return values: they carry metadata values,
 * file names and file content.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DmsController implements DmsApi {

    static final String ROLE = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER";

    private static final JsonMapper METADATA_JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final ConsumerContextResolver consumerContextResolver;
    private final DocumentService documentService;

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable(logArgs = false, logReturn = false)
    public ResponseEntity<GenericResponse> listDmsFilingCategories() {
        return respond(HttpStatus.OK, documentService.listFilingCategories(consumerContextResolver.resolve()));
    }

    /**
     * 201 with the new document ID; no Location header (there is no metadata GET for a document). The plain form
     * fields are gathered into the same request model the JSON part used to carry.
     */
    @Override
    @JwtSecured(roles = ROLE)
    @Loggable(logArgs = false, logReturn = false)
    public ResponseEntity<GenericResponse> saveDmsDocument(MultipartFile file, Long filingCategoryId, String fileName,
                                                           String description, Boolean overwrite, String metadata) {
        DmsSaveDocumentRequest request = new DmsSaveDocumentRequest(filingCategoryId)
                .fileName(fileName)
                .description(description)
                .metadata(parseMetadata(metadata));
        if (overwrite != null) {
            request.overwrite(overwrite);
        }
        return respond(HttpStatus.CREATED, documentService.save(consumerContextResolver.resolve(), file, request));
    }

    /**
     * The {@code metadata} form field: a JSON object of metadata ID to value or array (absent or blank = none).
     * Decimals bind to BigDecimal, never Double. Anything else is DMS101 without echoing the input.
     */
    static Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = METADATA_JSON.readValue(json, METADATA_TYPE);
            return parsed == null ? new HashMap<>() : parsed;
        } catch (JacksonException e) {
            throw new DmsException(ErrorCode.DMS101, GlobalExceptionHandler.INVALID_METADATA);
        }
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable(logArgs = false, logReturn = false)
    public ResponseEntity<GenericResponse> searchDmsDocuments(DmsSearchRequest dmsSearchRequest) {
        return respond(HttpStatus.OK, documentService.search(consumerContextResolver.resolve(), dmsSearchRequest));
    }

    /**
     * Streams the file without wrapping or buffering. The body is exactly an {@link InputStreamResource}:
     * ResourceHttpMessageConverter skips contentLength() for that class only (a subclass would drain the stream).
     */
    @Override
    @JwtSecured(roles = ROLE)
    @Loggable(logArgs = false, logReturn = false)
    public ResponseEntity<Resource> downloadDmsDocumentContent(Long documentId) {
        DmsDocumentContent content = documentService.download(consumerContextResolver.resolve(), documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(content.contentType() != null ? content.contentType() : MediaType.APPLICATION_OCTET_STREAM);
        if (content.contentLength() != null) {
            headers.setContentLength(content.contentLength());
        }
        headers.set(HttpHeaders.CONTENT_DISPOSITION, content.contentDisposition());
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(content.body()));
    }

    private static ResponseEntity<GenericResponse> respond(HttpStatus status, Object payload) {
        GenericResponse body = new GenericResponse();
        body.setResponseCode(String.valueOf(status.value()));
        body.setResponseMessage(status.getReasonPhrase());
        body.setResponse(payload);
        body.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());
        return ResponseEntity.status(status).body(body);
    }
}
