package com.example.restcontrollerdemo.controller;

import com.example.restcontrollerdemo.dto.DevTokenRequest;
import com.example.restcontrollerdemo.dto.TokenResponse;
import com.example.restcontrollerdemo.security.DevTokenEnabled;
import com.example.restcontrollerdemo.service.DevTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@DevTokenEnabled
@RequestMapping("/auth")
@Tag(name = "Auth (dev)", description = "Passwordless tokens for seeded users — DEV_TOKEN_ENABLED only")
public class AuthController {

    private final DevTokenService devTokenService;

    public AuthController(DevTokenService devTokenService) {
        this.devTokenService = devTokenService;
    }

    @PostMapping("/dev-token")
    @Operation(
            operationId = "devToken",
            summary = "Get a token for a seeded user, without password",
            description = "Seeded users: alice@example.com, bob@example.com (enterprise 1), "
                    + "eve@example.com (enterprise 2).")
    public TokenResponse devToken(@Valid @RequestBody DevTokenRequest request) {
        return devTokenService.issue(request.email());
    }
}
