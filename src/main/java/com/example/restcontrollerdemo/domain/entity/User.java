package com.example.restcontrollerdemo.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * belongsTo {@link Enterprise}; hasOne {@link Profile}; hasMany {@link Post}; manyToMany {@link Post} through
 * {@link PostLike}. Never serialized directly: responses use DTOs that list their fields explicitly.
 */
@Table("users")
public record User(@Id Long id, Long enterpriseId, String name, String email) {}
