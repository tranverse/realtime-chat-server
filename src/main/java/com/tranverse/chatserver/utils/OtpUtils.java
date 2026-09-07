package com.tranverse.chatserver.utils;

import java.security.SecureRandom;

public final class OtpUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateCode() {
        int number = RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }
}