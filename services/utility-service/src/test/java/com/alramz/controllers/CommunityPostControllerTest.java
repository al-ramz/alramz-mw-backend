package com.alramz.controllers;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.CommunityPost;
import com.alramz.model.CommunityPostRequest;
import com.alramz.model.CommunityPostResponse;
import com.alramz.model.GenericResponse;
import com.alramz.service.CommunityPostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostControllerTest {

    @Mock
    private CommunityPostService communityPostService;

    @AfterEach
    void tearDown() {
        MDCUtil.clear();
    }

    @Test
    void createCommunityPost_shouldReturn201WithLocationHeaderAndPopulatedBody() {
        CommunityPostController controller = new CommunityPostController(communityPostService);

        CommunityPostRequest request = new CommunityPostRequest(2L, "admin", "admin", "<p>hello</p>");

        CommunityPost post = new CommunityPost();
        post.setPostId(4747L);
        post.setPostedUserId(2L);
        post.setPostedBy("admin");
        post.setStatus(CommunityPost.StatusEnum.ACTIVE);
        post.setPostType("2");

        when(communityPostService.publish(any(CommunityPostRequest.class))).thenReturn(post);

        var response = controller.createCommunityPost(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/v1/community/posts/4747");

        GenericResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getResponseCode()).isEqualTo("201");
        assertThat(body.getResponseMessage()).isEqualTo("Created");
        assertThat(body.getCorrelationId()).isNotNull();
        assertThat(body.getResponse()).isInstanceOf(CommunityPostResponse.class);
        assertThat(((CommunityPostResponse) body.getResponse()).getPost().getPostId()).isEqualTo(4747L);

        verify(communityPostService).publish(request);
    }

    @Test
    void createCommunityPost_shouldEchoTheRequestCorrelationId() {
        MDCUtil.putCorrelationId("2afe4a42-44f7-4e12-aae7-df0e42d4d757");

        CommunityPostController controller = new CommunityPostController(communityPostService);
        CommunityPostRequest request = new CommunityPostRequest(2L, "admin", "admin", "<p>hello</p>");
        CommunityPost post = new CommunityPost();
        post.setPostId(1L);
        when(communityPostService.publish(any(CommunityPostRequest.class))).thenReturn(post);

        var response = controller.createCommunityPost(request);

        assertThat(response.getBody().getCorrelationId().toString()).isEqualTo("2afe4a42-44f7-4e12-aae7-df0e42d4d757");
    }

    @Test
    void createCommunityPost_shouldGenerateCorrelationIdWhenRequestIdIsNotAUuid() {
        MDCUtil.putCorrelationId("not-a-uuid");

        CommunityPostController controller = new CommunityPostController(communityPostService);
        CommunityPostRequest request = new CommunityPostRequest(2L, "admin", "admin", "<p>hello</p>");
        CommunityPost post = new CommunityPost();
        post.setPostId(1L);
        when(communityPostService.publish(any(CommunityPostRequest.class))).thenReturn(post);

        var response = controller.createCommunityPost(request);

        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }
}
