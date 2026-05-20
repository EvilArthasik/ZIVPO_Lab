package com.example.demo.service;

import com.example.demo.dto.ActivateLicenseRequest;
import com.example.demo.dto.CheckLicenseRequest;
import com.example.demo.dto.CreateLicenseRequest;
import com.example.demo.dto.RenewLicenseRequest;
import com.example.demo.dto.TicketResponse;
import com.example.demo.exception.BadRequestException;
import com.example.demo.model.License;
import com.example.demo.model.User;
import com.example.demo.repository.DeviceLicenseRepository;
import com.example.demo.repository.LicenseHistoryRepository;
import com.example.demo.repository.LicenseRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class LicenseServiceTests {
    @Autowired
    private LicenseService licenseService;

    @Autowired
    private LicenseRepository licenseRepository;

    @Autowired
    private DeviceLicenseRepository deviceLicenseRepository;

    @Autowired
    private LicenseHistoryRepository licenseHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createActivateCheckAndRenewLicense() {
        User user = userRepository.save(user("client", "client@example.com"));
        License license = licenseService.createLicense(createRequest(user.getId(), 30));

        assertThat(license.getLicenseKey()).startsWith("LIC-");
        assertThat(license.getStatus()).isEqualTo(License.LicenseStatus.CREATED);
        assertThat(license.getDeviceLimit()).isEqualTo(1);

        TicketResponse activated = licenseService.activateLicense(activateRequest(license.getLicenseKey(), "device-1"));

        assertThat(activated.getTicket().getUserId()).isEqualTo(user.getId());
        assertThat(activated.getTicket().getDeviceId()).isNotNull();
        assertThat(activated.getTicket().getTicketTtlSeconds()).isEqualTo(300);
        assertThat(activated.getSignature()).isNotBlank();

        TicketResponse checked = licenseService.checkLicense(checkRequest(license.getLicenseKey(), "device-1"));

        assertThat(checked.getTicket().getLicenseActivatedAt()).isNotNull();
        assertThat(checked.getTicket().getLicenseExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));

        LocalDateTime expiresBeforeRenewal = checked.getTicket().getLicenseExpiresAt();
        TicketResponse renewed = licenseService.renewLicense(license.getLicenseKey(), renewRequest(15));

        assertThat(renewed.getTicket().getLicenseExpiresAt()).isAfter(expiresBeforeRenewal);
        assertThat(licenseRepository.findByLicenseKey(license.getLicenseKey()).orElseThrow().getDurationDays())
                .isEqualTo(45);
        assertThat(deviceLicenseRepository.countByLicense(license)).isEqualTo(1);
        assertThat(licenseHistoryRepository.count()).isEqualTo(4);
    }

    @Test
    void checkRejectsAnotherDevice() {
        User user = userRepository.save(user("client", "client@example.com"));
        License license = licenseService.createLicense(createRequest(user.getId(), 30));
        licenseService.activateLicense(activateRequest(license.getLicenseKey(), "device-1"));

        assertThatThrownBy(() -> licenseService.checkLicense(checkRequest(license.getLicenseKey(), "device-2")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("another device");
    }

    @Test
    void activationRejectsSecondDeviceWhenLimitIsReached() {
        User user = userRepository.save(user("client", "client@example.com"));
        License license = licenseService.createLicense(createRequest(user.getId(), 30));
        licenseService.activateLicense(activateRequest(license.getLicenseKey(), "AA-BB-CC"));

        assertThatThrownBy(() -> licenseService.activateLicense(activateRequest(license.getLicenseKey(), "DD:EE:FF")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Device limit reached");
    }

    private User user(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setRole(User.Role.USER);
        return user;
    }

    private CreateLicenseRequest createRequest(Long userId, int durationDays) {
        CreateLicenseRequest request = new CreateLicenseRequest();
        request.setUserId(userId);
        request.setDurationDays(durationDays);
        request.setDeviceLimit(1);
        return request;
    }

    private ActivateLicenseRequest activateRequest(String licenseKey, String deviceFingerprint) {
        ActivateLicenseRequest request = new ActivateLicenseRequest();
        request.setLicenseKey(licenseKey);
        request.setDeviceFingerprint(deviceFingerprint);
        request.setDeviceName("Workstation");
        return request;
    }

    private CheckLicenseRequest checkRequest(String licenseKey, String deviceFingerprint) {
        CheckLicenseRequest request = new CheckLicenseRequest();
        request.setLicenseKey(licenseKey);
        request.setDeviceFingerprint(deviceFingerprint);
        return request;
    }

    private RenewLicenseRequest renewRequest(int additionalDays) {
        RenewLicenseRequest request = new RenewLicenseRequest();
        request.setAdditionalDays(additionalDays);
        return request;
    }
}
