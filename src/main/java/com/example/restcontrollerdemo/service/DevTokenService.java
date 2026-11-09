package com.example.restcontrollerdemo.service;

import com.example.restcontrollerdemo.dto.TokenResponse;
import com.example.restcontrollerdemo.exception.AppException;
import com.example.restcontrollerdemo.repository.UserRepository;
import com.example.restcontrollerdemo.security.DevTokenEnabled;
import com.example.restcontrollerdemo.security.DevTokenIssuer;
import org.springframework.stereotype.Service;

/** Passwordless token for local development. Exists only with DEV_TOKEN_ENABLED, which requires a loopback bind. */
@Service
@DevTokenEnabled
public class DevTokenService {

    private final UserRepository userRepository;
    private final DevTokenIssuer issuer;

    public DevTokenService(UserRepository userRepository, DevTokenIssuer issuer) {
        this.userRepository = userRepository;
        this.issuer = issuer;
    }

    public TokenResponse issue(String email) {
        return userRepository
                .findByEmail(email)
                .map(issuer::issue)
                .orElseThrow(() -> new AppException.Unauthorized("error.auth.invalid"));
    }
}
