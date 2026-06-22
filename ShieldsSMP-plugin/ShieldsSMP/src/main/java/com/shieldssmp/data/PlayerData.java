package com.shieldssmp.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerData {

    private final UUID playerId;
    private String className;
    private int level;
    private int lives;
    /** UUIDs this player has marked as trusted (one-directional) */
    private final Set<UUID> trustedPlayers = new HashSet<>();

    public static final int MAX_LIVES = 10;

    public PlayerData(UUID playerId) {
        this.playerId  = playerId;
        this.className = null;
        this.level     = 1;
        this.lives     = 5; // players start with 5 lives
    }

    public UUID   getPlayerId()              { return playerId; }
    public String getClassName()             { return className; }
    public void   setClassName(String name)  { this.className = name; }
    public int    getLevel()                 { return level; }
    public void   setLevel(int level)        { this.level = level; }
    public int    getLives()                 { return lives; }
    public void   setLives(int lives)        { this.lives = Math.max(0, Math.min(MAX_LIVES, lives)); }
    public boolean hasClass()               { return className != null; }

    // ── Trust system ──────────────────────────────────────────────────────────

    public Set<UUID> getTrustedPlayers() { return trustedPlayers; }

    public boolean isTrusted(UUID other) {
        return playerId.equals(other) || trustedPlayers.contains(other);
    }

    /** Toggle trust for a player. Returns true if now trusted, false if removed. */
    public boolean toggleTrust(UUID other) {
        if (trustedPlayers.contains(other)) {
            trustedPlayers.remove(other);
            return false;
        } else {
            trustedPlayers.add(other);
            return true;
        }
    }

    public void save(JavaPlugin plugin) {
        File dir = new File(plugin.getDataFolder(), "playerdata");
        dir.mkdirs();
        File file = new File(dir, playerId + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("className", className);
        cfg.set("level",     level);
        cfg.set("lives",     lives);
        cfg.set("trusted",   trustedPlayers.stream().map(UUID::toString).toList());
        try { cfg.save(file); } catch (IOException ignored) {}
    }

    public static PlayerData load(JavaPlugin plugin, UUID id) {
        File file = new File(new File(plugin.getDataFolder(), "playerdata"), id + ".yml");
        PlayerData data = new PlayerData(id);
        if (!file.exists()) return data;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        data.className = cfg.getString("className", null);
        data.level     = cfg.getInt("level", 1);
        data.lives     = cfg.getInt("lives", 5);
        for (String s : cfg.getStringList("trusted")) {
            try { data.trustedPlayers.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }
}
