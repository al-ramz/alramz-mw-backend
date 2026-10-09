package com.alramz.finoux;

import com.alramz.model.CommunityPost;

/** Outcome of a Finoux {@code InsertPostData} call. */
public sealed interface FinouxResult
        permits FinouxResult.Posted, FinouxResult.Rejected, FinouxResult.Failed, FinouxResult.Unavailable {

    record Posted(CommunityPost post) implements FinouxResult {
    }

    /** 2xx, but Finoux refused the post or returned data we cannot read. */
    record Rejected(String message) implements FinouxResult {
    }

    /** Finoux answered with a non-2xx status. */
    record Failed(int httpStatus, String message) implements FinouxResult {
    }

    /** Timeout, connection failure, or circuit breaker open. */
    record Unavailable(Throwable cause) implements FinouxResult {
    }
}
