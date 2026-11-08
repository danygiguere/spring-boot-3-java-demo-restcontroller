package com.example.restcontrollerdemo.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** belongsTo {@link Post}. {@code position} (1 to 10) is the display order. */
@Table("images")
public record Image(@Id Long id, Long postId, String url, Integer position) {}
