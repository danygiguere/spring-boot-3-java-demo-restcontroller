package com.example.restcontrollerdemo.service;

import com.example.restcontrollerdemo.dto.PostDetails;

/** Outcome of POST /posts; {@code replayed} when the Idempotency-Key had already created this post. */
public record CreatedPost(PostDetails post, boolean replayed) {}
