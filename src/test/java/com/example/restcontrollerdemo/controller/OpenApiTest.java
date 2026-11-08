package com.example.restcontrollerdemo.controller;

import com.example.restcontrollerdemo.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class OpenApiTest extends IntegrationTest {

    @Test
    void swaggerUi_redirects() {
        webTestClient.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
    }

    @Test
    void apiDocs_listEveryRouteWithTypedResponses() {
        webTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/auth/dev-token'].post")
                .exists()
                .jsonPath("$.paths['/enterprises/current'].get")
                .exists()
                .jsonPath("$.paths['/users'].get")
                .exists()
                .jsonPath("$.paths['/users/me/profile'].put")
                .exists()
                .jsonPath("$.paths['/users/{id}/liked-posts'].get")
                .exists()
                .jsonPath("$.paths['/posts'].post")
                .exists()
                .jsonPath("$.paths['/posts/{id}'].delete")
                .exists()
                .jsonPath("$.paths['/posts/{id}/likes'].put")
                .exists()
                // Inferred from the signature PostDetails, no annotation needed.
                .jsonPath("$.paths['/posts/{id}'].get.responses['200'].content['*/*'].schema['$ref']")
                .isEqualTo("#/components/schemas/PostDetails")
                // AuthenticatedUser comes from the token: not documented as a parameter.
                .jsonPath("$.paths['/users/me'].get.parameters")
                .doesNotExist()
                .jsonPath("$.components.securitySchemes.bearerAuth")
                .exists();
    }
}
