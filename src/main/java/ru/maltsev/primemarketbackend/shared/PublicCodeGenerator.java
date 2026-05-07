package ru.maltsev.primemarketbackend.shared;

import java.security.SecureRandom;

public final class PublicCodeGenerator {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicCodeGenerator() {
    }

    public static String generate(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Public code prefix is required");
        }

        StringBuilder code = new StringBuilder(prefix.length() + 1 + CODE_LENGTH);
        code.append(prefix.trim().toUpperCase()).append('-');
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
