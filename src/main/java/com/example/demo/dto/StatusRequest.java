package com.example.demo.dto;

import com.example.demo.model.Task;
import lombok.Data;

@Data
public class StatusRequest {
    private Task.TaskStatus status;
}
