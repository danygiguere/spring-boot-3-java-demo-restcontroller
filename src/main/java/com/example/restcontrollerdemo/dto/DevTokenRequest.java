package com.example.restcontrollerdemo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dev token request (dev profile only)")
public record DevTokenRequest(
        @NotBlank(message = "{auth.email.required}")
                @Email(message = "{auth.email.invalid}")
                @Size(max = 255, message = "{auth.email.size}")
                @Schema(example = "alice@example.com")
                String email) {}
