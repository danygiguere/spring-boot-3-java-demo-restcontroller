package com.example.restcontrollerdemo.dto;

import com.example.restcontrollerdemo.domain.entity.Enterprise;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An enterprise")
public record EnterpriseResponse(Long id, String name) {

    public static EnterpriseResponse of(Enterprise enterprise) {
        return new EnterpriseResponse(enterprise.id(), enterprise.name());
    }
}
