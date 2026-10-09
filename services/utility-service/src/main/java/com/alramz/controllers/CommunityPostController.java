package com.alramz.controllers;

import com.alramz.api.CommunityApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.CommunityPost;
import com.alramz.model.CommunityPostRequest;
import com.alramz.model.CommunityPostResponse;
import com.alramz.model.GenericResponse;
import com.alramz.service.CommunityPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommunityPostController implements CommunityApi {

    private final CommunityPostService communityPostService;

    @Override
    @JwtSecured(roles = "APP_UTILITY")
    @Loggable
    public ResponseEntity<GenericResponse> createCommunityPost(CommunityPostRequest communityPostRequest) {
        CommunityPost post = communityPostService.publish(communityPostRequest);

        GenericResponse body = new GenericResponse();
        body.setResponseCode("201");
        body.setResponseMessage("Created");
        body.setResponse(new CommunityPostResponse().post(post));
        body.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());

        return ResponseEntity.created(URI.create("/api/v1/community/posts/" + post.getPostId()))
                .body(body);
    }
}
