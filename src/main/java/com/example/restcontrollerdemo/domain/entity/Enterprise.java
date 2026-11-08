package com.example.restcontrollerdemo.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** The tenant. hasMany {@link User}; hasManyThrough {@link Post} (through users). */
@Table("enterprises")
public record Enterprise(@Id Long id, String name) {}
