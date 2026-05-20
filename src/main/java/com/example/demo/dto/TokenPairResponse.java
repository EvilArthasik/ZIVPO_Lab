package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenPairResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long accessExpiresInSeconds;
    private long refreshExpiresInSeconds;
}
