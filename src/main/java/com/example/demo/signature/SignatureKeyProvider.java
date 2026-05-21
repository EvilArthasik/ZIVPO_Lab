package com.example.demo.signature;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.util.Enumeration;

@Component
public class SignatureKeyProvider {
    private final SignatureProperties properties;
    private final ResourceLoader resourceLoader;
    private volatile KeyMaterial cachedKeyMaterial;

    public SignatureKeyProvider(SignatureProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    public PrivateKey getPrivateKey() {
        return getKeyMaterial().privateKey();
    }

    public PublicKey getPublicKey() {
        return getKeyMaterial().certificate().getPublicKey();
    }

    public Certificate getCertificate() {
        return getKeyMaterial().certificate();
    }

    private KeyMaterial getKeyMaterial() {
        KeyMaterial result = cachedKeyMaterial;
        if (result == null) {
            synchronized (this) {
                result = cachedKeyMaterial;
                if (result == null) {
                    result = loadKeyMaterial();
                    cachedKeyMaterial = result;
                }
            }
        }
        return result;
    }

    private KeyMaterial loadKeyMaterial() {
        validateConfiguration();
        char[] storePassword = properties.getKeyStorePassword().toCharArray();
        char[] keyPassword = keyPassword();

        try {
            KeyStore keyStore = KeyStore.getInstance(properties.getKeyStoreType());
            Resource resource = resourceLoader.getResource(properties.getKeyStorePath());
            if (!resource.exists()) {
                resource = resourceLoader.getResource("file:" + properties.getKeyStorePath());
            }
            try (InputStream inputStream = resource.getInputStream()) {
                keyStore.load(inputStream, storePassword);
            }

            String alias = resolveAlias(keyStore);
            Key key = keyStore.getKey(alias, keyPassword);
            if (!(key instanceof PrivateKey privateKey)) {
                throw new SignatureException("Signature keystore alias does not contain a private key: " + alias);
            }
            Certificate certificate = keyStore.getCertificate(alias);
            if (certificate == null) {
                throw new SignatureException("Signature keystore alias does not contain a certificate: " + alias);
            }
            return new KeyMaterial(privateKey, certificate);
        } catch (SignatureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SignatureException("Unable to load signature key material", exception);
        }
    }

    private void validateConfiguration() {
        if (isBlank(properties.getKeyStorePath())) {
            throw new SignatureException("signature.key-store-path is required");
        }
        if (isBlank(properties.getKeyStorePassword())) {
            throw new SignatureException("signature.key-store-password is required");
        }
        if (isBlank(properties.getKeyStoreType())) {
            throw new SignatureException("signature.key-store-type is required");
        }
    }

    private String resolveAlias(KeyStore keyStore) throws Exception {
        if (!isBlank(properties.getKeyAlias())) {
            if (!keyStore.containsAlias(properties.getKeyAlias())) {
                throw new SignatureException("Signature keystore alias not found: " + properties.getKeyAlias());
            }
            return properties.getKeyAlias();
        }
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) {
                return alias;
            }
        }
        throw new SignatureException("Signature keystore does not contain private key entries");
    }

    private char[] keyPassword() {
        String keyPassword = isBlank(properties.getKeyPassword())
                ? properties.getKeyStorePassword()
                : properties.getKeyPassword();
        return keyPassword.toCharArray();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record KeyMaterial(PrivateKey privateKey, Certificate certificate) {
    }
}
