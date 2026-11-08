package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.Profile;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ProfileRepository extends ListCrudRepository<Profile, Long> {

    /** hasOne — an {@link Optional} is safe because of UNIQUE (user_id). */
    Optional<Profile> findByUserId(Long userId);

    /**
     * Create or replace in one atomic statement: no check-then-insert race, idempotent. A write that returns rows, so
     * {@code @Transactional} (read-write) rather than {@code @Modifying}.
     */
    @Transactional
    @Query(
            """
            INSERT INTO profiles (user_id, bio, avatar_url)
            VALUES (:userId, :bio, :avatarUrl)
            ON CONFLICT (user_id) DO UPDATE SET bio = EXCLUDED.bio, avatar_url = EXCLUDED.avatar_url
            RETURNING *
            """)
    Profile upsert(Long userId, String bio, String avatarUrl);
}
