package com.example.restcontrollerdemo.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** belongsTo {@link User}; at most one per user (UNIQUE (user_id)). */
@Table("profiles")
public record Profile(@Id Long id, Long userId, String bio, String avatarUrl) {}
