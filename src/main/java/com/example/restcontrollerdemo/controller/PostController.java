package com.example.restcontrollerdemo.controller;

import com.example.restcontrollerdemo.dto.CreatePostRequest;
import com.example.restcontrollerdemo.dto.ImageResponse;
import com.example.restcontrollerdemo.dto.PostDetails;
import com.example.restcontrollerdemo.dto.PostSummary;
import com.example.restcontrollerdemo.dto.UpdatePostRequest;
import com.example.restcontrollerdemo.dto.UserResponse;
import com.example.restcontrollerdemo.security.AuthenticatedUser;
import com.example.restcontrollerdemo.service.CreatedPost;
import com.example.restcontrollerdemo.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.util.List;
import org.hibernate.validator.constraints.Range;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
@Tag(name = "Posts", description = "Posts of the caller's enterprise; writes are restricted to the author")
public class PostController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    @Operation(operationId = "getFeed", summary = "The enterprise feed, newest first (hasManyThrough)")
    public List<PostSummary> feed(
            AuthenticatedUser me,
            @RequestParam(required = false) @Positive(message = "{param.positive}") Long beforeId,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT)
                    @Range(min = 1, max = Pagination.MAX_LIMIT, message = "{param.limit.range}")
                    int limit) {
        return postService.feed(me, Pagination.before(beforeId), limit);
    }

    /** Required key: without it, a retried or double-submitted create would publish the post twice. */
    @PostMapping
    @Operation(
            operationId = "createPost",
            summary = "Publish a post with its images, as the authenticated user",
            description = "A retry with the same Idempotency-Key and body returns the post created the first time "
                    + "(header Idempotent-Replayed: true). The same key with a different body is a 409.",
            responses = @ApiResponse(responseCode = "201", description = "Created (or replayed)"))
    public ResponseEntity<PostDetails> create(
            AuthenticatedUser me,
            @Parameter(example = "3f8a2c4e-9b1d-4e6f-a5c7-2d9e8b1f0a3c")
                    @RequestHeader(IDEMPOTENCY_KEY_HEADER)
                    @Pattern(regexp = "[A-Za-z0-9_-]{8,128}", message = "{idempotency.key.pattern}")
                    String idempotencyKey,
            @Valid @RequestBody CreatePostRequest request) {
        CreatedPost created = postService.create(me, idempotencyKey, request);
        return ResponseEntity.created(URI.create("/posts/" + created.post().id()))
                .header(REPLAYED_HEADER, String.valueOf(created.replayed()))
                .body(created.post());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getPostById", summary = "Get a post with its author, images and like count")
    public PostDetails findById(AuthenticatedUser me, @PathVariable @Positive(message = "{param.positive}") long id) {
        return postService.findById(me, id);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updatePost", summary = "Edit a post's content (author only)")
    public PostDetails update(
            AuthenticatedUser me,
            @PathVariable @Positive(message = "{param.positive}") long id,
            @Valid @RequestBody UpdatePostRequest request) {
        return postService.update(me, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deletePost", summary = "Delete a post and its images (author only)")
    public void delete(AuthenticatedUser me, @PathVariable @Positive(message = "{param.positive}") long id) {
        postService.delete(me, id);
    }

    @GetMapping("/{id}/images")
    @Operation(operationId = "getPostImages", summary = "List a post's images in display order (hasMany)")
    public List<ImageResponse> findImages(
            AuthenticatedUser me, @PathVariable @Positive(message = "{param.positive}") long id) {
        return postService.findImages(me, id);
    }

    @GetMapping("/{id}/likes")
    @Operation(operationId = "getPostLikers", summary = "List users who like a post (manyToMany)")
    public List<UserResponse> findLikers(
            AuthenticatedUser me,
            @PathVariable @Positive(message = "{param.positive}") long id,
            @RequestParam(required = false) @PositiveOrZero(message = "{param.positiveOrZero}") Long afterId,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT)
                    @Range(min = 1, max = Pagination.MAX_LIMIT, message = "{param.limit.range}")
                    int limit) {
        return postService.findLikers(me, id, Pagination.after(afterId), limit);
    }

    @PutMapping("/{id}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "likePost", summary = "Like a post as the authenticated user (idempotent)")
    public void like(AuthenticatedUser me, @PathVariable @Positive(message = "{param.positive}") long id) {
        postService.like(me, id);
    }

    @DeleteMapping("/{id}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "unlikePost", summary = "Remove the authenticated user's like (idempotent)")
    public void unlike(AuthenticatedUser me, @PathVariable @Positive(message = "{param.positive}") long id) {
        postService.unlike(me, id);
    }
}
