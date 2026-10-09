package com.alramz.service.impl;

import com.alramz.exception.ApplicationException;
import com.alramz.exception.ErrorCode;
import com.alramz.exception.FinouxIntegrationException;
import com.alramz.exception.FinouxUnavailableException;
import com.alramz.finoux.FinouxClient;
import com.alramz.finoux.FinouxResult;
import com.alramz.model.CommunityPost;
import com.alramz.model.CommunityPostRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceImplTest {

    @Mock
    private FinouxClient finouxClient;

    @InjectMocks
    private CommunityPostServiceImpl service;

    private static CommunityPostRequest request(String postDesc) {
        return new CommunityPostRequest(2L, "admin", "admin", postDesc);
    }

    // --- sanitisation ---

    @Test
    void publish_shouldStripScriptTagButKeepFormattingMarkup() {
        CommunityPostRequest request = request("<p>Hello</p><script>alert('xss')</script>");
        when(finouxClient.insertPost(any(), anyString())).thenReturn(new FinouxResult.Posted(new CommunityPost()));

        service.publish(request);

        ArgumentCaptor<String> sanitized = ArgumentCaptor.forClass(String.class);
        verify(finouxClient).insertPost(eq(request), sanitized.capture());
        assertThat(sanitized.getValue()).contains("Hello").doesNotContain("<script>").doesNotContain("alert(");
    }

    @Test
    void publish_shouldRejectDescriptionThatIsBlankAfterSanitising() {
        assertThatThrownBy(() -> service.publish(request("<script>alert('x')</script>")))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_DESC_INVALID);
        verifyNoInteractions(finouxClient);
    }

    @Test
    void publish_shouldRejectDescriptionLongerThanTheCapAfterSanitising() {
        assertThatThrownBy(() -> service.publish(request("a".repeat(10_001))))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_DESC_INVALID);
        verifyNoInteractions(finouxClient);
    }

    @Test
    void publish_shouldAllowDescriptionAtExactlyTheCap() {
        when(finouxClient.insertPost(any(), anyString())).thenReturn(new FinouxResult.Posted(new CommunityPost()));

        service.publish(request("a".repeat(10_000)));

        verify(finouxClient).insertPost(any(), eq("a".repeat(10_000)));
    }

    // --- result translation ---

    @Test
    void publish_shouldReturnThePostWhenFinouxPostedIt() {
        CommunityPost post = new CommunityPost();
        post.setPostId(4747L);
        when(finouxClient.insertPost(any(), anyString())).thenReturn(new FinouxResult.Posted(post));

        assertThat(service.publish(request("desc"))).isSameAs(post);
    }

    @Test
    void publish_shouldThrowFNX011WhenFinouxRejectsThePost() {
        when(finouxClient.insertPost(any(), anyString())).thenReturn(new FinouxResult.Rejected("Business rule failed"));

        assertThatThrownBy(() -> service.publish(request("desc")))
                .isInstanceOf(FinouxIntegrationException.class)
                .hasMessage("Business rule failed")
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FINOUX_REJECTED);
    }

    @Test
    void publish_shouldThrowFNX010WhenFinouxReturnsAnErrorStatus() {
        when(finouxClient.insertPost(any(), anyString())).thenReturn(new FinouxResult.Failed(500, "Finoux internal error"));

        assertThatThrownBy(() -> service.publish(request("desc")))
                .isInstanceOf(FinouxIntegrationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FINOUX_ERROR);
    }

    @Test
    void publish_shouldThrowFinouxUnavailableWhenFinouxCannotBeReached() {
        RuntimeException cause = new RuntimeException("connection refused");
        when(finouxClient.insertPost(any(), anyString())).thenReturn(new FinouxResult.Unavailable(cause));

        assertThatThrownBy(() -> service.publish(request("desc")))
                .isInstanceOf(FinouxUnavailableException.class)
                .hasCause(cause);
    }
}
