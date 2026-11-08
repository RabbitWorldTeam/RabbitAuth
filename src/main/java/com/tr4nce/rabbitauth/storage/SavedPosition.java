package com.tr4nce.rabbitauth.storage;

public record SavedPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
    public SavedPosition {
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30000000 || Math.abs(z) > 30000000 || Math.abs(y) > 20000000
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("Invalid saved position");
    }
}
