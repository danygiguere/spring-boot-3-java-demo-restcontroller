package com.example.restcontrollerdemo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.restcontrollerdemo.domain.entity.Image;
import com.example.restcontrollerdemo.domain.entity.Post;
import com.example.restcontrollerdemo.domain.entity.User;
import com.example.restcontrollerdemo.dto.PostSummaryRow;
import com.example.restcontrollerdemo.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Each relationship of the model, on the seed: post p belongs to user (p - 1) / 4 + 1, has (p % 4) images and (p % 7) likes. */
class RelationshipsTest extends IntegrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProfileRepository profileRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    ImageRepository imageRepository;

    @Autowired
    PostLikeRepository postLikeRepository;

    @Test
    void belongsTo_post5IsBobs() {
        Post post = postRepository.findById(5L).orElseThrow();

        assertThat(userRepository.findById(post.userId()).orElseThrow().name()).isEqualTo("Bob");
    }

    @Test
    void hasOne_eachUserHasOneProfile() {
        assertThat(profileRepository.findByUserId(1L).orElseThrow().userId()).isEqualTo(1L);
    }

    @Test
    void hasMany_userPostsAndPostImagesInOrder() {
        List<PostSummaryRow> posts = postRepository.findByAuthor(1L, Long.MAX_VALUE, 100);
        List<Image> images = imageRepository.findAllByPostIdOrderByPositionAsc(3L);

        assertThat(posts).extracting(PostSummaryRow::id).containsExactly(4L, 3L, 2L, 1L);
        assertThat(posts).extracting(PostSummaryRow::imageCount).containsExactly(0L, 3L, 2L, 1L);
        assertThat(posts).allSatisfy(post -> assertThat(post.authorName()).isEqualTo("Alice"));
        assertThat(images).extracting(Image::position).containsExactly(1, 2, 3);
        assertThat(imageRepository.findAllByPostIdOrderByPositionAsc(4L)).isEmpty();
    }

    @Test
    void hasManyThrough_user1Has6ImagesThroughPosts1To3() {
        List<Image> images = imageRepository.findByUser(1L, Long.MAX_VALUE, 100);

        assertThat(images).extracting(Image::id).containsExactly(6L, 5L, 4L, 3L, 2L, 1L);
        assertThat(images).extracting(Image::postId).containsOnly(3L, 2L, 1L);
        assertThat(imageRepository.findByUser(1L, 4L, 2)).extracting(Image::id).containsExactly(3L, 2L);
    }

    @Test
    void manyToMany_bothDirections() {
        List<User> likers = userRepository.findLikers(6L, 0, 100);

        assertThat(likers).hasSize(6);
        assertThat(postLikeRepository.countByPostId(6L)).isEqualTo(6L);
        for (User liker : likers) {
            assertThat(postRepository.findLikedBy(liker.id(), Long.MAX_VALUE, 100))
                    .extracting(PostSummaryRow::id)
                    .contains(6L);
        }
    }
}
