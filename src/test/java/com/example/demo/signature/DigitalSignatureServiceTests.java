package com.example.demo.signature;

import com.example.demo.dto.Ticket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DigitalSignatureServiceTests {
    @Autowired
    private JsonCanonicalizer canonicalizer;

    @Autowired
    private DigitalSignatureService digitalSignatureService;

    @Autowired
    private SignatureKeyProvider signatureKeyProvider;

    @Test
    void canonicalizationSortsObjectFieldsAndUsesUtf8Json() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("z", 1);
        payload.put("a", "line\nbreak");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("b", true);
        nested.put("a", null);
        payload.put("nested", nested);

        assertThat(canonicalizer.canonicalize(payload))
                .isEqualTo("{\"a\":\"line\\nbreak\",\"nested\":{\"a\":null,\"b\":true},\"z\":1}");
    }

    @Test
    void signsTicketWithConfiguredRsaKeyAndVerifiesWithCertificatePublicKey() {
        Ticket ticket = new Ticket(
                LocalDateTime.of(2026, 5, 21, 12, 0),
                300L,
                LocalDateTime.of(2026, 5, 21, 12, 0),
                LocalDateTime.of(2026, 6, 20, 12, 0),
                1L,
                2L,
                false
        );

        String signature = digitalSignatureService.sign(ticket);

        assertThat(Base64.getDecoder().decode(signature)).hasSize(256);
        assertThat(digitalSignatureService.verify(ticket, signature)).isTrue();
    }

    @Test
    void signsManifestBytesWithoutJsonCanonicalization() throws Exception {
        byte[] manifest = new byte[] {'Z', 'S', 'G', 'M', 0, 0, 0, 1};

        String signature = digitalSignatureService.signManifest(manifest);
        byte[] signatureBytes = Base64.getDecoder().decode(signature);

        java.security.Signature verifier = java.security.Signature.getInstance(digitalSignatureService.algorithm());
        verifier.initVerify(signatureKeyProvider.getPublicKey());
        verifier.update(manifest);
        assertThat(signatureBytes).hasSize(256);
        assertThat(verifier.verify(signatureBytes)).isTrue();
    }
}
