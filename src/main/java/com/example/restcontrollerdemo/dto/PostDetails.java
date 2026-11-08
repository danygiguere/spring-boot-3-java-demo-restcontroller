package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.domain.entity.Image;
import com.example.restcontrollerdemo.domain.entity.Post;
import com.example.restcontrollerdemo.domain.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "A post with its author, images and like count")
public record PostDetails(
        Long id, UserResponse author, String content, Instant createdAt, List<ImageResponse> images, long likeCount) {

    public static PostDetails of(Post post, User author, List<Image> images, long likeCount) {
        return new PostDetails(
                post.id(),
                UserResponse.of(author),
                post.content(),
                post.createdAt(),
                images.stream().map(ImageResponse::of).toList(),
                likeCount);
    }
}
