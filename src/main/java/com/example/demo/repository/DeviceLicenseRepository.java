package com.example.demo.repository;

import com.example.demo.model.Device;
import com.example.demo.model.DeviceLicense;
import com.example.demo.model.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceLicenseRepository extends JpaRepository<DeviceLicense, Long> {
    boolean existsByLicenseAndDevice(License license, Device device);

    long countByLicense(License license);

    Optional<DeviceLicense> findFirstByLicenseOrderByActivatedAtAsc(License license);
}
