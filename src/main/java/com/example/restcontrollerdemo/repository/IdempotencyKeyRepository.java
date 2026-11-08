package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.IdempotencyKey;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface IdempotencyKeyRepository extends ListCrudRepository<IdempotencyKey, Long> {

    /**
     * Claims the key atomically and returns its row id; empty when the key was already claimed. A concurrent claim of
     * the same key waits on the UNIQUE index until the first transaction ends, then sees the committed row.
     *
     * <p>An INSERT that returns rows is a query, not a {@code @Modifying} statement; {@code @Transactional} keeps it out
     * of the read-only transaction Spring Data applies to query methods.
     */
    @Transactional
    @Query(
            """
            INSERT INTO idempotency_keys (user_id, idempotency_key, request_hash, created_at)
            VALUES (:userId, :key, :requestHash, :createdAt)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            RETURNING id
            """)
    Optional<Long> claim(Long userId, String key, String requestHash, Instant createdAt);

    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Modifying
    @Query("UPDATE idempotency_keys SET post_id = :postId WHERE id = :id")
    int attachPost(Long id, Long postId);
}
