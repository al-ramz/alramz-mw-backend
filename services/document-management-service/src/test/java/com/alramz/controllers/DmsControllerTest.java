package com.alramz.controllers;

import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.DmsFilingCategoriesResponse;
import com.alramz.model.DmsSaveDocumentRequest;
import com.alramz.model.DmsSaveDocumentResponse;
import com.alramz.model.DmsSearchRequest;
import com.alramz.model.DmsSearchResponse;
import com.alramz.service.DmsDocumentContent;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import com.alramz.model.GenericResponse;
import com.alramz.service.ConsumerContext;
import com.alramz.service.ConsumerContextResolver;
import com.alramz.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DmsControllerTest {

    static final ConsumerContext ONBOARDING = new ConsumerContext("onboarding-portal", "test-key-onboarding");

    @Mock
    private ConsumerContextResolver resolver;

    @Mock
    private DocumentService documentService;

    private DmsController controller;

    @BeforeEach
    void setUp() {
        controller = new DmsController(resolver, documentService);
    }

    @Test
    void listDmsFilingCategories_returns200Envelope() {
        DmsFilingCategoriesResponse payload = new DmsFilingCategoriesResponse().filingCategories(List.of());
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.listFilingCategories(ONBOARDING)).thenReturn(payload);

        ResponseEntity<GenericResponse> response = controller.listDmsFilingCategories();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getResponseCode()).isEqualTo("200");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("OK");
        assertThat(response.getBody().getResponse()).isSameAs(payload);
        assertThat(response.getBody().getErrorCode()).isNull();
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }

    @Test
    void saveDmsDocument_returns201EnvelopeWithoutLocation() {
        MockMultipartFile file = new MockMultipartFile("file", "passport.pdf", "application/pdf", new byte[]{1});
        DmsSaveDocumentResponse payload = new DmsSaveDocumentResponse().documentId(88213L).filingCategoryId(42L);
        when(resolver.resolve()).thenReturn(ONBOARDING);
        org.mockito.ArgumentCaptor<DmsSaveDocumentRequest> sent = org.mockito.ArgumentCaptor.forClass(DmsSaveDocumentRequest.class);
        when(documentService.save(org.mockito.ArgumentMatchers.eq(ONBOARDING), org.mockito.ArgumentMatchers.same(file),
                sent.capture())).thenReturn(payload);

        ResponseEntity<GenericResponse> response = controller.saveDmsDocument(file, 42L, "p.pdf", "desc", false,
                "{\"11329\":\"FIT-100234\",\"11330\":[\"a\",\"b\"],\"11333\":12.50,\"11334\":true}");

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getResponseCode()).isEqualTo("201");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Created");
        assertThat(response.getBody().getResponse()).isSameAs(payload);
        assertThat(response.getHeaders().getLocation()).isNull();
        DmsSaveDocumentRequest request = sent.getValue();
        assertThat(request.getFilingCategoryId()).isEqualTo(42L);
        assertThat(request.getFileName()).isEqualTo("p.pdf");
        assertThat(request.getDescription()).isEqualTo("desc");
        assertThat(request.getOverwrite()).isFalse();
        assertThat(request.getMetadata()).containsEntry("11329", "FIT-100234")
                .containsEntry("11330", List.of("a", "b"))
                .containsEntry("11333", new java.math.BigDecimal("12.50"))
                .containsEntry("11334", true);
    }

    @Test
    void saveDmsDocument_optionalFieldsAbsent_keepTheDefaults() {
        MockMultipartFile file = new MockMultipartFile("file", "passport.pdf", "application/pdf", new byte[]{1});
        when(resolver.resolve()).thenReturn(ONBOARDING);
        org.mockito.ArgumentCaptor<DmsSaveDocumentRequest> sent = org.mockito.ArgumentCaptor.forClass(DmsSaveDocumentRequest.class);
        when(documentService.save(org.mockito.ArgumentMatchers.eq(ONBOARDING), org.mockito.ArgumentMatchers.same(file),
                sent.capture())).thenReturn(new DmsSaveDocumentResponse());

        controller.saveDmsDocument(file, 42L, null, null, null, null);

        assertThat(sent.getValue().getOverwrite()).isTrue();
        assertThat(sent.getValue().getMetadata()).isEmpty();
        assertThat(sent.getValue().getFileName()).isNull();
    }

    @Test
    void parseMetadata_acceptsAbsentAndNull_rejectsAnythingButAnObject() {
        assertThat(DmsController.parseMetadata(null)).isEmpty();
        assertThat(DmsController.parseMetadata("  ")).isEmpty();
        assertThat(DmsController.parseMetadata("null")).isEmpty();
        for (String bad : List.of("[\"FIT-100234\"]", "\"FIT-100234\"", "{\"11329\":", "{\"a\":1} trailing", "not json")) {
            assertThatThrownBy(() -> DmsController.parseMetadata(bad))
                    .isInstanceOfSatisfying(com.alramz.exception.DmsException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo(com.alramz.exception.ErrorCode.DMS101);
                        assertThat(e.getMessage()).isEqualTo("metadata must be a JSON object of metadata ID to value")
                                .doesNotContain("FIT-100234");
                    });
        }
    }

    /** Huge exponents parse cheaply to BigDecimal here; MetadataValidator bounds them before any expansion. */
    @Test
    void parseMetadata_keepsAHugeExponentAsAnUnexpandedBigDecimal() {
        Object value = DmsController.parseMetadata("{\"11337\":1e999999999}").get("11337");

        assertThat(value).isInstanceOf(java.math.BigDecimal.class);
        assertThat(((java.math.BigDecimal) value).scale()).isEqualTo(-999_999_999);
    }

    @Test
    void searchDmsDocuments_returns200Envelope() {
        DmsSearchRequest request = new DmsSearchRequest(42L);
        DmsSearchResponse payload = new DmsSearchResponse().documents(List.of()).totalCount(0L);
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.search(ONBOARDING, request)).thenReturn(payload);

        ResponseEntity<GenericResponse> response = controller.searchDmsDocuments(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getResponse()).isSameAs(payload);
    }

    @Test
    void downloadDmsDocumentContent_returnsARawInputStreamResource() throws Exception {
        InputStream body = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.download(ONBOARDING, 88213L)).thenReturn(new DmsDocumentContent(MediaType.APPLICATION_PDF, 3L,
                "attachment; filename=\"a.pdf\"; filename*=UTF-8''a.pdf", body));

        ResponseEntity<Resource> response = controller.downloadDmsDocumentContent(88213L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isExactlyInstanceOf(InputStreamResource.class);
        assertThat(response.getBody().getInputStream()).isSameAs(body);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(3L);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment; filename=\"a.pdf\"");
    }

    @Test
    void downloadDmsDocumentContent_defaultsTheContentType() {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.download(ONBOARDING, 1L)).thenReturn(new DmsDocumentContent(null, null,
                "attachment; filename=\"document-1\"; filename*=UTF-8''document-1", new ByteArrayInputStream(new byte[0])));

        ResponseEntity<Resource> response = controller.downloadDmsDocumentContent(1L);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(-1L);
    }

    @Test
    void unknownConsumer_neverCallsTheService() {
        when(resolver.resolve()).thenThrow(new DmsException(ErrorCode.DMS109));

        assertThatThrownBy(controller::listDmsFilingCategories).isInstanceOf(DmsException.class);
        verifyNoInteractions(documentService);
        verify(resolver).resolve();
    }

    @Test
    void everyEndpoint_isSecuredAndNeverLogsArgumentsOrReturnValues() {
        assertThat(DmsController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1");
        List<Method> endpoints = Arrays.stream(DmsController.class.getDeclaredMethods())
                .filter(m -> m.getReturnType() == ResponseEntity.class && java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .toList();
        assertThat(endpoints).isNotEmpty();
        for (Method method : endpoints) {
            JwtSecured secured = method.getAnnotation(JwtSecured.class);
            assertThat(secured).as(method.getName()).isNotNull();
            assertThat(secured.roles()).containsExactly("APP_DOCUMENT_MANAGEMENT_SERVICE_USER");
            Loggable loggable = method.getAnnotation(Loggable.class);
            assertThat(loggable).as(method.getName()).isNotNull();
            assertThat(loggable.logArgs()).isFalse();
            assertThat(loggable.logReturn()).isFalse();
        }
    }
}
