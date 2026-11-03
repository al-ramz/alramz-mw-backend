package com.alramz.service.impl;

import com.alramz.config.DmsProperties;
import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import com.alramz.model.DmsFilingCategoriesResponse;
import com.alramz.model.DmsFilingCategory;
import com.alramz.model.DmsMetadataDefinition;
import com.alramz.model.DmsSaveDocumentRequest;
import com.alramz.model.DmsSaveDocumentResponse;
import com.alramz.model.DmsSearchRequest;
import com.alramz.model.DmsSearchResponse;
import com.alramz.openbee.MetadataEncoder;
import com.alramz.openbee.OpenBeeClient;
import com.alramz.openbee.OpenBeeException;
import com.alramz.openbee.OpenBeeFile;
import com.alramz.openbee.OpenBeeSearchRequest;
import com.alramz.openbee.OpenBeeSearchResponse;
import com.alramz.openbee.OpenBeeUpload;
import com.alramz.openbee.OpenBeeWireMockSupport;
import com.alramz.openbee.SearchFilterBuilder;
import com.alramz.service.CatalogCategory;
import com.alramz.service.CatalogMetadata;
import com.alramz.service.ConsumerContext;
import com.alramz.service.ContentDispositionFormatter;
import com.alramz.service.DmsDocumentContent;
import com.alramz.service.DmsRequestSummary;
import com.alramz.service.OpenBeeTimestampConverter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import com.alramz.service.FileNameResolver;
import com.alramz.service.FilingCategoryCatalog;
import com.alramz.service.MetadataTypeGroup;
import com.alramz.service.MetadataValidator;
import com.alramz.service.UploadSpooler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DocumentServiceImplTest {

    static final ConsumerContext ONBOARDING = new ConsumerContext("onboarding-portal", "test-key-onboarding");

    @Mock
    private FilingCategoryCatalog catalog;

    @Mock
    private OpenBeeClient openBeeClient;

    @TempDir
    Path tempDir;

    private DmsProperties properties;
    private DocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = OpenBeeWireMockSupport.properties("http://localhost:65535");
        properties.getUpload().setTempDir(tempDir);
        service = new DocumentServiceImpl(catalog, new MetadataValidator(), new FileNameResolver(),
                new UploadSpooler(properties), openBeeClient, new SearchFilterBuilder(new MetadataEncoder(properties)),
                new OpenBeeTimestampConverter(properties), new ContentDispositionFormatter(), properties);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ------------------------------------------------------------------ list

    @Test
    void listFilingCategories_mapsPerBrief61() {
        when(catalog.get(ONBOARDING)).thenReturn(List.of(passportCategory()));

        DmsFilingCategoriesResponse response = service.listFilingCategories(ONBOARDING);

        DmsFilingCategory category = response.getFilingCategories().get(0);
        assertThat(category.getId()).isEqualTo(42L);
        assertThat(category.getName()).isEqualTo("Customer Passport");
        assertThat(category.getPath()).isEqualTo("/Clients/[CLIENT_ID]");
        assertThat(category.getAutoFiling()).isTrue();
        assertThat(category.getFileNamePattern()).isEqualTo("PASSPORT_[CLIENT_ID]");
        DmsMetadataDefinition clientId = category.getMetadata().get(0);
        assertThat(clientId.getType()).isEqualTo("VARCHAR");
        assertThat(clientId.getMandatory()).isTrue();
        assertThat(clientId.getListValues()).isNull();
        DmsMetadataDefinition type = category.getMetadata().get(1);
        assertThat(type.getListId()).isEqualTo(7L);
        assertThat(type.getListValues()).extracting("name").containsExactly("Passport", "Emirates ID");
        assertThat(type.getAllowNewValue()).isFalse();
        DmsMetadataDefinition reference = category.getMetadata().get(3);
        assertThat(reference.getReadOnly()).isTrue();
    }

    @Test
    void listFilingCategories_allowNewValue_isExposedWithoutListValues() {
        when(catalog.get(ONBOARDING)).thenReturn(List.of(new CatalogCategory(11373, "Share Transfer In", null, true, null,
                List.of(new CatalogMetadata(11888, "RequestID", "LIST", MetadataTypeGroup.LIST, true, true, false, null,
                        11887L, null, true)))));

        DmsMetadataDefinition requestId = service.listFilingCategories(ONBOARDING).getFilingCategories().get(0)
                .getMetadata().get(0);

        assertThat(requestId.getAllowNewValue()).isTrue();
        assertThat(requestId.getListValues()).isNull();
        assertThat(requestId.getListId()).isEqualTo(11887L);
    }

    @Test
    void listFilingCategories_openBeeUnavailable_isDms203() {
        when(catalog.get(ONBOARDING)).thenThrow(new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), "/c"));

        assertDms(() -> service.listFilingCategories(ONBOARDING), ErrorCode.DMS203);
    }

    @Test
    void listFilingCategories_badCredentials_isDms202() {
        when(catalog.get(ONBOARDING)).thenThrow(
                new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 401, List.of("BAD_CREDENTIALS"), "/c"));

        assertDms(() -> service.listFilingCategories(ONBOARDING), ErrorCode.DMS202);
    }

    // ------------------------------------------------------------------ save

    @Test
    void save_happyPath_uploadsAndDeletesTheTempFile() throws Exception {
        DmsRequestSummary summary = bindSummary();
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        ArgumentCaptor<OpenBeeUpload> upload = ArgumentCaptor.forClass(OpenBeeUpload.class);
        when(openBeeClient.addDocument(eq(ONBOARDING), upload.capture())).thenAnswer(invocation -> {
            assertThat(Files.exists(invocation.<OpenBeeUpload>getArgument(1).file())).isTrue();
            return 88213L;
        });

        DmsSaveDocumentResponse response = service.save(ONBOARDING, pdf("passport.pdf"), request("passport.pdf"));

        assertThat(response.getDocumentId()).isEqualTo(88213L);
        assertThat(response.getFilingCategoryId()).isEqualTo(42L);
        OpenBeeUpload sent = upload.getValue();
        assertThat(sent.fileName()).isEqualTo("passport.pdf");
        assertThat(sent.description()).isEqualTo("passport.pdf");
        assertThat(sent.filingCategoryId()).isEqualTo(42L);
        assertThat(sent.overwrite()).isTrue();
        assertThat(sent.metadata()).hasSize(2);
        assertThat(sent.sha256Hex()).hasSize(64);
        assertThat(tempDir).isEmptyDirectory();
        assertThat(summary.getFilingCategoryId()).isEqualTo(42L);
        assertThat(summary.getDocumentId()).isEqualTo(88213L);
        assertThat(summary.getExtension()).isEqualTo("pdf");
    }

    @Test
    void save_explicitDescriptionAndOverwriteFalse_arePassedThrough() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        ArgumentCaptor<OpenBeeUpload> upload = ArgumentCaptor.forClass(OpenBeeUpload.class);
        when(openBeeClient.addDocument(eq(ONBOARDING), upload.capture())).thenReturn(1L);
        DmsSaveDocumentRequest request = request(null).description("Scanned at branch").overwrite(false);

        service.save(ONBOARDING, pdf("C:\\scans\\passport.pdf"), request);

        assertThat(upload.getValue().fileName()).isEqualTo("passport.pdf");
        assertThat(upload.getValue().description()).isEqualTo("Scanned at branch");
        assertThat(upload.getValue().overwrite()).isFalse();
    }

    @Test
    void save_emptyFile_isDms101() {
        assertDms(() -> service.save(ONBOARDING, new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]),
                request(null)), ErrorCode.DMS101, "file must not be empty");
        assertDms(() -> service.save(ONBOARDING, null, request(null)), ErrorCode.DMS101, "file must not be empty");
        verifyNoInteractions(catalog, openBeeClient);
    }

    @Test
    void save_oversizeFile_isDms107() {
        properties.getUpload().setMaxFileSize(DataSize.ofBytes(5));

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request(null)), ErrorCode.DMS107);
        verifyNoInteractions(catalog, openBeeClient);
    }

    @Test
    void save_unavailableCategory_isDms102() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.empty());

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request(null)), ErrorCode.DMS102,
                "Filing category 42 is not available to this consumer");
        verifyNoInteractions(openBeeClient);
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void save_catalogUnavailable_isDms203() {
        when(catalog.find(ONBOARDING, 42)).thenThrow(new OpenBeeException(OpenBeeException.Kind.CONNECTION, 0, List.of(), "/c"));

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request(null)), ErrorCode.DMS203);
    }

    @Test
    void save_validationFailure_neverSpoolsOrUploads() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        DmsSaveDocumentRequest request = request(null);
        request.getMetadata().remove("11329");

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request), ErrorCode.DMS104);
        verifyNoInteractions(openBeeClient);
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void save_unknownMetadataId_forcesOneRefreshAndUsesTheNewDefinition() {
        CatalogCategory refreshed = new CatalogCategory(42, "Customer Passport", null, true, null, List.of(
                passportCategory().metadata().get(0), passportCategory().metadata().get(1),
                new CatalogMetadata(11999, "NEW_FIELD", "VARCHAR", MetadataTypeGroup.TEXT, false, false, false, null, null, null)));
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(catalog.refreshIfAllowed(ONBOARDING)).thenReturn(true);
        when(catalog.get(ONBOARDING)).thenReturn(List.of(refreshed));
        when(openBeeClient.addDocument(eq(ONBOARDING), any())).thenReturn(5L);
        DmsSaveDocumentRequest request = request(null);
        request.getMetadata().put("11999", "new");

        assertThat(service.save(ONBOARDING, pdf("a.pdf"), request).getDocumentId()).isEqualTo(5L);
        verify(catalog).refreshIfAllowed(ONBOARDING);
    }

    @Test
    void save_unknownMetadataId_rateLimited_isDms103() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(catalog.refreshIfAllowed(ONBOARDING)).thenReturn(false);
        DmsSaveDocumentRequest request = request(null);
        request.getMetadata().put("99999", "FIT-secret");

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request), ErrorCode.DMS103,
                "Metadata 99999 is not defined on filing category 42");
        verify(catalog, never()).get(ONBOARDING);
    }

    @Test
    void save_refreshRemovesTheCategory_isDms102() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(catalog.refreshIfAllowed(ONBOARDING)).thenReturn(true);
        when(catalog.get(ONBOARDING)).thenReturn(List.of());
        DmsSaveDocumentRequest request = request(null);
        request.getMetadata().put("99999", "x");

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request), ErrorCode.DMS102);
    }

    @Test
    void save_openBeeServerError_isDms203AndTheTempFileIsDeleted() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(openBeeClient.addDocument(eq(ONBOARDING), any()))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 500, List.of(), "/browse/ws/document"));

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request(null)), ErrorCode.DMS203);
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void save_timeout_warnsAboutAPossibleDuplicate(CapturedOutput output) {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(openBeeClient.addDocument(eq(ONBOARDING), any()))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), "/browse/ws/document"));

        assertDms(() -> service.save(ONBOARDING, pdf("passport.pdf"), request(null)), ErrorCode.DMS203);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> output.getOut().contains("possible duplicate version in Open Bee"));
        assertThat(output).contains("possible duplicate version in Open Bee").doesNotContain("passport.pdf");
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void save_conflictWithOverwriteFalse_isDms106() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(openBeeClient.addDocument(eq(ONBOARDING), any()))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 409, List.of(), "/browse/ws/document"));

        assertDms(() -> service.save(ONBOARDING, pdf("a.pdf"), request(null).overwrite(false)), ErrorCode.DMS106);
    }

    // ------------------------------------------------------------------ search

    @Test
    void search_defaultsMapsAndDropsFolders() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        ArgumentCaptor<OpenBeeSearchRequest> sent = ArgumentCaptor.forClass(OpenBeeSearchRequest.class);
        when(openBeeClient.advancedSearch(eq(ONBOARDING), sent.capture())).thenReturn(new OpenBeeSearchResponse(List.of(
                new OpenBeeSearchResponse.Item(null, 88213L, "PASSPORT_FIT-100234.pdf", "2026-10-28T10:15:00",
                        "2026-10-29T08:00:00", 248331L, 42L, "Customer Passport", "pdf", "/Clients/FIT-100234", List.of(
                        new OpenBeeSearchResponse.MetadataValue("11329", "FIT-100234"),
                        new OpenBeeSearchResponse.MetadataValue("77777", "unknown-to-cache"),
                        new OpenBeeSearchResponse.MetadataValue("not-a-number", "x"))),
                new OpenBeeSearchResponse.Item(5501L, null, "FIT-100234", null, null, null, null, null, null, null, null),
                new OpenBeeSearchResponse.Item(null, null, "orphan", null, null, null, null, null, null, null, null)),
                new OpenBeeSearchResponse.Pagination(1L, 1, 25, 1)));
        DmsSearchRequest request = new DmsSearchRequest(42L).page(null).pageSize(null)
                .metadata(new LinkedHashMap<>(Map.of("11329", "FIT-100234")));

        DmsSearchResponse response = service.search(ONBOARDING, request);

        assertThat(sent.getValue().pagination()).isEqualTo(new OpenBeeSearchRequest.PaginationSpec(25, 1));
        assertThat(sent.getValue().filters()).hasSize(3);
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(25);
        assertThat(response.getTotalCount()).isEqualTo(1L);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.getDocuments()).singleElement().satisfies(doc -> {
            assertThat(doc.getDocumentId()).isEqualTo(88213L);
            assertThat(doc.getFileName()).isEqualTo("PASSPORT_FIT-100234.pdf");
            assertThat(doc.getExtension()).isEqualTo("pdf");
            assertThat(doc.getSizeBytes()).isEqualTo(248331L);
            assertThat(doc.getCreationDate()).isEqualTo(OffsetDateTime.parse("2026-10-28T10:15:00+04:00"));
            assertThat(doc.getModificationDate()).isEqualTo(OffsetDateTime.parse("2026-10-29T08:00:00+04:00"));
            assertThat(doc.getFilingCategory().getId()).isEqualTo(42L);
            assertThat(doc.getFilingCategory().getName()).isEqualTo("Customer Passport");
            assertThat(doc.getPath()).isEqualTo("/Clients/FIT-100234");
            assertThat(doc.getMetadata()).extracting("id", "name", "value").containsExactly(
                    org.assertj.core.groups.Tuple.tuple(11329L, "CLIENT_ID", "FIT-100234"),
                    org.assertj.core.groups.Tuple.tuple(77777L, null, "unknown-to-cache"));
        });
    }

    /** Live Open Bee shapes: name without extension, multiple metadata as JSON text, timestamps with offset. */
    @Test
    void search_liveShapes_fileNameWithExtension_andMultipleValuesAsArray() {
        CatalogCategory shareTransfer = new CatalogCategory(11373, "Share Transfer In", null, true, null, List.of(
                new CatalogMetadata(11888, "RequestID", "LIST", MetadataTypeGroup.LIST, true, true, false, null, 11887L, List.of()),
                new CatalogMetadata(11889, "FIT number", "VARCHAR", MetadataTypeGroup.TEXT, true, false, false, null, null, null)));
        when(catalog.find(ONBOARDING, 11373)).thenReturn(Optional.of(shareTransfer));
        when(openBeeClient.advancedSearch(eq(ONBOARDING), any())).thenReturn(new OpenBeeSearchResponse(List.of(
                new OpenBeeSearchResponse.Item(null, 189530L, "2026-10-30-13-14-37_DFM", "2026-10-30T13:14:39+00:00",
                        "2026-10-30T13:14:39+00:00", 54L, 11373L, "Share Transfer In", "txt", "/A/2026-10-30-13-14-37_DFM.txt",
                        List.of(new OpenBeeSearchResponse.MetadataValue("11888", "[\"1001\",\"102\"]"),
                                new OpenBeeSearchResponse.MetadataValue("11889", "TEST-DMS-002")))),
                new OpenBeeSearchResponse.Pagination(1L, 1, 25, 1)));

        DmsSearchResponse response = service.search(ONBOARDING, new DmsSearchRequest(11373L));

        assertThat(response.getDocuments()).singleElement().satisfies(doc -> {
            assertThat(doc.getFileName()).isEqualTo("2026-10-30-13-14-37_DFM.txt");
            assertThat(doc.getCreationDate()).isEqualTo(OffsetDateTime.parse("2026-10-30T13:14:39Z"));
            assertThat(doc.getMetadata()).extracting("id", "value", "values").containsExactly(
                    org.assertj.core.groups.Tuple.tuple(11888L, null, List.of("1001", "102")),
                    org.assertj.core.groups.Tuple.tuple(11889L, "TEST-DMS-002", null));
        });
    }

    @Test
    void multipleValues_parsesJsonArraysAndKeepsAnythingElse() {
        assertThat(DocumentServiceImpl.multipleValues("[\"a\",\"b\"]")).containsExactly("a", "b");
        assertThat(DocumentServiceImpl.multipleValues("[1, null]")).containsExactly("1", null);
        assertThat(DocumentServiceImpl.multipleValues("plain")).containsExactly("plain");
        assertThat(DocumentServiceImpl.multipleValues("{\"x\":1}")).containsExactly("{\"x\":1}");
        assertThat(DocumentServiceImpl.multipleValues("[broken")).containsExactly("[broken");
        assertThat(DocumentServiceImpl.multipleValues(null)).isEmpty();
    }

    @Test
    void search_emptyResult_isEmptyListAndZeroTotals() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(openBeeClient.advancedSearch(eq(ONBOARDING), any())).thenReturn(new OpenBeeSearchResponse(null, null));

        DmsSearchResponse response = service.search(ONBOARDING, new DmsSearchRequest(42L).page(2).pageSize(5));

        assertThat(response.getDocuments()).isEmpty();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.getPage()).isEqualTo(2);
        assertThat(response.getPageSize()).isEqualTo(5);
    }

    @Test
    void search_pageBounds_areDms101() {
        assertDms(() -> service.search(ONBOARDING, new DmsSearchRequest(42L).pageSize(101)), ErrorCode.DMS101,
                "pageSize must be between 1 and 100");
        assertDms(() -> service.search(ONBOARDING, new DmsSearchRequest(42L).pageSize(0)), ErrorCode.DMS101,
                "pageSize must be between 1 and 100");
        assertDms(() -> service.search(ONBOARDING, new DmsSearchRequest(42L).page(0)), ErrorCode.DMS101,
                "page must be a positive integer");
        verifyNoInteractions(catalog, openBeeClient);
    }

    @Test
    void search_unavailableCategory_isDms102_andDateArray_isDms105() {
        when(catalog.find(ONBOARDING, 43)).thenReturn(Optional.empty());
        assertDms(() -> service.search(ONBOARDING, new DmsSearchRequest(43L)), ErrorCode.DMS102);

        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        DmsSearchRequest dateArray = new DmsSearchRequest(42L).metadata(new LinkedHashMap<>(Map.of("11331", List.of("2027-12-31"))));
        assertDms(() -> service.search(ONBOARDING, dateArray), ErrorCode.DMS105,
                "Multiple values are not supported for metadata 11331 (EXPIRY_DATE)");
        verifyNoInteractions(openBeeClient);
    }

    @Test
    void search_openBeeFailure_isMapped() {
        when(catalog.find(ONBOARDING, 42)).thenReturn(Optional.of(passportCategory()));
        when(openBeeClient.advancedSearch(eq(ONBOARDING), any()))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 500, List.of(), "/s"));

        assertDms(() -> service.search(ONBOARDING, new DmsSearchRequest(42L)), ErrorCode.DMS203);
    }

    // ------------------------------------------------------------------ download

    @Test
    void download_nameFromOpenBeeContentDisposition() throws Exception {
        when(openBeeClient.downloadFile(ONBOARDING, 88213)).thenReturn(file(Optional.of("PASSPORT_FIT-100234.pdf")));

        DmsDocumentContent content = service.download(ONBOARDING, 88213);

        assertThat(content.contentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(content.contentLength()).isEqualTo(5L);
        assertThat(content.contentDisposition()).contains("filename=\"PASSPORT_FIT-100234.pdf\"")
                .contains("filename*=UTF-8''PASSPORT_FIT-100234.pdf");
        try (InputStream in = content.body()) {
            assertThat(in.readAllBytes()).isEqualTo("%PDF-".getBytes(StandardCharsets.UTF_8));
        }
        // document info is looked up BEFORE the file stream opens (Open Bee serves one request per key at a time)
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(openBeeClient);
        order.verify(openBeeClient).getDocumentName(ONBOARDING, 88213);
        order.verify(openBeeClient).downloadFile(ONBOARDING, 88213);
        assertThat(content.toString()).doesNotContain("PASSPORT");
    }

    @Test
    void download_nameFromDocumentInfo() {
        when(openBeeClient.downloadFile(ONBOARDING, 88213)).thenReturn(file(Optional.empty()));
        when(openBeeClient.getDocumentName(ONBOARDING, 88213)).thenReturn(Optional.of("Info Name.pdf"));

        assertThat(service.download(ONBOARDING, 88213).contentDisposition()).contains("filename=\"Info Name.pdf\"");
    }

    @Test
    void download_defaultNameWhenDocumentInfoHasNone() {
        when(openBeeClient.downloadFile(ONBOARDING, 88213)).thenReturn(file(Optional.empty()));
        when(openBeeClient.getDocumentName(ONBOARDING, 88213)).thenReturn(Optional.empty());

        assertThat(service.download(ONBOARDING, 88213).contentDisposition()).contains("filename=\"document-88213\"");
    }

    @Test
    void download_documentInfoFailure_isSwallowedWithWarn(CapturedOutput output) {
        when(openBeeClient.downloadFile(ONBOARDING, 88213)).thenReturn(file(Optional.empty()));
        when(openBeeClient.getDocumentName(ONBOARDING, 88213))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 500, List.of(), "/ws/v2/document/{idDocument}"));

        assertThat(service.download(ONBOARDING, 88213).contentDisposition()).contains("filename=\"document-88213\"");
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> output.getOut().contains("document info lookup failed for document 88213"));
        assertThat(output).contains("document info lookup failed for document 88213");
    }

    @Test
    void download_unexpectedFailureAfterOpening_cancelsTheBodyAndRethrows() {
        ContentDispositionFormatter failing = org.mockito.Mockito.mock(ContentDispositionFormatter.class);
        when(failing.attachment(any())).thenThrow(new IllegalStateException("boom"));
        DocumentServiceImpl failingService = new DocumentServiceImpl(catalog, new MetadataValidator(), new FileNameResolver(),
                new UploadSpooler(properties), openBeeClient, new SearchFilterBuilder(new MetadataEncoder(properties)),
                new OpenBeeTimestampConverter(properties), failing, properties);
        AtomicBoolean cancelled = new AtomicBoolean();
        when(openBeeClient.downloadFile(ONBOARDING, 88213)).thenReturn(new OpenBeeFile(null, null, Optional.empty(),
                Flux.<DataBuffer>never().doOnCancel(() -> cancelled.set(true))));

        assertThatThrownBy(() -> failingService.download(ONBOARDING, 88213)).isInstanceOf(IllegalStateException.class);
        assertThat(cancelled).isTrue();
    }

    @Test
    void download_notFound_isDms108_andBadId_isDms101() {
        when(openBeeClient.downloadFile(ONBOARDING, 404))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 404, List.of(), "/ws/v2/file/{idDocument}"));

        assertDms(() -> service.download(ONBOARDING, 404), ErrorCode.DMS108);
        assertDms(() -> service.download(ONBOARDING, 0), ErrorCode.DMS101, "documentId must be a positive integer");
    }

    /**
     * Live Open Bee: /ws/v2/file answers 403 for an unknown document too, but document info answers 404. The info
     * lookup comes first, so an unknown document is DMS108 and the file is never requested.
     */
    @Test
    void download_documentInfoNotFound_isDms108_withoutOpeningTheFile() {
        when(openBeeClient.getDocumentName(ONBOARDING, 999))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 404, List.of("DOCUMENT_NOTFOUND"), "/ws/v2/document/{idDocument}"));

        assertDms(() -> service.download(ONBOARDING, 999), ErrorCode.DMS108);
        verify(openBeeClient, never()).downloadFile(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void download_forbidden_isDms109() {
        when(openBeeClient.getDocumentName(ONBOARDING, 997))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 403, List.of("PERMISSION_DENIED"), "/ws/v2/document/{idDocument}"));
        assertDms(() -> service.download(ONBOARDING, 997), ErrorCode.DMS109);

        when(openBeeClient.getDocumentName(ONBOARDING, 998)).thenReturn(Optional.of("x.pdf"));
        when(openBeeClient.downloadFile(ONBOARDING, 998))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 403, List.of("PERMISSION_DENIED"), "/ws/v2/file/{idDocument}"));
        assertDms(() -> service.download(ONBOARDING, 998), ErrorCode.DMS109);
    }

    @Test
    void download_documentInfoRejectsTheKey_isDms202() {
        when(openBeeClient.getDocumentName(ONBOARDING, 996))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, 401, List.of(), "/ws/v2/document/{idDocument}"));

        assertDms(() -> service.download(ONBOARDING, 996), ErrorCode.DMS202);
    }

    @Test
    void download_documentInfoTimeout_stillDownloadsWithTheDefaultName() {
        when(openBeeClient.getDocumentName(ONBOARDING, 88213))
                .thenThrow(new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), "/ws/v2/document/{idDocument}"));
        when(openBeeClient.downloadFile(ONBOARDING, 88213)).thenReturn(file(Optional.empty()));

        assertThat(service.download(ONBOARDING, 88213).contentDisposition()).contains("filename=\"document-88213\"");
    }

    private static OpenBeeFile file(Optional<String> name) {
        return new OpenBeeFile(MediaType.APPLICATION_PDF, 5L, name, Flux.just(
                DefaultDataBufferFactory.sharedInstance.wrap("%PDF-".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void loggableExtension_isLowercaseAndBounded() {
        assertThat(DocumentServiceImpl.loggableExtension("a.PDF")).isEqualTo("pdf");
        assertThat(DocumentServiceImpl.loggableExtension("a.verylongextension")).hasSize(10);
        assertThat(DocumentServiceImpl.loggableExtension("noext")).isNull();
    }

    // ------------------------------------------------------------------ fixtures

    static CatalogCategory passportCategory() {
        return new CatalogCategory(42, "Customer Passport", "/Clients/[CLIENT_ID]", true, "PASSPORT_[CLIENT_ID]", List.of(
                new CatalogMetadata(11329, "CLIENT_ID", "VARCHAR", MetadataTypeGroup.TEXT, true, false, false, null, null, null),
                new CatalogMetadata(11330, "DOCUMENT_TYPE", "LIST", MetadataTypeGroup.LIST, true, false, false, null, 7L,
                        List.of(new CatalogMetadata.ListValue(101, "Passport", null),
                                new CatalogMetadata.ListValue(102, "Emirates ID", null))),
                new CatalogMetadata(11331, "EXPIRY_DATE", "DATE", MetadataTypeGroup.DATE, false, false, false, null, null, null),
                new CatalogMetadata(11332, "REFERENCE_NO", "INT", MetadataTypeGroup.INTEGER, false, false, true, null, null, null)));
    }

    static DmsSaveDocumentRequest request(String fileName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("11329", "FIT-100234");
        metadata.put("11330", "Passport");
        return new DmsSaveDocumentRequest(42L).fileName(fileName).metadata(metadata);
    }

    static MockMultipartFile pdf(String originalName) {
        return new MockMultipartFile("file", originalName, "application/pdf",
                "%PDF-1.7 synthetic".getBytes(StandardCharsets.UTF_8));
    }

    private static DmsRequestSummary bindSummary() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        DmsRequestSummary summary = new DmsRequestSummary("saveDmsDocument", System.nanoTime());
        request.setAttribute(DmsRequestSummary.ATTRIBUTE, summary);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return summary;
    }

    static void assertDms(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DmsException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    static void assertDms(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code, String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DmsException.class, e -> {
            assertThat(e.getErrorCode()).isEqualTo(code);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }
}
