package cn.bilicraft.bccyberware.config.model;

import java.util.UUID;

public record ResourcePackSpec(
        String id,
        UUID uuid,
        String url,
        byte[] sha1,
        boolean required,
        String prompt
) {
    public ResourcePackSpec {
        sha1 = sha1.clone();
    }

    @Override
    public byte[] sha1() {
        return sha1.clone();
    }
}

