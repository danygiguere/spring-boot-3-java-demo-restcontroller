package com.example.restcontrollerdemo.domain.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Join row of the {@link User} ↔ {@link Post} manyToMany; UNIQUE (post_id, user_id). */
@Table("post_likes")
public record PostLike(@Id Long id, Long postId, Long userId, Instant createdAt) {}
