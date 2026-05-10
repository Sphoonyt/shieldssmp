package com.shieldssmp.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class PlayerData {

    private final UUID playerId;
    private String className;
    private int level;
    private int lives;

    public static final int MAX_LIVES = 10;

    public PlayerData(UUID playerId) {
        this.playerId  = playerId;
        this.className = null;
        this.level     = 1;
        this.lives     = MAX_LIVES;
    }

    public UUID   getPlayerId()              { return playerId; }
    public String getClassName()             { return className; }
    public void   setClassName(String name)  { this.className = name; }
    public int    getLevel()                 { return level; }
    public void   setLevel(int level)        { this.level = level; }
    public int    getLives()                 { return lives; }
    public void   setLives(int lives)        { this.lives = Math.max(0, Math.min(MAX_LIVES, lives)); }
    public boolean hasClass()               { return className != null; }

    public void save(JavaPlugin plugin) {
        File dir = new File(plugin.getDataFolder(), "playerdata");
        dir.mkdirs();
        File file = new File(dir, playerId + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("className", className);
        cfg.set("level",     level);
        cfg.set("lives",     lives);
        try { cfg.save(file); } catch (IOException ignored) {}
    }

    public static PlayerData load(JavaPlugin plugin, UUID id) {
        File file = new File(new File(plugin.getDataFolder(), "playerdata"), id + ".yml");
        PlayerData data = new PlayerData(id);
        if (!file.exists()) return data;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        data.className = cfg.getString("className", null);
        data.level     = cfg.getInt("level", 1);
        data.lives     = cfg.getInt("lives", MAX_LIVES);
        return data;
    }
}
