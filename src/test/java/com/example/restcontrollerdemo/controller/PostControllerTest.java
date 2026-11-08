package com.example.restcontrollerdemo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.restcontrollerdemo.dto.PostDetails;
import com.example.restcontrollerdemo.dto.PostSummary;
import com.example.restcontrollerdemo.support.IntegrationTest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        PostDetails byColleague = create(as(COLLEAGUE), key, NEW_POST);

        assertThat(byColleague.id()).isNotEqualTo(byAuthor.id());
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
                .isEqualTo("/errors/param-invalid")
                .jsonPath("$.errors['Idempotency-Key'][0]")
                .isEqualTo("This parameter is required");
    }

    @Test
    void create_malformedIdempotencyKey_returns422() {
        as(AUTHOR)
                .post()
                .uri("/posts")
                .header("Idempotency-Key", "short")
                .bodyValue(NEW_POST)
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.errors['Idempotency-Key'][0]")
                .isEqualTo("Must be 8 to 128 characters: letters, digits, '-' or '_'");
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
        List<String> elevenImages = Collections.nCopies(11, "https://images.example.com/x.jpg");
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
    void findById_postOfAnotherEnterprise_isNotFound() {
        PostDetails post = create(as(AUTHOR), UUID.randomUUID().toString(), NEW_POST);

        as(OUTSIDER)
                .get()
                .uri("/posts/{id}", post.id())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/post-not-found");
    }

    @Test
    void update_onlyByAuthor() {
        PostDetails post = create(as(AUTHOR), UUID.randomUUID().toString(), NEW_POST);

        as(COLLEAGUE)
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

        as(COLLEAGUE)
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
        WebTestClient colleague = as(COLLEAGUE);

        colleague
                .put()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        colleague
                .put()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        colleague
                .get()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(List.of(4));

        colleague
                .delete()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        colleague
                .delete()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        colleague
                .get()
                .uri("/posts/{id}", post.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.likeCount")
                .isEqualTo(0);
    }

    @Test
    void like_postOfAnotherEnterprise_isNotFound() {
        PostDetails post = create(as(AUTHOR), UUID.randomUUID().toString(), NEW_POST);

        as(OUTSIDER)
                .put()
                .uri("/posts/{id}/likes", post.id())
                .exchange()
                .expectStatus()
                .isNotFound();
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
    void feed_isScopedToCallersEnterprise() {
        List<PostSummary> page = as(EVE).get()
                .uri("/posts?limit=100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBodyList(PostSummary.class)
                .returnResult()
                .getResponseBody();

        assertThat(page).isNotEmpty();
        assertThat(page).allSatisfy(post -> assertThat(post.author().id()).isBetween(11L, 20L));
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
