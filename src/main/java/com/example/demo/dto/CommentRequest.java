package com.example.demo.dto;

import lombok.Data;

@Data
public class CommentRequest {
    private Long taskId;
    private Long userId;
    private String content;
}
