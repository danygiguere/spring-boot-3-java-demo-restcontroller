package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.PostLike;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface PostLikeRepository extends ListCrudRepository<PostLike, Long> {

    long countByPostId(Long postId);

    /** Idempotent: liking twice leaves one row, enforced by UNIQUE (post_id, user_id). */
    @Modifying
    @Query(
            """
            INSERT INTO post_likes (post_id, user_id)
            VALUES (:postId, :userId)
            ON CONFLICT (post_id, user_id) DO NOTHING
            """)
    int likeIfAbsent(Long postId, Long userId);

    /** Idempotent; targets at most one row (UNIQUE (post_id, user_id)). */
    @Modifying
    @Query("DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId")
    int unlike(Long postId, Long userId);
}
