package com.alramz.controllers;

import com.alramz.config.DmsProperties;
import com.alramz.exception.DmsException;
import com.alramz.exception.ErrorCode;
import com.alramz.exception.GlobalExceptionHandler;
import com.alramz.logging.filter.CorrelationIdFilter;
import com.alramz.model.DmsFilingCategoriesResponse;
import com.alramz.model.DmsFilingCategory;
import com.alramz.service.ConsumerContext;
import com.alramz.service.ConsumerContextResolver;
import com.alramz.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import com.alramz.model.DmsDocumentSummary;
import com.alramz.model.DmsFilingCategoryRef;
import com.alramz.model.DmsSaveDocumentRequest;
import com.alramz.model.DmsSaveDocumentResponse;
import com.alramz.model.DmsSearchResponse;
import com.alramz.service.DmsDocumentContent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Spring MVC dispatch (binding, method validation, Jackson, the advice) for DmsController with a mocked
 * service. Not @WebMvcTest: a standalone MockMvc reusing the context's converters, validator, conversion
 * service, correlation-id filter and method-validation proxy (wealth MmfApiDispatchTest precedent).
 */
@SpringBootTest
class DmsApiDispatchTest {

    static final String BASE = "/api/v1/dms";
    static final String CORRELATION_ID = "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33";
    static final ConsumerContext ONBOARDING = new ConsumerContext("onboarding-portal", "test-key-onboarding");

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Autowired
    @Qualifier("mvcValidator")
    private Validator mvcValidator;

    @Autowired
    @Qualifier("mvcConversionService")
    private FormattingConversionService mvcConversionService;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    @Autowired
    private MethodValidationPostProcessor methodValidationPostProcessor;

    @Autowired
    private DmsProperties properties;

    private ConsumerContextResolver resolver;
    private DocumentService documentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        resolver = mock(ConsumerContextResolver.class);
        documentService = mock(DocumentService.class);
        Object controller = methodValidationPostProcessor.postProcessAfterInitialization(
                new DmsController(resolver, documentService), "dmsController");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(properties))
                .setMessageConverters(handlerAdapter.getMessageConverters().toArray(new HttpMessageConverter<?>[0]))
                .setValidator(mvcValidator)
                .setConversionService(mvcConversionService)
                .addFilters(correlationIdFilter)
                .build();
    }

    // ------------------------------------------------------------------ categories

    @Test
    void categories_returnCamelCaseEnvelopeAndEchoCorrelationId() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.listFilingCategories(ONBOARDING)).thenReturn(new DmsFilingCategoriesResponse()
                .filingCategories(List.of(new DmsFilingCategory().id(42L).name("Customer Passport").autoFiling(true))));

        mockMvc.perform(get(BASE + "/filing-categories").header("X-Correlation-ID", CORRELATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCode").value("200"))
                .andExpect(jsonPath("$.responseMessage").value("OK"))
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.response.filingCategories[0].id").value(42))
                .andExpect(jsonPath("$.response.filingCategories[0].autoFiling").value(true));
    }

    @Test
    void unknownConsumer_is403Dms109() throws Exception {
        when(resolver.resolve()).thenThrow(new DmsException(ErrorCode.DMS109));

        expectError(mockMvc.perform(get(BASE + "/filing-categories").header("X-Correlation-ID", CORRELATION_ID)),
                403, "DMS109")
                .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                .andExpect(jsonPath("$.errorMsg").value("Consumer is not allowed to perform this operation"));
        verifyNoInteractions(documentService);
    }

    @Test
    void openBeeUnavailable_is503Dms203() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.listFilingCategories(ONBOARDING)).thenThrow(new DmsException(ErrorCode.DMS203));

        expectError(mockMvc.perform(get(BASE + "/filing-categories")), 503, "DMS203");
    }

    @Test
    void unexpectedException_is500Dms299() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.listFilingCategories(ONBOARDING)).thenThrow(new IllegalStateException("boom"));

        expectError(mockMvc.perform(get(BASE + "/filing-categories")), 500, "DMS299")
                .andExpect(jsonPath("$.errorMsg").value("Internal Server Error"));
    }

    // ------------------------------------------------------------------ save

    static final String SAVE_METADATA = "{\"11329\":\"FIT-100234\",\"11330\":\"Passport\"}";

    /** A save request with the given form fields (name, value pairs) and, unless null, the given file part. */
    static MockMultipartHttpServletRequestBuilder save(MockMultipartFile file, String... fields) {
        MockMultipartHttpServletRequestBuilder builder = multipart(BASE + "/documents");
        if (file != null) {
            builder.file(file);
        }
        for (int i = 0; i < fields.length; i += 2) {
            builder.param(fields[i], fields[i + 1]);
        }
        return builder;
    }

    @Test
    void save_returns201WithoutLocation() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.save(eq(ONBOARDING), any(), any()))
                .thenReturn(new DmsSaveDocumentResponse().documentId(88213L).filingCategoryId(42L));

        mockMvc.perform(save(pdfPart(), "filingCategoryId", "42", "fileName", "passport.pdf",
                        "metadata", SAVE_METADATA, "overwrite", "true"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.responseCode").value("201"))
                .andExpect(jsonPath("$.responseMessage").value("Created"))
                .andExpect(jsonPath("$.response.documentId").value(88213))
                .andExpect(jsonPath("$.response.filingCategoryId").value(42));
        ArgumentCaptor<DmsSaveDocumentRequest> request = ArgumentCaptor.forClass(DmsSaveDocumentRequest.class);
        verify(documentService).save(eq(ONBOARDING), any(), request.capture());
        assertThat(request.getValue().getFilingCategoryId()).isEqualTo(42L);
        assertThat(request.getValue().getFileName()).isEqualTo("passport.pdf");
        assertThat(request.getValue().getMetadata()).containsEntry("11329", "FIT-100234");
        assertThat(request.getValue().getOverwrite()).isTrue();
    }

    @Test
    void save_onlyFileAndCategory_isEnough() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.save(eq(ONBOARDING), any(), any())).thenReturn(new DmsSaveDocumentResponse().documentId(1L));

        mockMvc.perform(save(pdfPart(), "filingCategoryId", "42")).andExpect(status().isCreated());
        ArgumentCaptor<DmsSaveDocumentRequest> request = ArgumentCaptor.forClass(DmsSaveDocumentRequest.class);
        verify(documentService).save(eq(ONBOARDING), any(), request.capture());
        assertThat(request.getValue().getMetadata()).isEmpty();
        assertThat(request.getValue().getOverwrite()).isTrue();
    }

    @Test
    void save_missingFilePart_isDms101() throws Exception {
        expectError(mockMvc.perform(save(null, "filingCategoryId", "42")), 400, "DMS101")
                .andExpect(jsonPath("$.errorMsg").value("file part is required"));
        verifyNoInteractions(documentService);
    }

    @Test
    void save_missingFilingCategoryId_isDms101() throws Exception {
        expectError(mockMvc.perform(save(pdfPart(), "metadata", SAVE_METADATA)), 400, "DMS101")
                .andExpect(jsonPath("$.errorMsg").value("filingCategoryId is required"));
        verifyNoInteractions(documentService);
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource(delimiter = '|', value = {
            "filingCategoryId| FIT-100234| filingCategoryId must be a positive integer",
            "filingCategoryId| 0| filingCategoryId must be a positive integer",
            "overwrite| maybe| overwrite must be true or false",
            "metadata| [\"FIT-100234\"]| metadata must be a JSON object of metadata ID to value",
            "metadata| {not json| metadata must be a JSON object of metadata ID to value"
    })
    void save_invalidField_isDms101WithoutEchoingValues(String field, String value, String message) throws Exception {
        String[] fields = "filingCategoryId".equals(field)
                ? new String[]{field, value}
                : new String[]{"filingCategoryId", "42", field, value};
        expectError(mockMvc.perform(save(pdfPart(), fields)), 400, "DMS101")
                .andExpect(jsonPath("$.errorMsg").value(message));
        verifyNoInteractions(documentService);
    }

    @Test
    void save_tooLongFileNameOrDescription_isDms101() throws Exception {
        expectError(mockMvc.perform(save(pdfPart(), "filingCategoryId", "42", "fileName", "a".repeat(256))), 400, "DMS101")
                .andExpect(jsonPath("$.errorMsg").value("fileName must be 1-255 characters"));

        expectError(mockMvc.perform(save(pdfPart(), "filingCategoryId", "42", "description", "d".repeat(1001))), 400, "DMS101")
                .andExpect(jsonPath("$.errorMsg").value("description must be at most 1000 characters"));
        verifyNoInteractions(documentService);
    }

    /**
     * The endpoint consumes multipart/form-data only: anything else is refused while mapping, with 415, before
     * DMS code runs. In the running app the starter's catch-all LoggingExceptionHandler (absent from this
     * standalone setup) turns that into 500 instead: a starter-wide known gap (README "Known gaps").
     */
    @Test
    void save_notMultipart_is415() throws Exception {
        mockMvc.perform(post(BASE + "/documents").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(documentService);
    }

    @Test
    void save_serviceLevelSizeCheck_is413Dms107() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.save(eq(ONBOARDING), any(), any())).thenThrow(new DmsException(ErrorCode.DMS107));

        expectError(mockMvc.perform(save(pdfPart(), "filingCategoryId", "42")), 413, "DMS107")
                .andExpect(jsonPath("$.errorMsg").value("File exceeds the maximum size of 10 MB"));
    }

    // ------------------------------------------------------------------ search

    @Test
    void search_returnsMappedEnvelopeWithOffsetTimestamps() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.search(eq(ONBOARDING), any())).thenReturn(new DmsSearchResponse()
                .documents(List.of(new DmsDocumentSummary().documentId(88213L).fileName("PASSPORT_FIT-100234.pdf")
                        .creationDate(OffsetDateTime.parse("2026-10-28T10:15:00+04:00"))
                        .filingCategory(new DmsFilingCategoryRef().id(42L).name("Customer Passport"))))
                .page(1).pageSize(25).totalCount(1L).totalPages(1));

        mockMvc.perform(post(BASE + "/documents/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filingCategoryId\":42,\"metadata\":{\"11329\":\"FIT-100234\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.documents[0].documentId").value(88213))
                .andExpect(jsonPath("$.response.documents[0].creationDate").value("2026-10-28T10:15:00+04:00"))
                .andExpect(jsonPath("$.response.documents[0].filingCategory.name").value("Customer Passport"))
                .andExpect(jsonPath("$.response.totalCount").value(1));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "{\"filingCategoryId\":42,\"page\":0}| page must be a positive integer",
            "{\"filingCategoryId\":42,\"pageSize\":0}| pageSize must be between 1 and 100",
            "{\"filingCategoryId\":42,\"pageSize\":\"many\"}| pageSize must be between 1 and 100",
            "{\"page\":1}| filingCategoryId must be a positive integer",
            "{broken| Request body is missing or malformed"
    })
    void search_invalidBody_isDms101(String json, String message) throws Exception {
        expectError(mockMvc.perform(post(BASE + "/documents/search").contentType(MediaType.APPLICATION_JSON).content(json)),
                400, "DMS101")
                .andExpect(jsonPath("$.errorMsg").value(message));
        verifyNoInteractions(documentService);
    }

    @Test
    void search_missingBody_isDms101() throws Exception {
        expectError(mockMvc.perform(post(BASE + "/documents/search").contentType(MediaType.APPLICATION_JSON)), 400, "DMS101");
        verifyNoInteractions(documentService);
    }

    // ------------------------------------------------------------------ download

    @Test
    void download_streamsRawBytesNotAnEnvelope() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.download(ONBOARDING, 88213L)).thenReturn(new DmsDocumentContent(MediaType.APPLICATION_PDF, 5L,
                "attachment; filename=\"a.pdf\"; filename*=UTF-8''a.pdf",
                new ByteArrayInputStream("%PDF-".getBytes(StandardCharsets.UTF_8))));

        mockMvc.perform(get(BASE + "/documents/88213/content").accept("application/octet-stream, application/json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Length", "5"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"a.pdf\"; filename*=UTF-8''a.pdf"))
                .andExpect(content().bytes("%PDF-".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void download_notFound_isAJson404Dms108UnderTheBinaryAcceptHeader() throws Exception {
        when(resolver.resolve()).thenReturn(ONBOARDING);
        when(documentService.download(ONBOARDING, 404L)).thenThrow(new DmsException(ErrorCode.DMS108));

        expectError(mockMvc.perform(get(BASE + "/documents/404/content").accept("application/octet-stream, application/json")),
                404, "DMS108")
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(jsonPath("$.errorMsg").value("Document not found"));
    }

    @ParameterizedTest
    @CsvSource({"0", "-5", "abc", "99999999999999999999"})
    void download_invalidDocumentId_isDms101WithoutEchoingTheValue(String documentId) throws Exception {
        expectError(mockMvc.perform(get(BASE + "/documents/" + documentId + "/content")
                        .accept("application/octet-stream, application/json")), 400, "DMS101")
                .andExpect(jsonPath("$.errorMsg").value("documentId must be a positive integer"));
        verifyNoInteractions(documentService);
    }

    static MockMultipartFile pdfPart() {
        return new MockMultipartFile("file", "passport.pdf", "application/pdf", "%PDF-1.7 synthetic".getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ helpers

    static ResultActions expectError(ResultActions actions, int status, String code) throws Exception {
        return actions.andExpect(status().is(status))
                .andExpect(jsonPath("$.responseCode").value(String.valueOf(status)))
                .andExpect(jsonPath("$.errorCode").value(code))
                .andExpect(jsonPath("$.response").doesNotExist())
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }
}
