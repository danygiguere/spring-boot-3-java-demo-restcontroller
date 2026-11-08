package com.example.restcontrollerdemo.dto;

/** {@code GROUP BY post_id} row. Posts with no rows are absent: callers treat them as 0. */
public record PostCount(Long postId, Long total) {}
