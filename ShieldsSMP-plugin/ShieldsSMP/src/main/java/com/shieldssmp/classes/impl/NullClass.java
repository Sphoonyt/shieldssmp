package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class NullClass extends PlayerClass {

    /** Active Null Fields: caster UUID → field center */
    private final Map<UUID, Location> activeFields = new HashMap<>();
    /** Players currently suppressed by a Null Field */
    private final Set<UUID> suppressed = new HashSet<>();
    /** Active Null Zones: caster UUID → list of placed barrier blocks */
    private final Map<UUID, List<Location>> activeZones = new HashMap<>();

    @Override public String getName()         { return "Null"; }
    @Override public String getDescription()  { return "Erase. Suppress. Control."; }
    @Override public String getAbility1Name() { return "Null Field"; }
    @Override public String getAbility2Name() { return "Overwrite"; }
    @Override public String getUltimateName() { return "Null Zone"; }
    @Override public String getAbility1CooldownKey() { return "NullField"; }
    @Override public String getAbility2CooldownKey() { return "Overwrite"; }
    @Override public String getUltimateCooldownKey() { return "NullZone"; }

    public boolean isSuppressed(UUID id) { return suppressed.contains(id); }

    // ── Passive: Dampening Aura – 3-block radius slows CD recovery (implemented
    // via ClassManager's ability dispatcher: if enemy is near Null player, add delay)
    // We store the Null player's position and check in ClassManager.useAbility1/2/ult
    // Simplified: apply Slowness to nearby enemies as proxy
    @Override
    public void tickPassive(Player player) {
        // Notify ClassManager of Null player position for cooldown penalty
        // (The actual cooldown slow is handled in ClassManager via check)
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 3, 3, 3)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            // Apply slight haste penalty as visual proxy
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 25, 0, false, false, false));
        }
    }

    // ── Ability 1: Null Field – 5x5 ability suppression zone for 15s ──────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "NullField", 2 * MIN)) return;
        startCooldown(player, "NullField", 2 * MIN);

        Location center = player.getLocation().clone();
        UUID id = player.getUniqueId();
        activeFields.put(id, center);
        World world = player.getWorld();

        player.sendActionBar(Component.text("⬛ NULL FIELD active – 15s suppression zone!", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 2f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 150 || !activeFields.containsKey(id)) {
                    activeFields.remove(id);
                    suppressed.clear();
                    cancel(); return;
                }

                // Particle ring showing zone boundary
                for (int i = 0; i < 32; i++) {
                    double a = (2 * Math.PI / 32) * i + t * 0.05;
                    world.spawnParticle(Particle.DUST,
                            center.getX() + 5 * Math.cos(a), center.getY() + 0.1,
                            center.getZ() + 5 * Math.sin(a), 1,
                            new Particle.DustOptions(Color.BLACK, 1.5f));
                }

                // Suppress all enemies inside 5-block radius
                for (Entity e : world.getNearbyEntities(center, 5, 5, 5)) {
                    if (!(e instanceof Player p) || p.equals(player)) continue;
                    suppressed.add(p.getUniqueId());
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.GLOWING, 30, 0, false, true, false));
                }
                // Remove suppression for players who left the zone
                suppressed.removeIf(pid -> {
                    Player p = Bukkit.getPlayer(pid);
                    return p == null || center.distance(p.getLocation()) > 5;
                });
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);
    }

    // ── Ability 2: Overwrite – copy last ability used against you ─────────────
    // Tracked in GlobalListener; stored as a Runnable
    private final Map<UUID, Runnable> storedAbility = new HashMap<>();

    public void storeLastAbility(UUID id, Runnable ability) {
        storedAbility.put(id, ability);
    }

    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "Overwrite", 5 * MIN)) return;

        Runnable stored = storedAbility.get(player.getUniqueId());
        if (stored == null) {
            player.sendActionBar(Component.text("⬛ No ability recorded yet!", NamedTextColor.GRAY));
            return;
        }

        startCooldown(player, "Overwrite", 5 * MIN);
        stored.run();
        player.sendActionBar(Component.text("⬛ OVERWRITE – copied last ability!", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 2f);
    }

    // ── Ultimate: Null Zone – 8x8 barrier box for 20s ────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "NullZone", 4 * MIN)) return;
        startCooldown(player, "NullZone", 4 * MIN);

        Location center = player.getLocation().clone();
        World world = player.getWorld();
        UUID id = player.getUniqueId();
        List<Location> walls = new ArrayList<>();

        int half = 4; int height = 5;
        // Build walls (air check: don't replace non-air blocks)
        for (int x = -half; x <= half; x++) {
            for (int y = 0; y <= height; y++) {
                for (int z = -half; z <= half; z++) {
                    boolean isWall = Math.abs(x) == half || Math.abs(z) == half || y == height;
                    if (!isWall) continue;
                    Location bLoc = center.clone().add(x, y, z);
                    if (bLoc.getBlock().getType().isAir()) {
                        bLoc.getBlock().setType(Material.BARRIER);
                        walls.add(bLoc);
                    }
                }
            }
        }
        activeZones.put(id, walls);

        // Suppress all inside
        for (Entity e : world.getNearbyEntities(center, half, height, half)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            suppressed.add(p.getUniqueId());
            p.sendActionBar(Component.text("⬛ NULL ZONE – you are locked in! No abilities!", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("⬛ NULL ZONE erected – 20s!", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.6f);

        new BukkitRunnable() {
            @Override public void run() {
                List<Location> list = activeZones.remove(id);
                if (list != null) list.forEach(l -> { if (l.getBlock().getType() == Material.BARRIER) l.getBlock().setType(Material.AIR); });
                suppressed.clear();
                if (player.isOnline()) player.sendActionBar(Component.text("⬛ Null Zone collapsed", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 400L);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        activeFields.remove(id);
        suppressed.removeIf(pid -> { Player p = Bukkit.getPlayer(pid); return p != null && p.getUniqueId().equals(id); });
        List<Location> walls = activeZones.remove(id);
        if (walls != null) walls.forEach(l -> { if (l.getBlock().getType() == Material.BARRIER) l.getBlock().setType(Material.AIR); });
        storedAbility.remove(id);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
