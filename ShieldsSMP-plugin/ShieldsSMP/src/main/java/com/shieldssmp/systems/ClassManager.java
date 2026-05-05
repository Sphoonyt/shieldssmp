package com.shieldssmp.systems;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.classes.impl.*;
import com.shieldssmp.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ClassManager {

    private final ShieldsSMP plugin;

    /** Registry of available classes by lowercase name */
    private final Map<String, PlayerClass> registry = new LinkedHashMap<>();

    /** Active class per player UUID */
    private final Map<UUID, PlayerClass> activeClass = new HashMap<>();

    /** Loaded player data */
    private final Map<UUID, PlayerData> dataMap = new HashMap<>();

    /** Players whose abilities are disabled (Nullifier Axe) */
    private final Map<UUID, Long> abilityDisabled   = new HashMap<>();
    private final Map<UUID, Long> nullifierCooldowns = new HashMap<>();

    public ClassManager(ShieldsSMP plugin) {
        this.plugin = plugin;
        registerClasses();
        startPassiveTicker();
    }

    // ── Class registry ─────────────────────────────────────────────────────────

    private void registerClasses() {
        register(new PhantomClass());
        register(new RandomizeClass());
        register(new LarpClass());
        register(new LifeClass());
        register(new GravityClass());
        register(new TerroristClass());
        register(new BossClass());
        register(new SuperClass());
    }

    private void register(PlayerClass cls) {
        registry.put(cls.getName().toLowerCase(), cls);
    }

    public Collection<PlayerClass> getAllClasses() { return registry.values(); }

    public PlayerClass getClassByName(String name) {
        return registry.get(name.toLowerCase());
    }

    // ── Player data ────────────────────────────────────────────────────────────

    public PlayerData getPlayerData(UUID id) {
        return dataMap.computeIfAbsent(id, k -> PlayerData.load(plugin, k));
    }

    public void savePlayerData(UUID id) {
        PlayerData data = dataMap.get(id);
        if (data != null) data.save(plugin);
    }

    public void loadPlayer(Player player) {
        UUID id = player.getUniqueId();
        PlayerData data = PlayerData.load(plugin, id);
        dataMap.put(id, data);

        if (data.hasClass()) {
            PlayerClass cls = getClassByName(data.getClassName());
            if (cls != null) {
                activeClass.put(id, cls);
                cls.onEquip(player);
            }
        }
    }

    public void unloadPlayer(Player player) {
        UUID id = player.getUniqueId();
        PlayerClass cls = activeClass.remove(id);
        if (cls != null) cls.onUnequip(player);
        savePlayerData(id);
        dataMap.remove(id);
        abilityDisabled.remove(id);
    }

    // ── Class assignment ───────────────────────────────────────────────────────

    public boolean setClass(Player player, String className, boolean save) {
        PlayerClass cls = getClassByName(className);
        if (cls == null) return false;

        UUID id = player.getUniqueId();

        // Remove old class
        PlayerClass old = activeClass.get(id);
        if (old != null) old.onUnequip(player);

        activeClass.put(id, cls);
        cls.onEquip(player);

        PlayerData data = getPlayerData(id);
        data.setClassName(cls.getName());
        if (save) data.save(plugin);

        player.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                         .append(Component.text("Class set to ", NamedTextColor.GRAY))
                         .append(Component.text(cls.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                         .append(Component.text("!", NamedTextColor.GRAY)));
        return true;
    }

    public PlayerClass getPlayerClass(UUID id) { return activeClass.get(id); }

    // ── Leveling ───────────────────────────────────────────────────────────────

    public boolean upgradeLevel(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        if (!data.hasClass()) return false;
        if (data.getLevel() >= 2) return false;
        data.setLevel(2);
        data.save(plugin);
        return true;
    }

    public int getLevel(UUID id) {
        return getPlayerData(id).getLevel();
    }

    // ── Ability disable system (Nullifier Axe) ─────────────────────────────────

    public void disableAbilities(Player player, long durationMs) {
        abilityDisabled.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        player.sendActionBar(Component.text(
                "☠ Abilities DISABLED for " + (durationMs / 1000) + "s!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
    }

    public boolean abilitiesDisabled(UUID id) {
        Long expiry = abilityDisabled.get(id);
        if (expiry == null) return false;
        if (expiry <= System.currentTimeMillis()) {
            abilityDisabled.remove(id);
            return false;
        }
        return true;
    }

    // ── Passive ticker ─────────────────────────────────────────────────────────

    private void startPassiveTicker() {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    PlayerClass cls = activeClass.get(id);
                    if (cls == null) continue;
                    if (abilitiesDisabled(id)) continue;
                    cls.tickPassive(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ── Ability dispatchers ────────────────────────────────────────────────────

    public void useAbility1(Player player) {
        if (abilitiesDisabled(player.getUniqueId())) {
            notifyDisabled(player); return;
        }
        if (getLevel(player.getUniqueId()) < 2) {
            notifyLocked(player); return;
        }
        PlayerClass cls = activeClass.get(player.getUniqueId());
        if (cls == null) { noClass(player); return; }
        cls.useAbility1(player);
    }

    public void useAbility2(Player player) {
        if (abilitiesDisabled(player.getUniqueId())) {
            notifyDisabled(player); return;
        }
        if (getLevel(player.getUniqueId()) < 2) {
            notifyLocked(player); return;
        }
        PlayerClass cls = activeClass.get(player.getUniqueId());
        if (cls == null) { noClass(player); return; }
        cls.useAbility2(player);
    }

    public void useUltimate(Player player) {
        if (abilitiesDisabled(player.getUniqueId())) {
            notifyDisabled(player); return;
        }
        if (getLevel(player.getUniqueId()) < 2) {
            notifyLocked(player); return;
        }
        PlayerClass cls = activeClass.get(player.getUniqueId());
        if (cls == null) { noClass(player); return; }
        cls.useUltimate(player);
    }

    private void notifyDisabled(Player p) {
        p.sendActionBar(Component.text("☠ Your abilities are disabled!", NamedTextColor.DARK_RED));
    }

    private void notifyLocked(Player p) {
        p.sendActionBar(Component.text("🔒 Use a Class Upgrade Core to unlock abilities!", NamedTextColor.RED));
    }

    public boolean isOnNullifierCooldown(UUID id) {
        Long expiry = nullifierCooldowns.get(id);
        if (expiry == null) return false;
        if (expiry <= System.currentTimeMillis()) { nullifierCooldowns.remove(id); return false; }
        return true;
    }

    public void putNullifierCooldown(UUID id) {
        nullifierCooldowns.put(id, System.currentTimeMillis() + 3 * 60_000L);
    }

    private void noClass(Player p) {
        p.sendActionBar(Component.text("❌ You have no class! Use /class <name>", NamedTextColor.RED));
    }
}
