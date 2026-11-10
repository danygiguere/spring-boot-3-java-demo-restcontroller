package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/** Spring Data JDBC has no lazy relations, so every relation is an explicit query. */
public interface UserRepository extends ListCrudRepository<User, Long> {

    /** Dev token lookup only (email is UNIQUE). */
    Optional<User> findByEmail(String email);

    /** Oldest first, keyset-paginated. */
    @Query("SELECT * FROM users WHERE id > :afterId ORDER BY id LIMIT :limit")
    List<User> findPage(long afterId, int limit);

    /** manyToMany — users who like a post, through post_likes. */
    @Query(
            """
            SELECT u.* FROM users u
            JOIN post_likes l ON l.user_id = u.id
            WHERE l.post_id = :postId AND u.id > :afterId
            ORDER BY u.id
            LIMIT :limit
            """)
    List<User> findLikers(Long postId, long afterId, int limit);
}
