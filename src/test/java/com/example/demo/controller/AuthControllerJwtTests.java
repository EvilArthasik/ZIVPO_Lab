package com.example.demo.controller;

import com.example.demo.model.SessionStatus;
import com.example.demo.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerJwtTests {
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REFRESH_TOKEN = Pattern.compile("\"refreshToken\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Test
    void refreshRotatesTokenPairAndRejectsReusedRefreshToken() throws Exception {
        register("jwt-admin", "jwt-admin@example.com", "Strong#123");

        String firstLogin = login("jwt-admin", "Strong#123");
        String accessToken = extract(ACCESS_TOKEN, firstLogin);
        String oldRefreshToken = extract(REFRESH_TOKEN, firstLogin);

        mockMvc.perform(get("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        String refreshed = refresh(oldRefreshToken);
        String newRefreshToken = extract(REFRESH_TOKEN, refreshed);

        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(userSessionRepository.findAll())
                .extracting(session -> session.getStatus())
                .containsExactlyInAnyOrder(SessionStatus.REFRESHED, SessionStatus.ACTIVE);
    }

    private void register(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, email, password)))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String extract(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
