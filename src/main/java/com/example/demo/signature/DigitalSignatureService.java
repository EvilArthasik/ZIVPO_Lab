package com.example.demo.signature;

import org.springframework.stereotype.Service;

import java.security.Signature;
import java.util.Base64;

@Service
public class DigitalSignatureService {
    private final JsonCanonicalizer canonicalizer;
    private final SignatureKeyProvider keyProvider;
    private final SignatureProperties properties;

    public DigitalSignatureService(
            JsonCanonicalizer canonicalizer,
            SignatureKeyProvider keyProvider,
            SignatureProperties properties
    ) {
        this.canonicalizer = canonicalizer;
        this.keyProvider = keyProvider;
        this.properties = properties;
    }

    public String sign(Object payload) {
        try {
            Signature signature = Signature.getInstance(properties.getAlgorithm());
            signature.initSign(keyProvider.getPrivateKey());
            signature.update(canonicalizer.canonicalBytes(payload));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (SignatureException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SignatureException("Unable to create digital signature", exception);
        }
    }

    public boolean verify(Object payload, String signatureValue) {
        try {
            Signature signature = Signature.getInstance(properties.getAlgorithm());
            signature.initVerify(keyProvider.getPublicKey());
            signature.update(canonicalizer.canonicalBytes(payload));
            return signature.verify(Base64.getDecoder().decode(signatureValue));
        } catch (Exception exception) {
            throw new SignatureException("Unable to verify digital signature", exception);
        }
    }
}
