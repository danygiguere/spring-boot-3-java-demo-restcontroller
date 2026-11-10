package com.example.restcontrollerdemo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.restcontrollerdemo.dto.PostDetails;
import com.example.restcontrollerdemo.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class PostControllerTest extends IntegrationTest {

    private static final Map<String, Object> NEW_POST = Map.of(
            "content",
            "Hello",
            "imageUrls",
            List.of("https://images.example.com/a.jpg", "https://images.example.com/b.jpg"));

    @Test
    void create_storesPostAndImagesAsTheAuthenticatedUser() {
        String key = UUID.randomUUID().toString();
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", key)
                .bodyValue(NEW_POST)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueMatches("Location", "/posts/\\d+")
                .expectHeader()
                .valueEquals("Idempotent-Replayed", "false")
                .expectBody()
                .jsonPath("$.author.id")
                .isEqualTo(3)
                .jsonPath("$.images[*].position")
                .isEqualTo(List.of(1, 2))
                .jsonPath("$.likeCount")
                .isEqualTo(0);
    }

    @Test
    void create_retriedWithSameKey_returnsTheSamePost() {
        WebTestClient author = as(AUTHOR);
        String key = UUID.randomUUID().toString();
        PostDetails first = create(author, key, NEW_POST);

        PostDetails retry = author.post()
                .uri("/posts")
                .header("Idempotency-Key", key)
                .bodyValue(NEW_POST)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .valueEquals("Idempotent-Replayed", "true")
                .expectBody(PostDetails.class)
                .returnResult()
                .getResponseBody();

        assertThat(retry).isEqualTo(first);
    }

    @Test
    void create_sameKeyWithDifferentBody_returns409() {
        WebTestClient author = as(AUTHOR);
        String key = UUID.randomUUID().toString();
        create(author, key, NEW_POST);

        author.post()
                .uri("/posts")
                .header("Idempotency-Key", key)
                .bodyValue(Map.of("content", "Something else"))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/idempotency-key-reused");
    }

    @Test
    void create_sameKeyFromAnotherUser_isIndependent() {
        String key = UUID.randomUUID().toString();
        PostDetails byAuthor = create(as(AUTHOR), key, NEW_POST);
        PostDetails byOtherUser = create(as(OTHER_USER), key, NEW_POST);

        assertThat(byOtherUser.id()).isNotEqualTo(byAuthor.id());
    }

    @Test
    void create_withoutIdempotencyKey_returns400() {
        as(AUTHOR)
                .post()
                .uri("/posts")
                .bodyValue(NEW_POST)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/header-required");
    }

    @Test
    void create_malformedIdempotencyKey_returns400() {
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", "short")
                .bodyValue(NEW_POST)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/idempotency-key-invalid");
    }

    @Test
    void create_validationMessages_followAcceptLanguage() {
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Accept-Language", "fr")
                .bodyValue(Map.of("content", "x".repeat(5001)))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Échec de la validation — veuillez corriger les erreurs ci-dessus.")
                .jsonPath("$.errors.content[0]")
                .isEqualTo("Le contenu ne doit pas dépasser 5000 caractères");
    }

    @Test
    void create_invalidBody_returns422WithFieldErrors() {
        List<String> elevenImages = IntStream.range(0, 11)
                .mapToObj(i -> "https://images.example.com/" + i + ".jpg")
                .toList();
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(Map.of("content", " ", "imageUrls", elevenImages))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors.content[0]")
                .isEqualTo("Content is required")
                .jsonPath("$.errors.imageUrls[0]")
                .isEqualTo("A post can have at most 10 images");

        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(Map.of("content", "ok", "imageUrls", List.of("http://insecure.example.com/x.jpg")))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors['imageUrls[0]'][0]")
                .isEqualTo("Image URL must be a valid https URL");
    }

    @Test
    void create_duplicateImageUrls_returns422() {
        String url = "https://images.example.com/x.jpg";
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(Map.of("content", "ok", "imageUrls", List.of(url, url)))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors.imageUrls[0]")
                .isEqualTo("Image URLs must be unique");
    }

    @Test
    void create_authorFieldInBody_isRejected() {
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .bodyValue(Map.of("content", "Hello", "userId", 1))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void create_bodyAboveLimit_returns413() {
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "x".repeat(70_000)))
                .exchange()
                .expectStatus()
                .isEqualTo(413);
    }

    @Test
    void findById_isVisibleToEveryUser() {
        PostDetails post = create(as(AUTHOR), UUID.randomUUID().toString(), NEW_POST);

        as(OTHER_USER)
                .get()
                .uri("/posts/{id}", post.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.author.id")
                .isEqualTo(3)
                .jsonPath("$.images[*].postId")
                .isEqualTo(List.of(post.id().intValue(), post.id().intValue()));
    }

    @Test
    void update_onlyByAuthor() {
        PostDetails post = create(as(AUTHOR), UUID.randomUUID().toString(), NEW_POST);

        as(OTHER_USER)
                .put()
                .uri("/posts/{id}", post.id())
                .bodyValue(Map.of("content", "Hijacked"))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/post-not-author");

        as(AUTHOR)
                .put()
                .uri("/posts/{id}", post.id())
                .bodyValue(Map.of("content", "Edited"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content")
                .isEqualTo("Edited")
                .jsonPath("$.images.length()")
                .isEqualTo(2);
    }

    @Test
    void delete_onlyByAuthor_andKeyCannotRecreateIt() {
        WebTestClient author = as(AUTHOR);
        String key = UUID.randomUUID().toString();
        PostDetails post = create(author, key, NEW_POST);

        as(OTHER_USER)
                .delete()
                .uri("/posts/{id}", post.id())
                .exchange()
                .expectStatus()
                .isForbidden();
        author.delete().uri("/posts/{id}", post.id()).exchange().expectStatus().isNoContent();
        author.get().uri("/posts/{id}", post.id()).exchange().expectStatus().isNotFound();

        author.post()
                .uri("/posts")
                .header("Idempotency-Key", key)
                .bodyValue(NEW_POST)
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/idempotency-key-gone");
    }

    @Test
    void likeAndUnlike_areIdempotent() {
        PostDetails post = create(as(AUTHOR), UUID.randomUUID().toString(), NEW_POST);
        WebTestClient user = as(OTHER_USER);

        user.put().uri("/posts/{id}/likes", post.id()).exchange().expectStatus().isNoContent();
        user.put().uri("/posts/{id}/likes", post.id()).exchange().expectStatus().isNoContent();
        user.get()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(List.of(4));

        user.delete()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        user.delete()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        user.get()
                .uri("/posts/{id}", post.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.likeCount")
                .isEqualTo(0);
    }

    @Test
    void like_missingPost_isNotFound() {
        as(OTHER_USER)
                .put()
                .uri("/posts/999999/likes")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/post-not-found");
    }

    @Test
    void feed_isKeysetPaginatedWithBatchedCounts() {
        // Post p has (p % 4) images and (p % 7) likes in the seed.
        as(ALICE)
                .get()
                .uri("/posts?beforeId=8&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(List.of(7, 6, 5))
                .jsonPath("$[0].imageCount")
                .isEqualTo(3)
                .jsonPath("$[0].likeCount")
                .isEqualTo(0)
                .jsonPath("$[1].likeCount")
                .isEqualTo(6)
                .jsonPath("$[2].author.name")
                .isEqualTo("Bob");
    }

    @Test
    void feed_includesEveryUsersPosts() {
        as(ALICE)
                .get()
                .uri("/posts?beforeId=6&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(List.of(5, 4, 3))
                .jsonPath("$[*].author.name")
                .isEqualTo(List.of("Bob", "Alice", "Alice"));
    }

    @Test
    void errorCorrelationId_matchesResponseHeader() {
        var result = as(ALICE)
                .get()
                .uri("/posts/999999")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Map.class)
                .returnResult();

        assertThat(result.getResponseBody().get("correlationId"))
                .isEqualTo(result.getResponseHeaders().getFirst("X-Correlation-Id"));
    }

    private static PostDetails create(WebTestClient client, String key, Map<String, Object> body) {
        return client.post()
                .uri("/posts")
                .header("Idempotency-Key", key)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(PostDetails.class)
                .returnResult()
                .getResponseBody();
    }
}
