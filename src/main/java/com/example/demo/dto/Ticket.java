package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {
    private LocalDateTime serverDate;
    private Long ticketTtlSeconds;
    private LocalDateTime licenseActivatedAt;
    private LocalDateTime licenseExpiresAt;
    private Long userId;
    private Long deviceId;
    private Boolean blocked;
}
