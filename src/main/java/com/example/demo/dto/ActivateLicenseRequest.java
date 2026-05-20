package com.example.demo.dto;

import lombok.Data;

@Data
public class ActivateLicenseRequest {
    private String licenseKey;
    private String deviceFingerprint;
    private String deviceName;
}
