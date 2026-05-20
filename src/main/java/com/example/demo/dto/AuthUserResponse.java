package com.example.demo.dto;

import com.example.demo.model.User;

public record AuthUserResponse(
        Long id,
        String username,
        String email,
        User.Role role
) {
    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
