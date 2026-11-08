package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.validation.SafeText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/** Replaces the caller's profile: absent fields are cleared. */
@Schema(description = "Profile of the authenticated user")
public record ProfileRequest(
        @Size(max = 500, message = "{profile.bio.size}")
                @SafeText(message = "{profile.bio.unsafe}")
                @Schema(example = "Backend developer")
                String bio,
        @URL(protocol = "https", message = "{profile.avatarUrl.url}")
                @Size(max = 500, message = "{profile.avatarUrl.size}")
                @Schema(example = "https://images.example.com/avatars/me.png")
                String avatarUrl) {}
