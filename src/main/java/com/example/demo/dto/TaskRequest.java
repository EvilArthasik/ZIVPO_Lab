package com.example.demo.dto;

import com.example.demo.model.Task;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class TaskRequest {
    private String title;
    private String description;
    private Task.TaskStatus status;
    private Long projectId;
    private Long userId;
    private Set<Long> tagIds = new LinkedHashSet<>();
}
