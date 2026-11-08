package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.domain.entity.Profile;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user's profile")
public record ProfileResponse(Long userId, String bio, String avatarUrl) {

    public static ProfileResponse of(Profile profile) {
        return new ProfileResponse(profile.userId(), profile.bio(), profile.avatarUrl());
    }
}
