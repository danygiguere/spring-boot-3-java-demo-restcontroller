package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.domain.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A colleague, as seen by other users of the same enterprise (no email)")
public record UserResponse(Long id, String name) {

    public static UserResponse of(User user) {
        return new UserResponse(user.id(), user.name());
    }
}
