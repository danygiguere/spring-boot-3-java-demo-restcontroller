package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/**
 * Tenant-facing queries take the caller's {@code enterpriseId}: a user of another enterprise behaves exactly like a
 * missing one. Spring Data JDBC has no lazy relations, so every relation is an explicit query.
 */
public interface UserRepository extends ListCrudRepository<User, Long> {

    /** Cross-tenant by design: dev token lookup only (email is UNIQUE). */
    Optional<User> findByEmail(String email);

    @Query("SELECT * FROM users WHERE id = :id AND enterprise_id = :enterpriseId")
    Optional<User> findInEnterprise(Long id, Long enterpriseId);

    /** hasMany (enterprise → users), oldest first, keyset-paginated. */
    @Query(
            """
            SELECT * FROM users
            WHERE enterprise_id = :enterpriseId AND id > :afterId
            ORDER BY id
            LIMIT :limit
            """)
    List<User> findPageInEnterprise(Long enterpriseId, long afterId, int limit);

    /** Bulk load by ids; the caller bounds the list (one page of authors). */
    @Query("SELECT * FROM users WHERE id IN (:ids) AND enterprise_id = :enterpriseId")
    List<User> findAllInEnterprise(Collection<Long> ids, Long enterpriseId);

    /** manyToMany — users who like a post, through post_likes. */
    @Query(
            """
            SELECT u.* FROM users u
            JOIN post_likes l ON l.user_id = u.id
            WHERE l.post_id = :postId AND u.enterprise_id = :enterpriseId AND u.id > :afterId
            ORDER BY u.id
            LIMIT :limit
            """)
    List<User> findLikersInEnterprise(Long postId, Long enterpriseId, long afterId, int limit);
}
