package com.example.demo.dto;

import lombok.Data;

@Data
public class CheckLicenseRequest {
    private String licenseKey;
    private String deviceFingerprint;
}
