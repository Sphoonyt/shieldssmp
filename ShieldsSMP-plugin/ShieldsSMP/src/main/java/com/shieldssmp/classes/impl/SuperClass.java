package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SuperClass extends PlayerClass {

    /** Max flight stamina ticks (8 seconds = 160 ticks) */
    private static final int MAX_STAMINA = 160;

    private final Map<UUID, Integer> flightStamina = new HashMap<>();
    private final Map<UUID, BossBar> staminaBars   = new HashMap<>();
    private final Set<UUID> flying                  = new HashSet<>();
    private final Set<UUID> laserActive             = new HashSet<>();

    @Override public String getName()         { return "Super"; }
    @Override public String getDescription()  { return "Faster than a creeper, stronger than a golem"; }
    @Override public String getAbility1Name() { return "Laser Eyes"; }
    @Override public String getAbility2Name() { return "Heavy Punch"; }
    @Override public String getUltimateName() { return "Supernova"; }

    @Override
    public void onEquip(Player player) {
        UUID id = player.getUniqueId();
        flightStamina.put(id, MAX_STAMINA);
        BossBar bar = Bukkit.createBossBar("☀ Flight Stamina", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.addPlayer(player);
        staminaBars.put(id, bar);
    }

    @Override
    public void onUnequip(Player player) {
        UUID id = player.getUniqueId();
        flying.remove(id);
        BossBar bar = staminaBars.remove(id);
        if (bar != null) bar.removeAll();
        flightStamina.remove(id);
        if (!player.getGameMode().equals(GameMode.CREATIVE)) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        onUnequip(player);
        onEquip(player);
    }

    // ── Passive: Solar Flight ─────────────────────────────────────────────────
    @Override
    public void tickPassive(Player player) {
        UUID id = player.getUniqueId();
        int stamina = flightStamina.getOrDefault(id, MAX_STAMINA);
        BossBar bar = staminaBars.get(id);

        if (player.isFlying() && !player.getGameMode().equals(GameMode.CREATIVE)) {
            if (stamina > 0) {
                flying.add(id);
                stamina -= 2; // 2 ticks per scheduler call (every 10 ticks)
                flightStamina.put(id, stamina);
            } else {
                flying.remove(id);
                player.setFlying(false);
                player.setAllowFlight(false);
                player.sendActionBar(Component.text("☀ Flight stamina depleted!", NamedTextColor.YELLOW));
            }
        } else {
            flying.remove(id);
            // Recharge
            if (stamina < MAX_STAMINA) {
                stamina = Math.min(MAX_STAMINA, stamina + 1);
                flightStamina.put(id, stamina);
                if (stamina == MAX_STAMINA) player.setAllowFlight(true);
            }
        }

        // Ensure flight is allowed when stamina > 0
        if (stamina > 0 && !player.getAllowFlight() && !player.getGameMode().equals(GameMode.CREATIVE)) {
            player.setAllowFlight(true);
        }

        if (bar != null) bar.setProgress((double) stamina / MAX_STAMINA);
    }

    // ── Ability 1: Laser Eyes ─────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "LaserEyes", 2 * MIN)) return;
        startCooldown(player, "LaserEyes", 2 * MIN);

        UUID id = player.getUniqueId();
        laserActive.add(id);

        player.sendActionBar(Component.text("👁 LASER EYES – 6s!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || ticks >= 120) {
                    laserActive.remove(id);
                    cancel();
                    return;
                }
                ticks++;
                if (ticks % 20 != 0) return; // Deal damage every second

                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                Location pos = eye.clone();

                for (int i = 0; i < 50; i++) {
                    pos.add(dir);
                    player.getWorld().spawnParticle(Particle.FLAME, pos, 1, 0.05, 0.05, 0.05, 0);

                    for (Entity e : player.getWorld().getNearbyEntities(pos, 0.8, 0.8, 0.8)) {
                        if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
                        le.damage(4, player); // 2 hearts
                        le.setFireTicks(40);
                        break;
                    }

                    if (!pos.getBlock().getType().isAir()) break;
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Heavy Punch ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "HeavyPunch", (long)(1.5 * MIN))) return;

        Player target = getTargetPlayer(player, 6);
        if (target == null) {
            player.sendActionBar(Component.text("💪 No player in range!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "HeavyPunch", (long)(1.5 * MIN));

        // 5 hearts true damage
        double newHp = target.getHealth() - 10.0;
        target.setHealth(Math.max(0, newHp));

        // Massive knockback
        Vector kb = player.getLocation().getDirection().normalize().multiply(3);
        kb.setY(1.0);
        target.setVelocity(kb);

        player.sendActionBar(Component.text("💪 HEAVY PUNCH!", NamedTextColor.GOLD, TextDecoration.BOLD));
        target.sendActionBar(Component.text("💢 Heavy Punched!", NamedTextColor.DARK_RED));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0,1,0), 20, 0.4, 0.4, 0.4, 0.2);
    }

    // ── Ultimate: Supernova ───────────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Supernova", 5 * MIN)) return;
        startCooldown(player, "Supernova", 5 * MIN);

        World world = player.getWorld();

        // Fly upward
        player.setVelocity(new Vector(0, 3, 0));
        player.sendActionBar(Component.text("☀ SUPERNOVA charging...", NamedTextColor.YELLOW, TextDecoration.BOLD));
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);

        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }

                Location impact = player.getLocation();
                player.setVelocity(new Vector(0, -4, 0));

                new BukkitRunnable() {
                    @Override public void run() {
                        if (!player.isOnline()) { cancel(); return; }
                        if (player.isOnGround()) {
                            cancel();
                            triggerImpact(player, impact);
                        }
                    }
                }.runTaskTimer(getPlugin(), 2L, 1L);

                cancel();
            }
        }.runTaskLater(getPlugin(), 30L);
    }

    private void triggerImpact(Player player, Location loc) {
        World world = player.getWorld();
        double radius = 15;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 8, 3, 0, 3);
        world.spawnParticle(Particle.FLAME, loc, 100, 6, 2, 6, 0.1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);

        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.damage(12, player); // 6 hearts
            p.setFireTicks(200);  // 10s fire
            p.sendActionBar(Component.text("☀ Supernova impact!", NamedTextColor.RED, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("☀ SUPERNOVA!", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    private Player getTargetPlayer(Player player, double range) {
        Player closest = null;
        double bestDot = 0.7;
        var dir = player.getLocation().getDirection().normalize();
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            var toEntity = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            double dot = dir.dot(toEntity);
            if (dot > bestDot) { bestDot = dot; closest = p; }
        }
        return closest;
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return ShieldsSMP.getInstance();
    }
}
