package com.tranverse.chatserver.utils;

import java.util.UUID;

public final class UsernameUtils {

    private UsernameUtils() {
    }

    public static String generate(String email) {
        return email.split("@")[0]
                + "_"
                + UUID.randomUUID().toString().substring(0, 4);
    }
}