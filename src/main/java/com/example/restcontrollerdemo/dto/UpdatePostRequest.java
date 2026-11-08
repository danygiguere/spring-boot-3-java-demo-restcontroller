package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.validation.SafeText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Post update: only the content is writable")
public record UpdatePostRequest(
        @NotBlank(message = "{post.content.required}")
                @Size(max = 5000, message = "{post.content.size}")
                @SafeText(message = "{post.content.unsafe}")
                @Schema(example = "Edited content")
                String content) {}
