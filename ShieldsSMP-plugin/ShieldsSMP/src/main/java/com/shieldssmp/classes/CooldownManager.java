package com.shieldssmp.classes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> data = new HashMap<>();

    public void set(UUID player, String key, long durationMs) {
        data.computeIfAbsent(player, k -> new HashMap<>())
            .put(key, System.currentTimeMillis() + durationMs);
    }

    public boolean isOnCooldown(UUID player, String key) {
        Map<String, Long> map = data.get(player);
        if (map == null) return false;
        Long expiry = map.get(key);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    public long remainingSeconds(UUID player, String key) {
        Map<String, Long> map = data.get(player);
        if (map == null) return 0;
        Long expiry = map.get(key);
        if (expiry == null) return 0;
        long rem = expiry - System.currentTimeMillis();
        return rem > 0 ? (rem + 999) / 1000 : 0;
    }

    public void clearPlayer(UUID player) {
        data.remove(player);
    }
}
