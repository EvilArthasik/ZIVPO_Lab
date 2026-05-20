package com.example.demo.controller;

import com.example.demo.dto.ActivateLicenseRequest;
import com.example.demo.dto.CheckLicenseRequest;
import com.example.demo.dto.CreateLicenseRequest;
import com.example.demo.dto.RenewLicenseRequest;
import com.example.demo.dto.TicketResponse;
import com.example.demo.model.License;
import com.example.demo.service.LicenseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/licenses")
public class LicenseController {
    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping
    public ResponseEntity<License> createLicense(@RequestBody CreateLicenseRequest request) {
        return new ResponseEntity<>(licenseService.createLicense(request), HttpStatus.CREATED);
    }

    @PostMapping("/activate")
    public TicketResponse activateLicense(@RequestBody ActivateLicenseRequest request) {
        return licenseService.activateLicense(request);
    }

    @PostMapping("/check")
    public TicketResponse checkLicense(@RequestBody CheckLicenseRequest request) {
        return licenseService.checkLicense(request);
    }

    @PostMapping("/{licenseKey}/renew")
    public TicketResponse renewLicense(
            @PathVariable String licenseKey,
            @RequestBody RenewLicenseRequest request
    ) {
        return licenseService.renewLicense(licenseKey, request);
    }
}
