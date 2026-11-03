package com.alramz.service.impl;

import com.alramz.config.DmsProperties;
import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.DmsDocumentMetadataValue;
import com.alramz.model.DmsDocumentSummary;
import com.alramz.model.DmsFilingCategoriesResponse;
import com.alramz.model.DmsFilingCategory;
import com.alramz.model.DmsFilingCategoryRef;
import com.alramz.model.DmsListValue;
import com.alramz.model.DmsMetadataDefinition;
import com.alramz.model.DmsSaveDocumentRequest;
import com.alramz.model.DmsSaveDocumentResponse;
import com.alramz.model.DmsSearchRequest;
import com.alramz.model.DmsSearchResponse;
import com.alramz.openbee.OpenBeeClient;
import com.alramz.openbee.OpenBeeErrorMapper;
import com.alramz.openbee.OpenBeeException;
import com.alramz.openbee.OpenBeeFile;
import com.alramz.openbee.OpenBeeOperation;
import com.alramz.openbee.OpenBeeSearchResponse;
import com.alramz.openbee.OpenBeeUpload;
import com.alramz.openbee.SearchFilterBuilder;
import com.alramz.service.CatalogCategory;
import com.alramz.service.CatalogMetadata;
import com.alramz.service.ConsumerContext;
import com.alramz.service.ContentDispositionFormatter;
import com.alramz.service.DmsDocumentContent;
import com.alramz.service.DmsRequestSummary;
import com.alramz.service.OpenBeeTimestampConverter;
import com.alramz.service.DocumentService;
import com.alramz.service.FileNameResolver;
import com.alramz.service.FilingCategoryCatalog;
import com.alramz.service.MetadataValidator;
import com.alramz.service.SpooledUpload;
import com.alramz.service.UploadSpooler;
import com.alramz.service.ValidatedMetadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * DMS business rules (brief section 6). Open Bee failures are always converted by {@link OpenBeeErrorMapper}.
 * No {@code @Loggable} with arguments here: arguments carry metadata values and file names.
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    static final int MAX_LOGGED_EXTENSION_LENGTH = 10;
    /** Buffers requested ahead from Open Bee while streaming a download: bounds memory per download. */
    static final int DOWNLOAD_DEMAND = 16;
    static final String DEFAULT_NAME_PREFIX = "document-";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FilingCategoryCatalog catalog;
    private final MetadataValidator metadataValidator;
    private final FileNameResolver fileNameResolver;
    private final UploadSpooler uploadSpooler;
    private final OpenBeeClient openBeeClient;
    private final SearchFilterBuilder searchFilterBuilder;
    private final OpenBeeTimestampConverter timestampConverter;
    private final ContentDispositionFormatter contentDispositionFormatter;
    private final DmsProperties properties;

    public DocumentServiceImpl(FilingCategoryCatalog catalog,
                               MetadataValidator metadataValidator,
                               FileNameResolver fileNameResolver,
                               UploadSpooler uploadSpooler,
                               OpenBeeClient openBeeClient,
                               SearchFilterBuilder searchFilterBuilder,
                               OpenBeeTimestampConverter timestampConverter,
                               ContentDispositionFormatter contentDispositionFormatter,
                               DmsProperties properties) {
        this.catalog = catalog;
        this.metadataValidator = metadataValidator;
        this.fileNameResolver = fileNameResolver;
        this.uploadSpooler = uploadSpooler;
        this.openBeeClient = openBeeClient;
        this.searchFilterBuilder = searchFilterBuilder;
        this.timestampConverter = timestampConverter;
        this.contentDispositionFormatter = contentDispositionFormatter;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ categories

    @Override
    public DmsFilingCategoriesResponse listFilingCategories(ConsumerContext consumer) {
        List<CatalogCategory> categories = catalogCall(consumer, () -> catalog.get(consumer));
        return new DmsFilingCategoriesResponse()
                .filingCategories(categories.stream().map(DocumentServiceImpl::toDto).toList());
    }

    // ------------------------------------------------------------------ save

    @Override
    public DmsSaveDocumentResponse save(ConsumerContext consumer, MultipartFile file, DmsSaveDocumentRequest request) {
        if (file == null || file.isEmpty()) {
            throw new DmsException(ErrorCode.DMS101, "file must not be empty");
        }
        if (file.getSize() > properties.getUpload().getMaxFileSize().toBytes()) {
            throw new DmsException(ErrorCode.DMS107);
        }
        long categoryId = request.getFilingCategoryId();
        summary(s -> s.setFilingCategoryId(categoryId));
        Map<String, Object> metadata = request.getMetadata();
        CatalogCategory category = categoryFor(consumer, categoryId, metadata);
        List<ValidatedMetadata> validated = metadataValidator.validateForSave(category, metadata);
        String name = fileNameResolver.resolve(request.getFileName(), file.getOriginalFilename());
        summary(s -> s.setExtension(loggableExtension(name)));
        String description = request.getDescription() == null || request.getDescription().isBlank()
                ? name : request.getDescription();
        boolean overwrite = request.getOverwrite() == null || request.getOverwrite();

        SpooledUpload spooled = uploadSpooler.spool(file);
        try {
            long documentId = openBeeClient.addDocument(consumer, new OpenBeeUpload(spooled.path(), name, description,
                    categoryId, validated, overwrite, spooled.sha256Hex()));
            summary(s -> s.setDocumentId(documentId));
            return new DmsSaveDocumentResponse().documentId(documentId).filingCategoryId(categoryId);
        } catch (OpenBeeException e) {
            if (e.getKind() == OpenBeeException.Kind.TIMEOUT) {
                log.warn("Open Bee upload timed out for consumer {} in filing category {} (correlationId={}): "
                                + "possible duplicate version in Open Bee",
                        consumer.consumerId(), categoryId, MDCUtil.getCorrelationId());
            }
            throw OpenBeeErrorMapper.toDms(e, OpenBeeOperation.SAVE, overwrite, consumer.consumerId());
        } finally {
            UploadSpooler.deleteQuietly(spooled.path());
        }
    }

    // ------------------------------------------------------------------ search

    @Override
    public DmsSearchResponse search(ConsumerContext consumer, DmsSearchRequest request) {
        int page = request.getPage() == null ? 1 : request.getPage();
        if (page < 1) {
            throw new DmsException(ErrorCode.DMS101, "page must be a positive integer");
        }
        int maxPageSize = properties.getSearch().getMaxPageSize();
        int pageSize = request.getPageSize() == null ? properties.getSearch().getDefaultPageSize() : request.getPageSize();
        if (pageSize < 1 || pageSize > maxPageSize) {
            throw new DmsException(ErrorCode.DMS101, ErrorCode.pageSizeMessage(maxPageSize));
        }
        long categoryId = request.getFilingCategoryId();
        summary(s -> s.setFilingCategoryId(categoryId));
        Map<String, Object> metadata = request.getMetadata();
        CatalogCategory category = categoryFor(consumer, categoryId, metadata);
        List<ValidatedMetadata> validated = metadataValidator.validateForSearch(category, metadata);

        OpenBeeSearchResponse response;
        try {
            response = openBeeClient.advancedSearch(consumer, searchFilterBuilder.build(categoryId, validated, page, pageSize));
        } catch (OpenBeeException e) {
            throw OpenBeeErrorMapper.toDms(e, OpenBeeOperation.SEARCH, true, consumer.consumerId());
        }
        AtomicBoolean timestampWarned = new AtomicBoolean(false);
        List<DmsDocumentSummary> documents = response.results() == null ? List.of() : response.results().stream()
                .filter(item -> item != null && item.idFolder() == null && item.idDocument() != null)
                .map(item -> toSummary(item, category, timestampWarned))
                .toList();
        OpenBeeSearchResponse.Pagination pagination = response.pagination();
        return new DmsSearchResponse()
                .documents(documents)
                .page(page)
                .pageSize(pageSize)
                .totalCount(pagination == null || pagination.count() == null ? 0L : pagination.count())
                .totalPages(pagination == null || pagination.nbPages() == null ? 0 : pagination.nbPages());
    }

    /**
     * The live Open Bee returns a multiple metadata's value as JSON text ({@code ["1001","102"]}). Anything that is
     * not a JSON array is returned as a single-element list, unchanged.
     */
    static List<String> multipleValues(String raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (node != null && node.isArray()) {
                List<String> values = new ArrayList<>();
                node.forEach(element -> values.add(element.isNull() ? null : element.asText()));
                return values;
            }
        } catch (JsonProcessingException e) {
            // not JSON: a single stored value
        }
        return List.of(raw);
    }

    private DmsDocumentSummary toSummary(OpenBeeSearchResponse.Item item, CatalogCategory category, AtomicBoolean warned) {
        List<DmsDocumentMetadataValue> metadata = new ArrayList<>();
        if (item.metadata() != null) {
            for (OpenBeeSearchResponse.MetadataValue value : item.metadata()) {
                Long id = parseId(value == null ? null : value.idMetadata());
                if (id != null) {
                    Optional<CatalogMetadata> definition = category.metadata(id);
                    DmsDocumentMetadataValue mapped = new DmsDocumentMetadataValue()
                            .id(id)
                            .name(definition.map(CatalogMetadata::name).orElse(null));
                    if (definition.map(CatalogMetadata::multiple).orElse(false)) {
                        mapped.values(multipleValues(value.value()));
                    } else {
                        mapped.value(value.value());
                    }
                    metadata.add(mapped);
                }
            }
        }
        return new DmsDocumentSummary()
                .documentId(item.idDocument())
                .fileName(FileNameResolver.withExtension(item.name(), item.extension()))
                .extension(item.extension())
                .sizeBytes(item.size())
                .creationDate(timestampConverter.toOffsetDateTime(item.creationDate(), warned))
                .modificationDate(timestampConverter.toOffsetDateTime(item.modificationDate(), warned))
                .filingCategory(new DmsFilingCategoryRef().id(item.idFilingCategory()).name(item.nameFilingCategory()))
                .metadata(metadata)
                .path(item.documentPath());
    }

    private static Long parseId(String id) {
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ download

    @Override
    public DmsDocumentContent download(ConsumerContext consumer, long documentId) {
        if (documentId < 1) {
            throw new DmsException(ErrorCode.DMS101, "documentId must be a positive integer");
        }
        summary(s -> s.setDocumentId(documentId));
        // Document info first: the live Open Bee serves one request at a time per API key, so a lookup made while
        // the file stream is open waits for the stream and times out. It also tells "not found" from "forbidden",
        // which /ws/v2/file does not (403 for both).
        Optional<String> infoName = documentInfoName(consumer, documentId);
        OpenBeeFile file;
        try {
            file = openBeeClient.downloadFile(consumer, documentId);
        } catch (OpenBeeException e) {
            throw OpenBeeErrorMapper.toDms(e, OpenBeeOperation.DOWNLOAD, true, consumer.consumerId());
        }
        try {
            String name = file.fileName().or(() -> infoName).orElse(DEFAULT_NAME_PREFIX + documentId);
            InputStream body = DataBufferUtils.subscriberInputStream(file.body(), DOWNLOAD_DEMAND);
            return new DmsDocumentContent(file.contentType(), file.contentLength(),
                    contentDispositionFormatter.attachment(name), body);
        } catch (RuntimeException e) {
            file.body().subscribe(DataBufferUtils::release).dispose(); // never leave Open Bee's body unconsumed
            throw e;
        }
    }

    /**
     * The document's name from document info. 404 (DMS108), 403 (DMS109) and 401 (DMS202) are decisive and end the
     * download; any other failure is swallowed with a WARN (E46) and the default name is used.
     */
    private Optional<String> documentInfoName(ConsumerContext consumer, long documentId) {
        try {
            return openBeeClient.getDocumentName(consumer, documentId);
        } catch (OpenBeeException e) {
            int status = e.getHttpStatus();
            if (e.getKind() == OpenBeeException.Kind.HTTP_ERROR && (status == 401 || status == 403 || status == 404)) {
                throw OpenBeeErrorMapper.toDms(e, OpenBeeOperation.DOCUMENT_INFO, true, consumer.consumerId());
            }
            log.warn("Open Bee document info lookup failed for document {} (kind={}, status={}, correlationId={}); "
                            + "using the default file name",
                    documentId, e.getKind(), e.getHttpStatus(), MDCUtil.getCorrelationId());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The category if the consumer may use it (DMS102 otherwise). An unknown metadata ID also triggers the
     * single rate-limited forced refresh, so a newly added metadata definition is picked up before DMS103.
     */
    CatalogCategory categoryFor(ConsumerContext consumer, long categoryId, Map<String, Object> metadata) {
        CatalogCategory category = catalogCall(consumer, () -> catalog.find(consumer, categoryId))
                .orElseThrow(() -> new DmsException(ErrorCode.DMS102,
                        "Filing category " + categoryId + " is not available to this consumer"));
        if (metadataValidator.firstUnknownId(category, metadata).isPresent()
                && catalogCall(consumer, () -> catalog.refreshIfAllowed(consumer))) {
            category = catalogCall(consumer, () -> catalog.get(consumer)).stream()
                    .filter(c -> c.id() == categoryId)
                    .findFirst()
                    .orElseThrow(() -> new DmsException(ErrorCode.DMS102,
                            "Filing category " + categoryId + " is not available to this consumer"));
        }
        return category;
    }

    private static <T> T catalogCall(ConsumerContext consumer, Supplier<T> call) {
        try {
            return call.get();
        } catch (OpenBeeException e) {
            throw OpenBeeErrorMapper.toDms(e, OpenBeeOperation.LIST_CATEGORIES, true, consumer.consumerId());
        }
    }

    static String loggableExtension(String name) {
        String extension = FileNameResolver.extension(name);
        if (extension == null) {
            return null;
        }
        String lower = extension.toLowerCase(Locale.ROOT);
        return lower.length() > MAX_LOGGED_EXTENSION_LENGTH ? lower.substring(0, MAX_LOGGED_EXTENSION_LENGTH) : lower;
    }

    private static void summary(java.util.function.Consumer<DmsRequestSummary> update) {
        DmsRequestSummary.current().ifPresent(update);
    }

    static DmsFilingCategory toDto(CatalogCategory category) {
        return new DmsFilingCategory()
                .id(category.id())
                .name(category.name())
                .path(category.path())
                .autoFiling(category.autoFiling())
                .fileNamePattern(category.fileNamePattern())
                .metadata(category.metadata().stream().map(DocumentServiceImpl::toDto).toList());
    }

    static DmsMetadataDefinition toDto(CatalogMetadata metadata) {
        return new DmsMetadataDefinition()
                .id(metadata.id())
                .name(metadata.name())
                .type(metadata.rawType())
                .mandatory(metadata.mandatory())
                .multiple(metadata.multiple())
                .readOnly(metadata.readOnly())
                .parentMetadataId(metadata.parentMetadataId())
                .listId(metadata.listId())
                .listValues(metadata.listValues() == null ? null : metadata.listValues().stream()
                        .map(v -> new DmsListValue().id(v.id()).name(v.name()).sublistId(v.sublistId()))
                        .toList())
                .allowNewValue(metadata.allowNewValue());
    }
}
