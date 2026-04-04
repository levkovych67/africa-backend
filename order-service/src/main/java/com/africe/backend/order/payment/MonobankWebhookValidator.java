package com.africe.backend.order.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Component
public class MonobankWebhookValidator {

    private final MonobankClient monobankClient;
    private volatile PublicKey cachedPublicKey;

    public MonobankWebhookValidator(MonobankClient monobankClient) {
        this.monobankClient = monobankClient;
    }

    public boolean validateSignature(String body, String xSignHeader) {
        try {
            PublicKey publicKey = getPublicKey();
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(body.getBytes());
            byte[] signatureBytes = Base64.getDecoder().decode(xSignHeader);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to validate Monobank webhook signature: {}", e.getMessage());
            return false;
        }
    }

    private PublicKey getPublicKey() throws Exception {
        if (cachedPublicKey == null) {
            synchronized (this) {
                if (cachedPublicKey == null) {
                    String keyBase64 = monobankClient.getPublicKey();
                    byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
                    X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                    KeyFactory factory = KeyFactory.getInstance("EC");
                    cachedPublicKey = factory.generatePublic(spec);
                }
            }
        }
        return cachedPublicKey;
    }
}
