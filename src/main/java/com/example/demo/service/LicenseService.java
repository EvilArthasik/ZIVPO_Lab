package com.example.demo.service;

import com.example.demo.dto.ActivateLicenseRequest;
import com.example.demo.dto.CheckLicenseRequest;
import com.example.demo.dto.CreateLicenseRequest;
import com.example.demo.dto.RenewLicenseRequest;
import com.example.demo.dto.Ticket;
import com.example.demo.dto.TicketResponse;
import com.example.demo.exception.BadRequestException;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.model.Device;
import com.example.demo.model.DeviceLicense;
import com.example.demo.model.License;
import com.example.demo.model.LicenseHistory;
import com.example.demo.model.LicenseHistoryStatus;
import com.example.demo.model.User;
import com.example.demo.repository.DeviceLicenseRepository;
import com.example.demo.repository.DeviceRepository;
import com.example.demo.repository.LicenseHistoryRepository;
import com.example.demo.repository.LicenseRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class LicenseService {
    private final LicenseRepository licenseRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceLicenseRepository deviceLicenseRepository;
    private final LicenseHistoryRepository licenseHistoryRepository;
    private final TicketSignatureService ticketSignatureService;
    private final long ticketTtlSeconds;

    public LicenseService(
            LicenseRepository licenseRepository,
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            DeviceLicenseRepository deviceLicenseRepository,
            LicenseHistoryRepository licenseHistoryRepository,
            TicketSignatureService ticketSignatureService,
            @Value("${license.ticket-ttl-seconds:300}") long ticketTtlSeconds
    ) {
        this.licenseRepository = licenseRepository;
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.deviceLicenseRepository = deviceLicenseRepository;
        this.licenseHistoryRepository = licenseHistoryRepository;
        this.ticketSignatureService = ticketSignatureService;
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    @Transactional
    public License createLicense(CreateLicenseRequest request) {
        if (request.getDurationDays() == null || request.getDurationDays() <= 0) {
            throw new BadRequestException("License duration must be positive");
        }

        License license = new License();
        license.setLicenseKey(generateLicenseKey());
        license.setUser(getUser(request.getUserId()));
        license.setDurationDays(request.getDurationDays());
        license.setDeviceLimit(request.getDeviceLimit() == null ? 1 : request.getDeviceLimit());
        if (license.getDeviceLimit() <= 0) {
            throw new BadRequestException("Device limit must be positive");
        }

        License savedLicense = licenseRepository.save(license);
        saveHistory(savedLicense, LicenseHistoryStatus.CREATED, "License created");
        return savedLicense;
    }

    @Transactional
    public TicketResponse activateLicense(ActivateLicenseRequest request) {
        License license = getLicense(request.getLicenseKey());
        Device device = getOrCreateDevice(request.getDeviceFingerprint(), request.getDeviceName());

        if (Boolean.TRUE.equals(license.getBlocked())) {
            throw new BadRequestException("License is blocked");
        }

        LocalDateTime now = LocalDateTime.now();
        license.setStatus(resolveStatus(license, now));
        if (license.getStatus() == License.LicenseStatus.EXPIRED) {
            throw new BadRequestException("License is expired");
        }
        if (license.getActivatedAt() == null) {
            license.setActivatedAt(now);
            license.setExpiresAt(now.plusDays(license.getDurationDays()));
        }
        if (!deviceLicenseRepository.existsByLicenseAndDevice(license, device)
                && deviceLicenseRepository.countByLicense(license) >= license.getDeviceLimit()) {
            throw new BadRequestException("Device limit reached");
        }

        createDeviceLicenseIfMissing(license, device, now);
        if (license.getDevice() == null) {
            license.setDevice(device);
        }
        license.setStatus(resolveStatus(license, now));

        License savedLicense = licenseRepository.save(license);
        saveHistory(savedLicense, LicenseHistoryStatus.ACTIVATED, "License activated");
        return buildTicketResponse(savedLicense, device, now);
    }

    @Transactional
    public TicketResponse checkLicense(CheckLicenseRequest request) {
        License license = getLicense(request.getLicenseKey());
        Device device = validateDevice(license, request.getDeviceFingerprint());
        LocalDateTime now = LocalDateTime.now();
        license.setStatus(resolveStatus(license, now));
        if (license.getStatus() == License.LicenseStatus.EXPIRED) {
            throw new BadRequestException("License is expired");
        }
        saveHistory(license, LicenseHistoryStatus.CHECKED, "License checked");
        return buildTicketResponse(licenseRepository.save(license), device, now);
    }

    @Transactional
    public TicketResponse renewLicense(String licenseKey, RenewLicenseRequest request) {
        if (request.getAdditionalDays() == null || request.getAdditionalDays() <= 0) {
            throw new BadRequestException("Additional duration must be positive");
        }

        LocalDateTime now = LocalDateTime.now();
        License license = getLicense(licenseKey);
        license.setDurationDays(license.getDurationDays() + request.getAdditionalDays());
        if (license.getActivatedAt() != null) {
            LocalDateTime baseDate = license.getExpiresAt() != null && license.getExpiresAt().isAfter(now)
                    ? license.getExpiresAt()
                    : now;
            license.setExpiresAt(baseDate.plusDays(request.getAdditionalDays()));
        }
        license.setStatus(resolveStatus(license, now));
        License savedLicense = licenseRepository.save(license);
        saveHistory(savedLicense, LicenseHistoryStatus.RENEWED, "License renewed");
        Device ticketDevice = savedLicense.getDevice();
        if (ticketDevice == null) {
            ticketDevice = deviceLicenseRepository.findFirstByLicenseOrderByActivatedAtAsc(savedLicense)
                    .map(DeviceLicense::getDevice)
                    .orElse(null);
        }
        return buildTicketResponse(savedLicense, ticketDevice, now);
    }

    private TicketResponse buildTicketResponse(License license, LocalDateTime now) {
        return buildTicketResponse(license, license.getDevice(), now);
    }

    private TicketResponse buildTicketResponse(License license, Device device, LocalDateTime now) {
        Ticket ticket = new Ticket(
                now,
                ticketTtlSeconds,
                license.getActivatedAt(),
                license.getExpiresAt(),
                license.getUser().getId(),
                device == null ? null : device.getId(),
                license.getBlocked()
        );
        return new TicketResponse(ticket, ticketSignatureService.sign(ticket));
    }

    private User getUser(Long id) {
        if (id == null) {
            throw new BadRequestException("User id is required");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private License getLicense(String licenseKey) {
        if (licenseKey == null || licenseKey.isBlank()) {
            throw new BadRequestException("License key is required");
        }
        return licenseRepository.findByLicenseKey(licenseKey)
                .orElseThrow(() -> new ResourceNotFoundException("License not found: " + licenseKey));
    }

    private Device getOrCreateDevice(String fingerprint, String name) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new BadRequestException("Device fingerprint is required");
        }
        String normalizedFingerprint = normalizeFingerprint(fingerprint);
        return deviceRepository.findByFingerprint(normalizedFingerprint)
                .orElseGet(() -> {
                    Device device = new Device();
                    device.setFingerprint(normalizedFingerprint);
                    device.setName(name);
                    return deviceRepository.save(device);
                });
    }

    private Device validateDevice(License license, String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new BadRequestException("Device fingerprint is required");
        }
        Device device = deviceRepository.findByFingerprint(normalizeFingerprint(fingerprint))
                .orElseThrow(() -> new BadRequestException("License belongs to another device"));
        if (!deviceLicenseRepository.existsByLicenseAndDevice(license, device)) {
            throw new BadRequestException("License is not activated");
        }
        return device;
    }

    private License.LicenseStatus resolveStatus(License license, LocalDateTime now) {
        if (license.getExpiresAt() != null && license.getExpiresAt().isBefore(now)) {
            return License.LicenseStatus.EXPIRED;
        }
        return license.getActivatedAt() == null ? License.LicenseStatus.CREATED : License.LicenseStatus.ACTIVE;
    }

    private String generateLicenseKey() {
        String key;
        do {
            key = "LIC-" + UUID.randomUUID().toString().toUpperCase();
        } while (licenseRepository.existsByLicenseKey(key));
        return key;
    }

    private void createDeviceLicenseIfMissing(License license, Device device, LocalDateTime activatedAt) {
        if (deviceLicenseRepository.existsByLicenseAndDevice(license, device)) {
            return;
        }
        DeviceLicense deviceLicense = new DeviceLicense();
        deviceLicense.setLicense(license);
        deviceLicense.setDevice(device);
        deviceLicense.setActivatedAt(activatedAt);
        deviceLicenseRepository.save(deviceLicense);
    }

    private void saveHistory(License license, LicenseHistoryStatus status, String description) {
        LicenseHistory history = new LicenseHistory();
        history.setLicense(license);
        history.setStatus(status);
        history.setDescription(description);
        licenseHistoryRepository.save(history);
    }

    private String normalizeFingerprint(String fingerprint) {
        return fingerprint.trim().replace('-', ':').toUpperCase();
    }
}
