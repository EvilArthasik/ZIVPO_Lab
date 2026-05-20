package com.example.demo.controller;

import com.example.demo.dto.CommentRequest;
import com.example.demo.dto.StatusRequest;
import com.example.demo.dto.TaskSummaryResponse;
import com.example.demo.model.Comment;
import com.example.demo.model.Task;
import com.example.demo.service.TaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operations")
public class BusinessOperationController {
    private final TaskService taskService;

    public BusinessOperationController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PatchMapping("/tasks/{taskId}/assign/{userId}")
    public Task assignTask(@PathVariable Long taskId, @PathVariable Long userId) {
        return taskService.assignTask(taskId, userId);
    }

    @PostMapping("/tasks/{taskId}/tags/{tagId}")
    public Task addTagToTask(@PathVariable Long taskId, @PathVariable Long tagId) {
        return taskService.addTag(taskId, tagId);
    }

    @DeleteMapping("/tasks/{taskId}/tags/{tagId}")
    public Task removeTagFromTask(@PathVariable Long taskId, @PathVariable Long tagId) {
        return taskService.removeTag(taskId, tagId);
    }

    @PatchMapping("/tasks/{taskId}/status")
    public Task changeStatus(@PathVariable Long taskId, @RequestBody StatusRequest request) {
        return taskService.changeStatus(taskId, request.getStatus());
    }

    @PostMapping("/tasks/{taskId}/comments")
    public Comment addComment(@PathVariable Long taskId, @RequestBody CommentRequest request) {
        return taskService.addComment(taskId, request);
    }

    @GetMapping("/projects/{projectId}/summary")
    public TaskSummaryResponse getProjectSummary(@PathVariable Long projectId) {
        return taskService.getProjectSummary(projectId);
    }
}
