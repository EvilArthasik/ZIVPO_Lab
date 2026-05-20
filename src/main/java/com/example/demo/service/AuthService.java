package com.example.demo.service;

import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.TokenPairResponse;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.UnauthorizedException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern SPECIAL_CHARACTER = Pattern.compile("[^A-Za-z0-9]");
    private static final Pattern DIGIT = Pattern.compile("\\d");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenPairService tokenPairService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenPairService tokenPairService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenPairService = tokenPairService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        validateRegistration(request);

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(userRepository.countByPasswordHashIsNotNull() == 0 ? User.Role.ADMIN : User.Role.USER);

        return userRepository.save(user);
    }

    @Transactional
    public TokenPairResponse login(LoginRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new UnauthorizedException("Username and password are required");
        }

        User user = userRepository.findByUsername(request.getUsername().trim())
                .or(() -> userRepository.findByEmail(request.getUsername().trim()))
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return tokenPairService.issueTokenPair(user);
    }

    @Transactional
    public TokenPairResponse refresh(String refreshToken) {
        if (isBlank(refreshToken)) {
            throw new UnauthorizedException("Refresh token is required");
        }
        return tokenPairService.refresh(refreshToken);
    }

    private void validateRegistration(RegisterRequest request) {
        if (request == null) {
            throw new BadRequestException("Registration data is required");
        }
        if (isBlank(request.getUsername())) {
            throw new BadRequestException("Username is required");
        }
        if (isBlank(request.getEmail())) {
            throw new BadRequestException("Email is required");
        }
        if (isBlank(request.getPassword())) {
            throw new BadRequestException("Password is required");
        }
        if (request.getPassword().length() < 8) {
            throw new BadRequestException("Password must contain at least 8 characters");
        }
        if (!SPECIAL_CHARACTER.matcher(request.getPassword()).find()) {
            throw new BadRequestException("Password must contain at least one special character");
        }
        if (!DIGIT.matcher(request.getPassword()).find()) {
            throw new BadRequestException("Password must contain at least one digit");
        }
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new BadRequestException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail().trim())) {
            throw new BadRequestException("Email is already taken");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
