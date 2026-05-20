package com.example.demo.service;

import com.example.demo.dto.CommentRequest;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Comment;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, TaskRepository taskRepository, UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    public List<Comment> getAllComments() {
        return commentRepository.findAll();
    }

    public Comment getComment(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + id));
    }

    @Transactional
    public Comment createComment(CommentRequest request) {
        Comment comment = new Comment();
        applyCommentRequest(comment, request);
        return commentRepository.save(comment);
    }

    @Transactional
    public Comment updateComment(Long id, CommentRequest request) {
        Comment comment = getComment(id);
        applyCommentRequest(comment, request);
        return commentRepository.save(comment);
    }

    @Transactional
    public void deleteComment(Long id) {
        if (!commentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Comment not found: " + id);
        }
        commentRepository.deleteById(id);
    }

    private void applyCommentRequest(Comment comment, CommentRequest request) {
        comment.setTask(taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + request.getTaskId())));
        comment.setAuthor(userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId())));
        comment.setContent(request.getContent());
    }
}
