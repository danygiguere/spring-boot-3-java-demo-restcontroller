-- Development seed (only when FLYWAY_LOCATIONS includes classpath:db/seed). Repeatable migration: Flyway re-runs it
-- whenever its content changes, and it starts by EMPTYING every table — never point it at a real database.
--
-- Deterministic volumes:
--   50 users, each with a profile
--   200 posts (post p belongs to user (p - 1) / 4 + 1)
--   300 images (post p has p % 4 images)
--   598 likes  (post p is liked by p % 7 users)
--
-- Named users:
--   user 1  Alice (alice@example.com) → posts 1 to 4
--   user 2  Bob   (bob@example.com)   → posts 5 to 8

TRUNCATE idempotency_keys, post_likes, images, posts, profiles, users RESTART IDENTITY CASCADE;

INSERT INTO users (name, email)
SELECT 'User ' || u,
       'user' || u || '@example.com'
FROM generate_series(1, 50) AS u;

UPDATE users SET name = 'Alice', email = 'alice@example.com' WHERE id = 1;
UPDATE users SET name = 'Bob', email = 'bob@example.com' WHERE id = 2;

INSERT INTO profiles (user_id, bio, avatar_url)
SELECT u,
       'Bio of user ' || u,
       'https://images.example.com/avatars/' || u || '.png'
FROM generate_series(1, 50) AS u;

INSERT INTO posts (user_id, content, created_at)
SELECT (p - 1) / 4 + 1,
       'Post #' || p,
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + p * INTERVAL '1 hour'
FROM generate_series(1, 200) AS p;

INSERT INTO images (post_id, url, position)
SELECT p,
       'https://images.example.com/posts/' || p || '/' || k || '.jpg',
       k
FROM generate_series(1, 200) AS p,
     generate_series(1, 3) AS k
WHERE k <= p % 4
ORDER BY p, k;

-- ((p * 13 + k * 7) % 50) is distinct for k = 1..6, so a post is liked at most once per user.
INSERT INTO post_likes (post_id, user_id, created_at)
SELECT p,
       ((p * 13 + k * 7) % 50) + 1,
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + p * INTERVAL '1 hour' + k * INTERVAL '1 minute'
FROM generate_series(1, 200) AS p,
     generate_series(1, 6) AS k
WHERE k <= p % 7
ORDER BY p, k;
