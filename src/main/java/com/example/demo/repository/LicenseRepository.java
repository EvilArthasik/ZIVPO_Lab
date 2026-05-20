package com.example.demo.repository;

import com.example.demo.model.License;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LicenseRepository extends JpaRepository<License, Long> {
    Optional<License> findByLicenseKey(String licenseKey);

    boolean existsByLicenseKey(String licenseKey);
}
