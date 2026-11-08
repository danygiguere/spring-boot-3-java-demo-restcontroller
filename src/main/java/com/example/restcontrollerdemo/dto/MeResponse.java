package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.domain.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The authenticated user")
public record MeResponse(Long id, Long enterpriseId, String name, String email) {

    public static MeResponse of(User user) {
        return new MeResponse(user.id(), user.enterpriseId(), user.name(), user.email());
    }
}
