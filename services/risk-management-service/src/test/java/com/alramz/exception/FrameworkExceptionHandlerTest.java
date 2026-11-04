package com.alramz.exception;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.model.LegacyCountryListEnvelope;
import com.alramz.model.LegacyCountryRatingEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class FrameworkExceptionHandlerTest {

    private final FrameworkExceptionHandler handler = new FrameworkExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    private static HttpServletRequest request(String method, String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getMethod()).thenReturn(method);
        lenient().when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void putOnCountries_shouldReturn405NotA500() {
        ResponseEntity<Object> response = handler.handleFrameworkException(
                new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "POST")),
                request("PUT", "/api/v1/countries"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.POST);
        assertThat(response.getBody()).isInstanceOfSatisfying(GenericResponse.class, body -> {
            assertThat(body.getResponseCode()).isEqualTo("405");
            assertThat(body.getResponseMessage()).isEqualTo("Method Not Allowed");
            assertThat(body.getErrorCode()).isNull();
            assertThat(body.getCorrelationId()).isNotNull();
        });
    }

    @Test
    void legacyCountriesPost_withUnsupportedMediaType_shouldReturnTheLegacy500WithHttp200() {
        MDCUtil.putCorrelationId("header-corr-001");

        ResponseEntity<Object> response = handler.handleFrameworkException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                request("POST", "/api/v1/countries"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOfSatisfying(LegacyCountryListEnvelope.class, body -> {
            assertThat(body.getCorrelationID()).isEqualTo("header-corr-001");
            assertThat(body.getResponseCode()).isEqualTo("500");
            assertThat(body.getResponseMessage()).isEqualTo("Internal Server Error");
            assertThat(body.getResponse()).isNull();
        });
    }

    @Test
    void legacyRatingsPost_withUnsupportedMediaType_shouldReturnTheLegacyRatings500() {
        ResponseEntity<Object> response = handler.handleFrameworkException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                request("POST", "/api/v1/countryRatings"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(LegacyCountryRatingEnvelope.class);
    }

    @Test
    void unsupportedMediaTypeOnANonLegacyRoute_shouldReturn415() {
        ResponseEntity<Object> response = handler.handleFrameworkException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                request("POST", "/api/auth/login"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void notAcceptable_shouldReturn406() {
        ResponseEntity<Object> response = handler.handleFrameworkException(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)),
                request("GET", "/api/v1/countries"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void unknownPath_shouldReturn404() {
        ResponseEntity<Object> response = handler.handleFrameworkException(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/nothing", "api/v1/nothing"),
                request("GET", "/api/v1/nothing"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((GenericResponse) response.getBody()).getResponseMessage()).isEqualTo("Not Found");
    }

    @Test
    void nonErrorResponseException_shouldFailClosedWithAGeneric500() {
        ResponseEntity<Object> response = handler.handleFrameworkException(
                new IllegalStateException("internal detail"), request("GET", "/api/v1/countries"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(((GenericResponse) response.getBody()).getErrorMsg()).isEqualTo("Internal Server Error");
    }
}
