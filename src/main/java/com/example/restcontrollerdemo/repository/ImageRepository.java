package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.Image;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

public interface ImageRepository extends ListCrudRepository<Image, Long> {

    /** hasMany (post → images), at most 10 rows (CHECK on position). */
    List<Image> findAllByPostIdOrderByPositionAsc(Long postId);

    /** hasManyThrough (user → posts → images), newest first, keyset-paginated. */
    @Query(
            """
            SELECT i.* FROM images i
            JOIN posts p ON p.id = i.post_id
            WHERE p.user_id = :userId AND i.id < :beforeId
            ORDER BY i.id DESC
            LIMIT :limit
            """)
    List<Image> findByUser(Long userId, long beforeId, int limit);
}
