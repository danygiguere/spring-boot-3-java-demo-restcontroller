package com.example.restcontrollerdemo.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * hasOne {@link Profile}; hasMany {@link Post}; hasManyThrough {@link Image} (through posts); manyToMany {@link Post}
 * through {@link PostLike}. Never serialized directly: responses use DTOs that list their fields explicitly.
 */
@Table("users")
public record User(@Id Long id, String name, String email) {}
