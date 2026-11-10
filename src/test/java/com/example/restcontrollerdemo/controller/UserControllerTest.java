package com.example.restcontrollerdemo.controller;

import com.example.restcontrollerdemo.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class UserControllerTest extends IntegrationTest {

    @Test
    void me_returnsTheTokenUserWithEmail() {
        as(ALICE)
                .get()
                .uri("/users/me")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo(1)
                .jsonPath("$.email")
                .isEqualTo(ALICE);
    }

    @Test
    void list_isKeysetPaginated() {
        as(ALICE)
                .get()
                .uri("/users?afterId=1&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(3)
                .jsonPath("$[0].id")
                .isEqualTo(2)
                .jsonPath("$[2].id")
                .isEqualTo(4)
                // Other users are listed without their email.
                .jsonPath("$[0].email")
                .doesNotExist();
    }

    @Test
    void list_rejectsLimitAboveMaximum() {
        as(ALICE)
                .get()
                .uri("/users?limit=500")
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/validation-failed")
                .jsonPath("$.errors.limit[0]")
                .isEqualTo("Must be between 1 and 100");
    }

    @Test
    void findById_unknownUser_isNotFoundWithLocalizedDetail() {
        as(ALICE)
                .get()
                .uri("/users/999999")
                .header("Accept-Language", "fr")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/user-not-found")
                .jsonPath("$.detail")
                .isEqualTo("Utilisateur 999999 introuvable")
                .jsonPath("$.instance")
                .isEqualTo("/users/999999");
    }

    @Test
    void findById_nonNumericId_returns400() {
        as(ALICE)
                .get()
                .uri("/users/abc")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/param-invalid")
                .jsonPath("$.errors.id[0]")
                .isEqualTo("This parameter has an invalid format");
    }

    @Test
    void profile_isReadableByOtherUsers() {
        as(BOB).get()
                .uri("/users/1/profile")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.userId")
                .isEqualTo(1)
                .jsonPath("$.bio")
                .isEqualTo("Bio of user 1");
    }

    @Test
    void replaceMyProfile_upsertsTheCallersProfileOnly() {
        WebTestClient user = as(OTHER_USER);
        for (int attempt = 0; attempt < 2; attempt++) {
            user.put()
                    .uri("/users/me/profile")
                    .bodyValue(Map.of("bio", "Updated bio", "avatarUrl", "https://images.example.com/u4.png"))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.userId")
                    .isEqualTo(4)
                    .jsonPath("$.bio")
                    .isEqualTo("Updated bio");
        }
    }

    @Test
    void replaceMyProfile_rejectsNonHttpsAvatar() {
        as(OTHER_USER)
                .put()
                .uri("/users/me/profile")
                .header("Accept-Language", "fr")
                .bodyValue(Map.of("avatarUrl", "javascript:alert(1)"))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/validation-failed")
                .jsonPath("$.errors.avatarUrl[0]")
                .isEqualTo("L'URL de l'avatar doit être une URL https valide");
    }

    @Test
    void replaceMyProfile_rejectsUnknownFields() {
        as(OTHER_USER)
                .put()
                .uri("/users/me/profile")
                .bodyValue(Map.of("bio", "x", "userId", 1))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    void posts_areTheUsersPostsNewestFirst() {
        as(BOB).get()
                .uri("/users/1/posts")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(java.util.List.of(4, 3, 2, 1))
                .jsonPath("$[0].author.name")
                .isEqualTo("Alice");
    }

    @Test
    void posts_ofUnknownUser_isNotFound() {
        as(ALICE)
                .get()
                .uri("/users/999999/posts")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/user-not-found");
    }

    @Test
    void images_areTheImagesOfTheUsersPostsNewestFirst() {
        // Alice's posts 1 to 3 have 1, 2 and 3 images: ids 1 to 6.
        as(BOB).get()
                .uri("/users/1/images?beforeId=6&limit=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[*].id")
                .isEqualTo(java.util.List.of(5, 4, 3))
                .jsonPath("$[*].postId")
                .isEqualTo(java.util.List.of(3, 3, 2));
    }

    @Test
    void images_ofUnknownUser_isNotFound() {
        as(ALICE)
                .get()
                .uri("/users/999999/images")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("/errors/user-not-found");
    }
}
