package com.example.demo.service;

import com.example.demo.dto.CommentRequest;
import com.example.demo.dto.TaskRequest;
import com.example.demo.dto.TaskSummaryResponse;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Comment;
import com.example.demo.model.Project;
import com.example.demo.model.Tag;
import com.example.demo.model.Task;
import com.example.demo.model.User;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TagRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final CommentRepository commentRepository;

    public TaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            TagRepository tagRepository,
            CommentRepository commentRepository
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.commentRepository = commentRepository;
    }

    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    public Task getTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
    }

    @Transactional
    public Task createTask(TaskRequest request) {
        Task task = new Task();
        applyTaskRequest(task, request);
        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTask(Long id, TaskRequest request) {
        Task task = getTask(id);
        applyTaskRequest(task, request);
        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new ResourceNotFoundException("Task not found: " + id);
        }
        taskRepository.deleteById(id);
    }

    @Transactional
    public Task assignTask(Long taskId, Long userId) {
        Task task = getTask(taskId);
        task.setAssignee(getUser(userId));
        return taskRepository.save(task);
    }

    @Transactional
    public Task addTag(Long taskId, Long tagId) {
        Task task = getTask(taskId);
        task.getTags().add(getTag(tagId));
        return taskRepository.save(task);
    }

    @Transactional
    public Task removeTag(Long taskId, Long tagId) {
        Task task = getTask(taskId);
        Tag tag = getTag(tagId);
        task.getTags().removeIf(existingTag -> existingTag.getId().equals(tag.getId()));
        return taskRepository.save(task);
    }

    @Transactional
    public Task changeStatus(Long taskId, Task.TaskStatus newStatus) {
        if (newStatus == null) {
            throw new BadRequestException("Status is required");
        }
        Task task = getTask(taskId);
        if (!canMoveStatus(task.getStatus(), newStatus)) {
            throw new BadRequestException("Illegal status transition: " + task.getStatus() + " -> " + newStatus);
        }
        task.setStatus(newStatus);
        return taskRepository.save(task);
    }

    @Transactional
    public Comment addComment(Long taskId, CommentRequest request) {
        Comment comment = new Comment();
        comment.setTask(getTask(taskId));
        comment.setAuthor(getUser(request.getUserId()));
        comment.setContent(request.getContent());
        return commentRepository.save(comment);
    }

    public TaskSummaryResponse getProjectSummary(Long projectId) {
        getProject(projectId);
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        long open = tasks.stream().filter(task -> task.getStatus() == Task.TaskStatus.OPEN).count();
        long inProgress = tasks.stream().filter(task -> task.getStatus() == Task.TaskStatus.IN_PROGRESS).count();
        long done = tasks.stream().filter(task -> task.getStatus() == Task.TaskStatus.DONE).count();
        return new TaskSummaryResponse(tasks.size(), open, inProgress, done);
    }

    private void applyTaskRequest(Task task, TaskRequest request) {
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setStatus(request.getStatus() == null ? Task.TaskStatus.OPEN : request.getStatus());
        task.setProject(getProject(request.getProjectId()));
        task.setAssignee(request.getUserId() == null ? null : getUser(request.getUserId()));
        task.setTags(getTags(request.getTagIds()));
    }

    private Project getProject(Long id) {
        if (id == null) {
            throw new BadRequestException("Project id is required");
        }
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private User getUser(Long id) {
        if (id == null) {
            throw new BadRequestException("User id is required");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private Tag getTag(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + id));
    }

    private Set<Tag> getTags(Set<Long> tagIds) {
        Set<Tag> tags = new LinkedHashSet<>();
        if (tagIds == null) {
            return tags;
        }
        tagIds.forEach(tagId -> tags.add(getTag(tagId)));
        return tags;
    }

    private boolean canMoveStatus(Task.TaskStatus currentStatus, Task.TaskStatus newStatus) {
        return currentStatus == newStatus
                || currentStatus == Task.TaskStatus.OPEN && newStatus == Task.TaskStatus.IN_PROGRESS
                || currentStatus == Task.TaskStatus.IN_PROGRESS && newStatus == Task.TaskStatus.DONE;
    }
}
