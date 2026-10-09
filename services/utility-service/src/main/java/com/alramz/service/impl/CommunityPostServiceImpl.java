package com.alramz.service.impl;

import com.alramz.exception.ApplicationException;
import com.alramz.exception.ErrorCode;
import com.alramz.exception.FinouxIntegrationException;
import com.alramz.exception.FinouxUnavailableException;
import com.alramz.finoux.FinouxClient;
import com.alramz.finoux.FinouxResult;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.CommunityPost;
import com.alramz.model.CommunityPostRequest;
import com.alramz.service.CommunityPostService;
import lombok.RequiredArgsConstructor;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunityPostServiceImpl implements CommunityPostService {

    private static final int MAX_POST_DESC_LENGTH = 10000;
    private static final PolicyFactory SANITIZER =
            Sanitizers.FORMATTING.and(Sanitizers.LINKS).and(Sanitizers.BLOCKS);

    private final FinouxClient finouxClient;

    @Override
    @Loggable
    public CommunityPost publish(CommunityPostRequest request) {
        FinouxResult result = finouxClient.insertPost(request, sanitize(request.getPostDesc()));
        return switch (result) {
            case FinouxResult.Posted posted -> posted.post();
            case FinouxResult.Rejected rejected ->
                    throw new FinouxIntegrationException(ErrorCode.FINOUX_REJECTED, rejected.message());
            case FinouxResult.Failed failed ->
                    throw new FinouxIntegrationException(ErrorCode.FINOUX_ERROR, failed.message());
            case FinouxResult.Unavailable unavailable ->
                    throw new FinouxUnavailableException("Finoux community service unavailable", unavailable.cause());
        };
    }

    // The length cap applies after sanitising, so markup that gets stripped doesn't count against it.
    private static String sanitize(String rawPostDesc) {
        String sanitized = SANITIZER.sanitize(rawPostDesc);
        if (sanitized == null || sanitized.isBlank()) {
            throw new ApplicationException(ErrorCode.POST_DESC_INVALID, "postDesc must not be blank after sanitisation");
        }
        if (sanitized.length() > MAX_POST_DESC_LENGTH) {
            throw new ApplicationException(ErrorCode.POST_DESC_INVALID,
                    "postDesc must be at most " + MAX_POST_DESC_LENGTH + " characters after sanitisation");
        }
        return sanitized;
    }
}
