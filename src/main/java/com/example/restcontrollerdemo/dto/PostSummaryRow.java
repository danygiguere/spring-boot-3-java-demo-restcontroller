package com.example.restcontrollerdemo.dto;

import java.time.Instant;

/** Flat row of the post summary queries: the post, its author's name and both counts. */
public record PostSummaryRow(
        Long id, Long userId, String authorName, String content, Instant createdAt, long imageCount, long likeCount) {}
