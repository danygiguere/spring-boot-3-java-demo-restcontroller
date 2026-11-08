package com.example.restcontrollerdemo.service;

import com.example.restcontrollerdemo.domain.entity.Post;
import com.example.restcontrollerdemo.domain.entity.User;
import com.example.restcontrollerdemo.dto.PostCount;
import com.example.restcontrollerdemo.dto.PostSummary;
import com.example.restcontrollerdemo.repository.ImageRepository;
import com.example.restcontrollerdemo.repository.PostLikeRepository;
import com.example.restcontrollerdemo.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Turns a page of posts into summaries with a constant number of queries — authors, image counts and like counts are
 * each loaded in bulk (three queries), then joined in memory through hash maps.
 */
@Component
public class PostSummaryLoader {

    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final PostLikeRepository postLikeRepository;

    public PostSummaryLoader(
            UserRepository userRepository, ImageRepository imageRepository, PostLikeRepository postLikeRepository) {
        this.userRepository = userRepository;
        this.imageRepository = imageRepository;
        this.postLikeRepository = postLikeRepository;
    }

    /** Keeps the order of {@code page}. The page is already bounded by the caller's limit. */
    public List<PostSummary> load(List<Post> page, long enterpriseId) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = page.stream().map(Post::id).toList();
        List<Long> authorIds = page.stream().map(Post::userId).distinct().toList();

        Map<Long, User> authors = userRepository.findAllInEnterprise(authorIds, enterpriseId).stream()
                .collect(Collectors.toMap(User::id, Function.identity()));
        Map<Long, Long> imageCounts = toMap(imageRepository.countByPostIds(postIds));
        Map<Long, Long> likeCounts = toMap(postLikeRepository.countByPostIds(postIds));

        return page.stream()
                // An author deleted between the two queries takes their posts with them (ON DELETE CASCADE).
                .filter(post -> authors.containsKey(post.userId()))
                .map(post -> PostSummary.of(
                        post,
                        authors.get(post.userId()),
                        imageCounts.getOrDefault(post.id(), 0L),
                        likeCounts.getOrDefault(post.id(), 0L)))
                .toList();
    }

    private static Map<Long, Long> toMap(List<PostCount> counts) {
        return counts.stream().collect(Collectors.toMap(PostCount::postId, PostCount::total));
    }
}
