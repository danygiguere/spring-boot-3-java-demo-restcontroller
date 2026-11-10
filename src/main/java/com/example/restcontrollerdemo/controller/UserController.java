package com.example.restcontrollerdemo.controller;

import com.example.restcontrollerdemo.dto.ImageResponse;
import com.example.restcontrollerdemo.dto.MeResponse;
import com.example.restcontrollerdemo.dto.PostSummary;
import com.example.restcontrollerdemo.dto.ProfileRequest;
import com.example.restcontrollerdemo.dto.ProfileResponse;
import com.example.restcontrollerdemo.dto.UserResponse;
import com.example.restcontrollerdemo.security.AuthenticatedUser;
import com.example.restcontrollerdemo.service.PostService;
import com.example.restcontrollerdemo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.hibernate.validator.constraints.Range;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping("/me")
    @Operation(operationId = "getMe", summary = "Get the authenticated user")
    public MeResponse me(AuthenticatedUser me) {
        return userService.me(me);
    }

    @PutMapping("/me/profile")
    @Operation(operationId = "replaceMyProfile", summary = "Create or replace the authenticated user's profile")
    public ProfileResponse replaceMyProfile(AuthenticatedUser me, @Valid @RequestBody ProfileRequest request) {
        return userService.replaceMyProfile(me, request);
    }

    @GetMapping
    @Operation(operationId = "getUsers", summary = "List users, oldest first")
    public List<UserResponse> findAll(
            @RequestParam(required = false) @PositiveOrZero(message = "{param.positiveOrZero}") Long afterId,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT)
                    @Range(min = 1, max = Pagination.MAX_LIMIT, message = "{param.limit.range}")
                    int limit) {
        return userService.findAll(Pagination.after(afterId), limit);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getUserById", summary = "Get a user (no email)")
    public UserResponse findById(@PathVariable @Positive(message = "{param.positive}") long id) {
        return userService.findById(id);
    }

    @GetMapping("/{id}/profile")
    @Operation(operationId = "getUserProfile", summary = "Get a user's profile (hasOne)")
    public ProfileResponse findProfile(@PathVariable @Positive(message = "{param.positive}") long id) {
        return userService.findProfile(id);
    }

    @GetMapping("/{id}/posts")
    @Operation(operationId = "getUserPosts", summary = "List a user's posts, newest first (hasMany)")
    public List<PostSummary> findPosts(
            @PathVariable @Positive(message = "{param.positive}") long id,
            @RequestParam(required = false) @Positive(message = "{param.positive}") Long beforeId,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT)
                    @Range(min = 1, max = Pagination.MAX_LIMIT, message = "{param.limit.range}")
                    int limit) {
        return postService.findByAuthor(id, Pagination.before(beforeId), limit);
    }

    @GetMapping("/{id}/liked-posts")
    @Operation(operationId = "getUserLikedPosts", summary = "List posts a user likes, newest first (manyToMany)")
    public List<PostSummary> findLikedPosts(
            @PathVariable @Positive(message = "{param.positive}") long id,
            @RequestParam(required = false) @Positive(message = "{param.positive}") Long beforeId,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT)
                    @Range(min = 1, max = Pagination.MAX_LIMIT, message = "{param.limit.range}")
                    int limit) {
        return postService.findLikedBy(id, Pagination.before(beforeId), limit);
    }

    @GetMapping("/{id}/images")
    @Operation(operationId = "getUserImages", summary = "List images of a user's posts, newest first (hasManyThrough)")
    public List<ImageResponse> findImages(
            @PathVariable @Positive(message = "{param.positive}") long id,
            @RequestParam(required = false) @Positive(message = "{param.positive}") Long beforeId,
            @RequestParam(defaultValue = Pagination.DEFAULT_LIMIT)
                    @Range(min = 1, max = Pagination.MAX_LIMIT, message = "{param.limit.range}")
                    int limit) {
        return userService.findImages(id, Pagination.before(beforeId), limit);
    }
}
