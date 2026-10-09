package com.alramz.finoux;

import com.alramz.client.AbstractRestClient;
import com.alramz.config.FinouxProperties;
import com.alramz.exceptions.ApiCallFailedException;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.CommunityPost;
import com.alramz.model.CommunityPostRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls Finoux's {@code InsertPostData} method and translates every outcome into a {@link FinouxResult}.
 * The outbound audit row is written by the starter's WebClient audit filter, not here.
 */
@Component
public class FinouxClient extends AbstractRestClient {

    private static final String PATH = "/api/market";
    private static final String METHOD_NAME = "InsertPostData";
    private static final String VERSION = "2.0";
    private static final String ACTIVE_STATUS = "A";

    private final FinouxProperties finouxProperties;

    public FinouxClient(
            @Qualifier("finouxService") WebClient webClient,
            @Qualifier("finouxCircuitBreaker") CircuitBreaker circuitBreaker,
            @Qualifier("finouxRetry") Retry retry,
            @Qualifier("finouxTimeLimiter") TimeLimiter timeLimiter,
            FinouxProperties finouxProperties,
            Environment environment,
            ObjectMapper objectMapper) {
        super(webClient, circuitBreaker, retry, timeLimiter, environment, objectMapper);
        this.finouxProperties = finouxProperties;
    }

    @Loggable
    public FinouxResult insertPost(CommunityPostRequest request, String sanitizedDesc) {
        return send(buildRequest(request, sanitizedDesc), request.getPostedUserId());
    }

    private FinouxInsertPostDataRequest buildRequest(CommunityPostRequest request, String sanitizedDesc) {
        List<FinouxParam> params = new ArrayList<>();
        params.add(new FinouxParam("p_posted_user_id", String.valueOf(request.getPostedUserId())));
        params.add(new FinouxParam("p_user_name", request.getUsername()));
        params.add(new FinouxParam("p_posted_by", request.getPostedBy()));
        params.add(new FinouxParam("p_post_desc", sanitizedDesc));
        if (request.getTagSymbol() != null && !request.getTagSymbol().isBlank()) {
            params.add(new FinouxParam("p_tag_sc_comp_id", request.getTagSymbol()));
        }
        FinouxProperties.Post post = finouxProperties.getPost();
        params.add(new FinouxParam("p_posted_user_flag", post.getPostedUserFlag()));
        params.add(new FinouxParam("p_publish_flag", post.getPublishFlag()));
        params.add(new FinouxParam("p_post_type_id", post.getPostTypeId()));
        return new FinouxInsertPostDataRequest(METHOD_NAME, VERSION, params);
    }

    private FinouxResult send(FinouxInsertPostDataRequest body, Long requestedUserId) {
        HttpHeaders headers = new HttpHeaders();
        if (finouxProperties.getApiKey() != null && !finouxProperties.getApiKey().isBlank()) {
            headers.set("X-API-Key", finouxProperties.getApiKey());
        }
        try {
            FinouxInsertPostDataResponse response = call(HttpMethod.POST, PATH,
                    FinouxInsertPostDataRequest.class, body,
                    FinouxInsertPostDataResponse.class, headers, null).block();
            return toResult(response, requestedUserId);
        } catch (ApiCallFailedException e) {
            return new FinouxResult.Failed(e.getResponseCode(), e.getResponseMessage());
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - timeout, connection refused, circuit open
            return new FinouxResult.Unavailable(e);
        }
    }

    private FinouxResult toResult(FinouxInsertPostDataResponse response, Long requestedUserId) {
        if (response == null || !Boolean.TRUE.equals(response.isSuccess())) {
            return new FinouxResult.Rejected(response != null && response.message() != null
                    ? response.message()
                    : "Finoux did not confirm the post (isSuccess=false)");
        }
        Map<String, Object> row = firstRow(response.data());
        if (row == null || row.get("post_ID") == null) {
            return new FinouxResult.Rejected("Finoux response is missing data.table[0].post_ID");
        }
        try {
            return new FinouxResult.Posted(toCommunityPost(row, requestedUserId));
        } catch (IllegalArgumentException | DateTimeException e) {
            return new FinouxResult.Rejected("Finoux returned unreadable post data: " + e.getMessage());
        }
    }

    private CommunityPost toCommunityPost(Map<String, Object> row, Long requestedUserId) {
        Long postedUserId = asLong(row.get("posted_User_Id"));
        CommunityPost post = new CommunityPost();
        post.setPostId(asLong(row.get("post_ID")));
        // Finoux echoes back the user we sent; fall back to it rather than inventing a value.
        post.setPostedUserId(postedUserId != null ? postedUserId : requestedUserId);
        post.setPostedBy(asString(row.get("posted_By")));
        post.setStatus(asStatus(row.get("status")));
        post.setCreatedAt(asDateTime(row.get("created_Dtm")));
        post.setPublishedAt(asDateTime(row.get("publish_dtm")));
        post.setPostType(asString(row.get("posttype")));
        return post;
    }

    private static Map<String, Object> firstRow(FinouxData data) {
        if (data == null || data.table() == null || data.table().isEmpty()) {
            return null;
        }
        return data.table().get(0);
    }

    private static CommunityPost.StatusEnum asStatus(Object value) {
        if (value == null) {
            return null;
        }
        // Finoux reports status with the same one-letter flags we send as p_publish_flag.
        String status = value.toString();
        return CommunityPost.StatusEnum.fromValue(ACTIVE_STATUS.equals(status) ? "ACTIVE" : status);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static OffsetDateTime asDateTime(Object value) {
        return value != null ? OffsetDateTime.parse(value.toString()) : null;
    }
}
