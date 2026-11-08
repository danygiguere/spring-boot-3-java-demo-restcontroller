package com.example.restcontrollerdemo.domain.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A client Idempotency-Key already used by {@code userId}, with the post it created. */
@Table("idempotency_keys")
public record IdempotencyKey(
        @Id Long id, Long userId, String idempotencyKey, String requestHash, Long postId, Instant createdAt) {}
