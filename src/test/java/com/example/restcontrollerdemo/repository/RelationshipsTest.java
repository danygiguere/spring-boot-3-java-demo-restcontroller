package com.example.restcontrollerdemo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.restcontrollerdemo.domain.entity.Image;
import com.example.restcontrollerdemo.domain.entity.Post;
import com.example.restcontrollerdemo.domain.entity.User;
import com.example.restcontrollerdemo.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Each relationship of the model, on the seed: user u belongs to enterprise (u - 1) / 10 + 1; post p belongs to user
 * (p - 1) / 4 + 1, has (p % 4) images and (p % 7) likes.
 */
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
    void belongsTo_post5IsBobsInEnterprise1() {
        Post post = postRepository.findById(5L).orElseThrow();
        User author = userRepository.findById(post.userId()).orElseThrow();

        assertThat(author.name()).isEqualTo("Bob");
        assertThat(author.enterpriseId()).isEqualTo(1L);
    }

    @Test
    void hasOne_eachUserHasOneProfile() {
        assertThat(profileRepository.findByUserId(1L).orElseThrow().userId()).isEqualTo(1L);
    }

    @Test
    void hasMany_userPostsAndPostImagesInOrder() {
        List<Post> posts = postRepository.findByAuthor(1L, 1L, Long.MAX_VALUE, 100);
        List<Image> images = imageRepository.findAllByPostIdOrderByPositionAsc(3L);

        assertThat(posts).extracting(Post::id).containsExactly(4L, 3L, 2L, 1L);
        assertThat(images).extracting(Image::position).containsExactly(1, 2, 3);
        assertThat(imageRepository.findAllByPostIdOrderByPositionAsc(4L)).isEmpty();
    }

    @Test
    void hasManyThrough_enterprise3Has40Posts() {
        List<Post> posts = postRepository.findFeed(3L, Long.MAX_VALUE, 100);

        assertThat(posts).hasSize(40);
        assertThat(posts).allSatisfy(post -> assertThat(post.userId()).isBetween(21L, 30L));
    }

    @Test
    void manyToMany_bothDirections() {
        List<User> likers = userRepository.findLikersInEnterprise(6L, 1L, 0, 100);

        assertThat(likers).hasSize(6);
        assertThat(postLikeRepository.countByPostId(6L)).isEqualTo(6L);
        for (User liker : likers) {
            assertThat(postRepository.findLikedBy(liker.id(), 1L, Long.MAX_VALUE, 100))
                    .extracting(Post::id)
                    .contains(6L);
        }
    }

    @Test
    void tenantScopedQueries_returnNothingAcrossEnterprises() {
        assertThat(userRepository.findInEnterprise(11L, 1L)).isEmpty();
        assertThat(postRepository.findVisible(41L, 1L)).isEmpty();
        assertThat(postRepository.findVisible(41L, 2L)).isPresent();
    }
}
