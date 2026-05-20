package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskSummaryResponse {
    private long total;
    private long open;
    private long inProgress;
    private long done;
}
