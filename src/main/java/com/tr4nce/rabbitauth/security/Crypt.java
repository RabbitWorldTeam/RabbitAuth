package com.tr4nce.rabbitauth.security;

import java.security.*;
import java.util.*;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Salted Argon2id hashes. No reversible password encryption or legacy format. */
public final class Crypt {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private Crypt() {}
    public static String hash(char[] password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt);
        try {
            return "$argon2id$v=19$m=65536,t=3,p=1$" + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(hash);
        } finally { Arrays.fill(hash, (byte) 0); }
    }
    public static boolean verify(char[] password, String encoded) {
        if (encoded == null || !encoded.startsWith("$argon2id$v=19$m=65536,t=3,p=1$")) return false;
        byte[] actual = null;
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 6) return false;
            byte[] salt = Base64.getDecoder().decode(parts[4]);
            byte[] expected = Base64.getDecoder().decode(parts[5]);
            if (salt.length != 16 || expected.length != 32) return false;
            actual = derive(password, salt);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) { return false; }
        finally { if (actual != null) Arrays.fill(actual, (byte) 0); }
    }
    private static byte[] derive(char[] password, byte[] salt) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(65536)
                .withIterations(3).withParallelism(1).withSalt(salt).build();
        try {
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(parameters);
            byte[] result = new byte[32];
            generator.generateBytes(password, result);
            return result;
        } finally { parameters.clear(); }
    }
}
