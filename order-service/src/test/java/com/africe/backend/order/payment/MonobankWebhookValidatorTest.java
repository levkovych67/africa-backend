package com.africe.backend.order.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.*;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonobankWebhookValidatorTest {

    @Mock MonobankClient monobankClient;
    @InjectMocks MonobankWebhookValidator validator;

    @Test
    void validateSignature_validSignature_returnsTrue() throws Exception {
        // Generate a test EC key pair
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();

        // Sign a test body
        String body = "{\"status\":\"success\",\"reference\":\"order123\"}";
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(body.getBytes());
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        // Return public key in base64
        String publicKeyBase64 = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded());
        when(monobankClient.getPublicKey()).thenReturn(publicKeyBase64);

        boolean result = validator.validateSignature(body, signature);

        assertThat(result).isTrue();
    }

    @Test
    void validateSignature_invalidSignature_returnsFalse() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();

        String publicKeyBase64 = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded());
        when(monobankClient.getPublicKey()).thenReturn(publicKeyBase64);

        boolean result = validator.validateSignature(
                "{\"body\":\"test\"}", "aW52YWxpZHNpZ25hdHVyZQ==");

        assertThat(result).isFalse();
    }

    @Test
    void validateSignature_malformedBase64_returnsFalse() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();

        String publicKeyBase64 = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded());
        when(monobankClient.getPublicKey()).thenReturn(publicKeyBase64);

        boolean result = validator.validateSignature("body", "not-valid-base64!!!");

        assertThat(result).isFalse();
    }
}
