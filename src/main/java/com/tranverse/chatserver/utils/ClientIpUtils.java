package com.tranverse.chatserver.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpUtils {
    private ClientIpUtils() {}

    public static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("x-forwarded-for");

        if(forwardedFor != null && !forwardedFor.isBlank()){
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("x-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
