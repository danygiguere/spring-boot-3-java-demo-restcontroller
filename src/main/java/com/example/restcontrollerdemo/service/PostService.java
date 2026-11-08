package com.example.restcontrollerdemo.service;

import com.example.restcontrollerdemo.domain.entity.IdempotencyKey;
import com.example.restcontrollerdemo.domain.entity.Image;
import com.example.restcontrollerdemo.domain.entity.Post;
import com.example.restcontrollerdemo.domain.entity.User;
import com.example.restcontrollerdemo.dto.CreatePostRequest;
import com.example.restcontrollerdemo.dto.ImageResponse;
import com.example.restcontrollerdemo.dto.PostDetails;
import com.example.restcontrollerdemo.dto.PostSummary;
import com.example.restcontrollerdemo.dto.UpdatePostRequest;
import com.example.restcontrollerdemo.dto.UserResponse;
import com.example.restcontrollerdemo.exception.AppException;
import com.example.restcontrollerdemo.repository.IdempotencyKeyRepository;
import com.example.restcontrollerdemo.repository.ImageRepository;
import com.example.restcontrollerdemo.repository.PostLikeRepository;
import com.example.restcontrollerdemo.repository.PostRepository;
import com.example.restcontrollerdemo.repository.UserRepository;
import com.example.restcontrollerdemo.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Posts are visible to the author's enterprise and writable by the author only. Both checks happen here, at the point
 * of action, from the verified token — never from ids in the request body.
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final ImageRepository imageRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final UserService userService;
    private final PostSummaryLoader summaryLoader;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PostService(
            PostRepository postRepository,
            ImageRepository imageRepository,
            PostLikeRepository postLikeRepository,
            UserRepository userRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            UserService userService,
            PostSummaryLoader summaryLoader,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.postRepository = postRepository;
        this.imageRepository = imageRepository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.userService = userService;
        this.summaryLoader = summaryLoader;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    // --- Reads ----------------------------------------------------------------------------------------------------

    public List<PostSummary> feed(AuthenticatedUser me, long beforeId, int limit) {
        return summaryLoader.load(postRepository.findFeed(me.enterpriseId(), beforeId, limit), me.enterpriseId());
    }

    public List<PostSummary> findByAuthor(AuthenticatedUser me, long userId, long beforeId, int limit) {
        userService.requireColleague(me, userId);
        return summaryLoader.load(
                postRepository.findByAuthor(userId, me.enterpriseId(), beforeId, limit), me.enterpriseId());
    }

    public List<PostSummary> findLikedBy(AuthenticatedUser me, long userId, long beforeId, int limit) {
        userService.requireColleague(me, userId);
        return summaryLoader.load(
                postRepository.findLikedBy(userId, me.enterpriseId(), beforeId, limit), me.enterpriseId());
    }

    /** The post, its author, images and like count: four short indexed queries, one after the other. */
    public PostDetails findById(AuthenticatedUser me, long id) {
        Post post = requireVisible(me, id);
        return PostDetails.of(
                post,
                userService.requireColleague(me, post.userId()),
                imageRepository.findAllByPostIdOrderByPositionAsc(id),
                postLikeRepository.countByPostId(id));
    }

    public List<ImageResponse> findImages(AuthenticatedUser me, long id) {
        requireVisible(me, id);
        return imageRepository.findAllByPostIdOrderByPositionAsc(id).stream()
                .map(ImageResponse::of)
                .toList();
    }

    public List<UserResponse> findLikers(AuthenticatedUser me, long id, long afterId, int limit) {
        requireVisible(me, id);
        return userRepository.findLikersInEnterprise(id, me.enterpriseId(), afterId, limit).stream()
                .map(UserResponse::of)
                .toList();
    }

    // --- Writes ---------------------------------------------------------------------------------------------------

    /**
     * Creates the post and its images in ONE transaction, together with the claim on {@code idempotencyKey}. A retry
     * with the same key and body returns the post created the first time; the same key with another body is a 409.
     */
    public CreatedPost create(AuthenticatedUser me, String idempotencyKey, CreatePostRequest request) {
        String requestHash = hash(request);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // PostgreSQL precision

        Claim claim = transactionTemplate.execute(status -> {
            Optional<Long> keyId = idempotencyKeyRepository.claim(me.userId(), idempotencyKey, requestHash, now);
            if (keyId.isPresent()) {
                PostDetails details = insertPost(me, request, now);
                idempotencyKeyRepository.attachPost(keyId.get(), details.id());
                return new Claim.Inserted(details);
            }
            IdempotencyKey existing = idempotencyKeyRepository
                    .findByUserIdAndIdempotencyKey(me.userId(), idempotencyKey)
                    .orElseThrow(); // claimed by a committed transaction: the row is visible
            return new Claim.AlreadyCreated(replayedPostId(existing, requestHash));
        });

        // The replayed post is read after the transaction ends, so the transaction stays as short as possible.
        return switch (claim) {
            case Claim.Inserted inserted -> new CreatedPost(inserted.post(), false);
            case Claim.AlreadyCreated replay -> new CreatedPost(findById(me, replay.postId()), true);
        };
    }

    /** What the transaction of {@link #create} did with the Idempotency-Key. */
    private sealed interface Claim {
        record Inserted(PostDetails post) implements Claim {}

        record AlreadyCreated(long postId) implements Claim {}
    }

    /** Only the author may edit. Setting the content to an absolute value is naturally idempotent. */
    public PostDetails update(AuthenticatedUser me, long id, UpdatePostRequest request) {
        requireAuthor(me, id);
        if (postRepository.updateContent(id, me.userId(), request.content()) != 1) {
            throw postNotFound(id);
        }
        return findById(me, id);
    }

    /** Only the author may delete. Images and likes go with the post (ON DELETE CASCADE). */
    public void delete(AuthenticatedUser me, long id) {
        requireAuthor(me, id);
        if (postRepository.deleteOwned(id, me.userId()) != 1) {
            throw postNotFound(id);
        }
    }

    /** Idempotent: liking twice keeps one like. */
    public void like(AuthenticatedUser me, long id) {
        requireVisible(me, id);
        try {
            postLikeRepository.likeIfAbsent(id, me.userId());
        } catch (DataIntegrityViolationException e) {
            // The post was deleted between the visibility check and the insert (foreign key violation).
            throw new AppException.NotFound(e, "error.post.not.found", id);
        }
    }

    /** Idempotent: unliking a post that is not liked changes nothing. */
    public void unlike(AuthenticatedUser me, long id) {
        requireVisible(me, id);
        postLikeRepository.unlike(id, me.userId());
    }

    // --- Helpers --------------------------------------------------------------------------------------------------

    private PostDetails insertPost(AuthenticatedUser me, CreatePostRequest request, Instant now) {
        User author = userService.requireColleague(me, me.userId());
        Post post = postRepository.save(new Post(null, author.id(), request.content(), now));
        List<Image> images = imageRepository.saveAll(toImages(post.id(), request.imageUrls()));
        return PostDetails.of(post, author, images, 0);
    }

    private static List<Image> toImages(Long postId, List<String> urls) {
        return IntStream.range(0, urls.size())
                .mapToObj(i -> new Image(null, postId, urls.get(i), i + 1))
                .toList();
    }

    private static Long replayedPostId(IdempotencyKey existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new AppException.Conflict("error.idempotency.key.reused");
        }
        if (existing.postId() == null) {
            throw new AppException.Conflict("error.idempotency.key.gone");
        }
        return existing.postId();
    }

    /** Length-prefixed fields, so two different requests can never produce the same input. */
    private static String hash(CreatePostRequest request) {
        StringBuilder canonical = new StringBuilder();
        appendField(canonical, request.content());
        request.imageUrls().forEach(url -> appendField(canonical, url));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    private static void appendField(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }

    private Post requireVisible(AuthenticatedUser me, long id) {
        return postRepository.findVisible(id, me.enterpriseId()).orElseThrow(() -> postNotFound(id));
    }

    /** Visible but not authored by the caller is a 403: the post's existence is no secret within the enterprise. */
    private Post requireAuthor(AuthenticatedUser me, long id) {
        Post post = requireVisible(me, id);
        if (!post.userId().equals(me.userId())) {
            throw new AppException.Forbidden("error.post.not.author", id);
        }
        return post;
    }

    private static AppException postNotFound(long id) {
        return new AppException.NotFound("error.post.not.found", id);
    }
}
