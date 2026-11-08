package com.example.restcontrollerdemo.service;

import com.example.restcontrollerdemo.dto.EnterpriseResponse;
import com.example.restcontrollerdemo.exception.AppException;
import com.example.restcontrollerdemo.repository.EnterpriseRepository;
import com.example.restcontrollerdemo.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

@Service
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;

    public EnterpriseService(EnterpriseRepository enterpriseRepository) {
        this.enterpriseRepository = enterpriseRepository;
    }

    /** The caller's enterprise: the only one they can see. */
    public EnterpriseResponse current(AuthenticatedUser me) {
        return enterpriseRepository
                .findById(me.enterpriseId())
                .map(EnterpriseResponse::of)
                .orElseThrow(() -> new AppException.NotFound("error.enterprise.not.found"));
    }
}
