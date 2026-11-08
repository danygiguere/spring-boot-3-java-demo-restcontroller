package com.example.restcontrollerdemo.domain.entity;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** belongsTo {@link User} (author); hasMany {@link Image}. {@code createdAt} is UTC (TIMESTAMPTZ). */
@Table("posts")
public record Post(@Id Long id, Long userId, String content, Instant createdAt) {}
