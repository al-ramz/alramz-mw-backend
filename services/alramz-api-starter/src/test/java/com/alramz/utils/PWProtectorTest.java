package com.alramz.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PWProtectorTest {

    @Test
    void roundTripEncryptDecryptWithSplitProperties() {
        PWProtector protector = new PWProtector("1234567890123456");
        String plaintext = "SuperSecret123!";

        String[] parts = protector.encrypt(plaintext);
        assertThat(parts).hasSize(2);
        assertThat(parts[0]).isNotBlank();
        assertThat(parts[1]).isNotBlank();

        String decrypted = protector.decrypt(parts[0], parts[1]);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void decryptLegacyJsonFormatStillWorks() {
        PWProtector protector = new PWProtector("1234567890123456");
        String plaintext = "LegacySecret123!";

        String[] parts = protector.encrypt(plaintext);
        String legacyFormat = parts[0] + ":" + parts[1];

        String decrypted = protector.decrypt(legacyFormat);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void differentPlaintextsProduceDifferentCiphertexts() {
        PWProtector protector = new PWProtector("1234567890123456");

        String[] parts1 = protector.encrypt("text1");
        String[] parts2 = protector.encrypt("text2");

        assertThat(parts1[0]).isNotEqualTo(parts2[0]);
        assertThat(parts1[1]).isNotEqualTo(parts2[1]);
    }
}
