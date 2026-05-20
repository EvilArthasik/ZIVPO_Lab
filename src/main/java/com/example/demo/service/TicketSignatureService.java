package com.example.demo.service;

import com.example.demo.dto.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;

@Service
public class TicketSignatureService {
    private final String secret;
    private final String keyStorePath;
    private final String keyStorePassword;
    private final String keyStoreType;
    private final String keyAlias;

    public TicketSignatureService(
            @Value("${jwt.secret:change-this-secret-for-local-development}") String secret,
            @Value("${server.ssl.key-store:}") String keyStorePath,
            @Value("${server.ssl.key-store-password:}") String keyStorePassword,
            @Value("${server.ssl.key-store-type:PKCS12}") String keyStoreType,
            @Value("${server.ssl.key-alias:}") String keyAlias
    ) {
        this.secret = secret;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
        this.keyStoreType = keyStoreType;
        this.keyAlias = keyAlias;
    }

    public String sign(Ticket ticket) {
        if (hasConfiguredKeyStore()) {
            return signWithPrivateKey(ticket);
        }
        return signWithHmac(ticket);
    }

    private String signWithPrivateKey(Ticket ticket) {
        try {
            PrivateKey privateKey = loadPrivateKey();
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(canonical(ticket).getBytes(StandardCharsets.UTF_8));
            return encode(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign ticket with private key", exception);
        }
    }

    private String signWithHmac(Ticket ticket) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return encode(mac.doFinal(canonical(ticket).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign ticket", exception);
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        char[] password = keyStorePassword.toCharArray();
        try (FileInputStream inputStream = new FileInputStream(keyStorePath)) {
            keyStore.load(inputStream, password);
        }
        String alias = keyAlias == null || keyAlias.isBlank() ? keyStore.aliases().nextElement() : keyAlias;
        Key key = keyStore.getKey(alias, password);
        if (key instanceof PrivateKey privateKey) {
            return privateKey;
        }
        throw new IllegalStateException("Configured key store alias does not contain a private key");
    }

    private boolean hasConfiguredKeyStore() {
        return keyStorePath != null
                && !keyStorePath.isBlank()
                && keyStorePassword != null
                && !keyStorePassword.isBlank()
                && new File(keyStorePath).isFile();
    }

    private String encode(byte[] signature) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private String canonical(Ticket ticket) {
        return "serverDate=" + ticket.getServerDate()
                + "\nticketTtlSeconds=" + ticket.getTicketTtlSeconds()
                + "\nlicenseActivatedAt=" + ticket.getLicenseActivatedAt()
                + "\nlicenseExpiresAt=" + ticket.getLicenseExpiresAt()
                + "\nuserId=" + ticket.getUserId()
                + "\ndeviceId=" + ticket.getDeviceId()
                + "\nblocked=" + ticket.getBlocked();
    }
}
