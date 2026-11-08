package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.Post;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * A post is visible to users of its author's enterprise. Every read takes the caller's {@code enterpriseId}; every
 * write also takes the author's {@code userId}, so the WHERE clause is the permission check and targets at most one
 * row (primary key).
 */
public interface PostRepository extends ListCrudRepository<Post, Long> {

    @Query(
            """
            SELECT p.* FROM posts p
            JOIN users u ON u.id = p.user_id
            WHERE p.id = :postId AND u.enterprise_id = :enterpriseId
            """)
    Optional<Post> findVisible(Long postId, Long enterpriseId);

    /** hasManyThrough (enterprise → users → posts), newest first, keyset-paginated. */
    @Query(
            """
            SELECT p.* FROM posts p
            JOIN users u ON u.id = p.user_id
            WHERE u.enterprise_id = :enterpriseId AND p.id < :beforeId
            ORDER BY p.id DESC
            LIMIT :limit
            """)
    List<Post> findFeed(Long enterpriseId, long beforeId, int limit);

    /** hasMany (user → posts). */
    @Query(
            """
            SELECT p.* FROM posts p
            JOIN users u ON u.id = p.user_id
            WHERE p.user_id = :userId AND u.enterprise_id = :enterpriseId AND p.id < :beforeId
            ORDER BY p.id DESC
            LIMIT :limit
            """)
    List<Post> findByAuthor(Long userId, Long enterpriseId, long beforeId, int limit);

    /** manyToMany — posts liked by a user, through post_likes. */
    @Query(
            """
            SELECT p.* FROM posts p
            JOIN users u ON u.id = p.user_id
            JOIN post_likes l ON l.post_id = p.id
            WHERE l.user_id = :userId AND u.enterprise_id = :enterpriseId AND p.id < :beforeId
            ORDER BY p.id DESC
            LIMIT :limit
            """)
    List<Post> findLikedBy(Long userId, Long enterpriseId, long beforeId, int limit);

    /** Returns the number of rows updated: 1, or 0 when the post is gone or not the caller's. */
    @Modifying
    @Query("UPDATE posts SET content = :content WHERE id = :id AND user_id = :userId")
    int updateContent(Long id, Long userId, String content);

    /** Returns the number of rows deleted: 1, or 0 when the post is gone or not the caller's. */
    @Modifying
    @Query("DELETE FROM posts WHERE id = :id AND user_id = :userId")
    int deleteOwned(Long id, Long userId);
}
