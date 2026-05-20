package com.example.demo.dto;

import lombok.Data;

@Data
public class CreateLicenseRequest {
    private Long userId;
    private Integer durationDays;
    private Integer deviceLimit;
}
