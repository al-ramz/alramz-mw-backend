package com.alramz.config;

import com.alramz.logging.util.MDCUtil;
import com.alramz.service.DmsRequestSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Emits exactly one structured INFO line per DMS request reaching MVC (brief 9.3): correlation ID, consumer,
 * operation, filing category, document ID, Open Bee status, HTTP status, DMS code, duration and, for a save,
 * the file extension only. Never file names, metadata values or keys. afterCompletion runs after a download's
 * body has been streamed, so the duration covers streaming. This line replaces the starter's request/response
 * lines for the save and content paths, which are excluded from them.
 */
@Slf4j
public class DmsRequestSummaryInterceptor implements HandlerInterceptor {

    static final String UNKNOWN_OPERATION = "unknown";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String operation = handler instanceof HandlerMethod method ? method.getMethod().getName() : UNKNOWN_OPERATION;
        request.setAttribute(DmsRequestSummary.ATTRIBUTE, new DmsRequestSummary(operation, System.nanoTime()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(request.getAttribute(DmsRequestSummary.ATTRIBUTE) instanceof DmsRequestSummary summary)) {
            return;
        }
        long durationMs = (System.nanoTime() - summary.getStartNanos()) / 1_000_000L;
        log.info("DMS request correlationId={} consumerId={} operation={} filingCategoryId={} documentId={} "
                        + "openBeeStatus={} httpStatus={} dmsCode={} durationMs={} extension={}",
                MDCUtil.getCorrelationId(), summary.getConsumerId(), summary.getOperation(), summary.getFilingCategoryId(),
                summary.getDocumentId(), summary.getOpenBeeStatus(), response.getStatus(), summary.getDmsCode(),
                durationMs, summary.getExtension());
    }
}
