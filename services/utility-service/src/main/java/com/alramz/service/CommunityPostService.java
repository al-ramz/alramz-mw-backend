package com.alramz.service;

import com.alramz.model.CommunityPost;
import com.alramz.model.CommunityPostRequest;

public interface CommunityPostService {

    CommunityPost publish(CommunityPostRequest request);
}
