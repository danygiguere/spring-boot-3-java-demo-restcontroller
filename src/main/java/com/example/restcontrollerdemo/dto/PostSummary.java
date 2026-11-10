package com.example.restcontrollerdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "A post in a list, with its author and counts")
public record PostSummary(
        Long id, UserResponse author, String content, Instant createdAt, long imageCount, long likeCount) {

    public static PostSummary of(PostSummaryRow row) {
        return new PostSummary(
                row.id(),
                new UserResponse(row.userId(), row.authorName()),
                row.content(),
                row.createdAt(),
                row.imageCount(),
                row.likeCount());
    }
}
