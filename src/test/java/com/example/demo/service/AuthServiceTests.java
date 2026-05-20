package com.example.demo.service;

import com.example.demo.dto.RegisterRequest;
import com.example.demo.exception.BadRequestException;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AuthServiceTests {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void firstRegisteredUserBecomesAdminAndPasswordIsEncoded() {
        RegisterRequest request = registerRequest("admin", "admin@example.com", "Strong#123");

        User user = authService.register(request);

        assertThat(user.getRole()).isEqualTo(User.Role.ADMIN);
        assertThat(user.getPasswordHash()).isNotEqualTo("Strong#123");
        assertThat(passwordEncoder.matches("Strong#123", user.getPasswordHash())).isTrue();
    }

    @Test
    void laterRegisteredUsersBecomeRegularUsers() {
        authService.register(registerRequest("admin", "admin@example.com", "Strong#123"));

        User user = authService.register(registerRequest("user", "user@example.com", "Strong#456"));

        assertThat(user.getRole()).isEqualTo(User.Role.USER);
    }

    @Test
    void weakPasswordIsRejected() {
        RegisterRequest request = registerRequest("user", "user@example.com", "password");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("special character");
        assertThat(userRepository.count()).isZero();
    }

    private RegisterRequest registerRequest(String username, String email, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }
}
