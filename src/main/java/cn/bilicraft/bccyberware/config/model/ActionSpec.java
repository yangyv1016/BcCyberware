package cn.bilicraft.bccyberware.config.model;

import java.util.Map;

public record ActionSpec(String type, Map<String, Object> values) {
    public ActionSpec {
        values = Map.copyOf(values);
    }

    public String string(String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    public double number(String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    public int integer(String key, int fallback) {
        return (int) Math.round(number(key, fallback));
    }

    public boolean bool(String key, boolean fallback) {
        Object value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}

