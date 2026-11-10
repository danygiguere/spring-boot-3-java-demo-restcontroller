package com.example.restcontrollerdemo.support;

import com.example.restcontrollerdemo.dto.TokenResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests: one PostgreSQL Testcontainer for the whole suite, and the local settings of
 * {@code .env.example}, so the deterministic seed ({@code db/seed/R__dev_seed.sql}), {@code /auth/dev-token} and
 * Swagger are available.
 *
 * <p>Tests that write create their own posts (as users 3 and 4) and never change seeded posts, likes or the users
 * 1 and 2, so seed-based assertions hold in any order.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
            "springdoc.api-docs.enabled=true",
            "springdoc.swagger-ui.enabled=true",
            "server.address=127.0.0.1",
            "app.dev-token.enabled=true",
            "app.jwt.secret=test-only-key-at-least-32-bytes-long-0123456789"
        })
public abstract class IntegrationTest {

    public static final String ALICE = "alice@example.com"; // user 1
    public static final String BOB = "bob@example.com"; // user 2
    public static final String AUTHOR = "user3@example.com"; // user 3
    public static final String OTHER_USER = "user4@example.com"; // user 4

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine")).withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    protected WebTestClient webTestClient;

    /** A client authenticated as the seeded user with this email. */
    protected WebTestClient as(String email) {
        String token = webTestClient
                .post()
                .uri("/auth/dev-token")
                .bodyValue(Map.of("email", email))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TokenResponse.class)
                .returnResult()
                .getResponseBody()
                .accessToken();
        return webTestClient
                .mutate()
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }
}
