package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.domain.entity.Post;
import com.example.restcontrollerdemo.domain.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A post in a list, with its author and counts")
public record PostSummary(
        Long id, UserResponse author, String content, Instant createdAt, long imageCount, long likeCount) {

    public static PostSummary of(Post post, User author, long imageCount, long likeCount) {
        return new PostSummary(
                post.id(), UserResponse.of(author), post.content(), post.createdAt(), imageCount, likeCount);
    }
}
