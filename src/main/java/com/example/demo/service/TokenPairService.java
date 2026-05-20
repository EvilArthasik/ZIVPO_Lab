package com.example.demo.service;

import com.example.demo.dto.TokenPairResponse;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.model.SessionStatus;
import com.example.demo.model.User;
import com.example.demo.model.UserSession;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSessionRepository;
import com.example.demo.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TokenPairService {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    public TokenPairService(
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            UserSessionRepository userSessionRepository
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Transactional
    public TokenPairResponse issueTokenPair(User user) {
        UserSession session = new UserSession();
        session.setUser(user);
        session.setStatus(SessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().plusSeconds(jwtTokenProvider.getRefreshTtlSeconds()));
        session = userSessionRepository.saveAndFlush(session);

        JwtTokenProvider.RefreshTokenData refreshToken = jwtTokenProvider.createRefreshToken(user, session.getId());
        session.setRefreshTokenId(refreshToken.tokenId());
        session.setExpiresAt(refreshToken.expiresAt());
        session.setUpdatedAt(Instant.now());
        userSessionRepository.save(session);

        String accessToken = jwtTokenProvider.createAccessToken(user, session.getId());
        return response(accessToken, refreshToken.token());
    }

    @Transactional
    public TokenPairResponse refresh(String refreshTokenValue) {
        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.validateRefreshToken(refreshTokenValue);
        UserSession oldSession = userSessionRepository.findByRefreshTokenId(claims.tokenId())
                .orElseThrow(() -> new UnauthorizedException("Refresh session was not found"));

        if (!oldSession.getId().equals(claims.sessionId())
                || !oldSession.getUser().getId().equals(claims.userId())
                || oldSession.getStatus() != SessionStatus.ACTIVE
                || !oldSession.getExpiresAt().isAfter(Instant.now())) {
            markExpiredIfNeeded(oldSession);
            throw new UnauthorizedException("Refresh session is not active");
        }

        oldSession.setStatus(SessionStatus.REFRESHED);
        oldSession.setUpdatedAt(Instant.now());
        userSessionRepository.save(oldSession);

        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new UnauthorizedException("Token user was not found"));
        return issueTokenPair(user);
    }

    private TokenPairResponse response(String accessToken, String refreshToken) {
        return new TokenPairResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessTtlSeconds(),
                jwtTokenProvider.getRefreshTtlSeconds()
        );
    }

    private void markExpiredIfNeeded(UserSession session) {
        if (session.getStatus() == SessionStatus.ACTIVE && !session.getExpiresAt().isAfter(Instant.now())) {
            session.setStatus(SessionStatus.EXPIRED);
            session.setUpdatedAt(Instant.now());
            userSessionRepository.save(session);
        }
    }
}
