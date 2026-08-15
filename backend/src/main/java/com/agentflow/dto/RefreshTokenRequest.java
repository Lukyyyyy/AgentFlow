package com.agentflow.dto;

import lombok.Data;

/**
 * Refresh Token 请求 DTO
 */
@Data
public class RefreshTokenRequest {

    private String refreshToken;
}
