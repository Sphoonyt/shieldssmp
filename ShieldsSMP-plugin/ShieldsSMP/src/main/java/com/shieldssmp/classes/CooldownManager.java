package com.shieldssmp.classes;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> data = new HashMap<>();

    public void set(UUID playerId, String key, long durationMs) {
        data.computeIfAbsent(playerId, k -> new HashMap<>())
            .put(key, System.currentTimeMillis() + durationMs);
    }

    /**
     * Set cooldown AND apply a visual bar on the given material in the player's hotbar.
     * Uses vanilla setCooldown() which shows the ender-pearl style greyed-out overlay.
     */
    public void set(Player player, String key, long durationMs, Material visualItem) {
        set(player.getUniqueId(), key, durationMs);
        // setCooldown takes ticks (20 ticks = 1 second)
        int ticks = (int)(durationMs / 50); // ms -> ticks (1 tick = 50ms)
        player.setCooldown(visualItem, ticks);
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
