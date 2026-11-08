package com.tr4nce.rabbitauth.storage;

import java.util.Locale;

public record Account(String name, String passwordHash, String registrationIp) {
    public static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}
