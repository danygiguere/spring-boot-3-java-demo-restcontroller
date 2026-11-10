package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.domain.entity.Image;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An image of a post")
public record ImageResponse(Long id, Long postId, String url, Integer position) {

    public static ImageResponse of(Image image) {
        return new ImageResponse(image.id(), image.postId(), image.url(), image.position());
    }
}
