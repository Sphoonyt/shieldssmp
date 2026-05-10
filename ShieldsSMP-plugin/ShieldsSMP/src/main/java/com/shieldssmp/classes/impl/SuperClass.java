package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class SuperClass extends PlayerClass {

    private static final int MAX_STAMINA = 160; // 8s
    private final Map<UUID, Integer>  flightStamina = new HashMap<>();
    private final Map<UUID, BossBar>  staminaBars   = new HashMap<>();
    private final Set<UUID>           laserActive   = new HashSet<>();
    /** Players gliding from Heavy Punch (Ability 2) */
    private final Set<UUID>           gliding       = new HashSet<>();
    private final Set<UUID>           novaFalling   = new HashSet<>();

    @Override public String getName()         { return "Super"; }
    @Override public String getDescription()  { return "Faster than a creeper, stronger than a golem"; }
    @Override public String getAbility1Name() { return "Laser Eyes"; }
    @Override public String getAbility2Name() { return "Power Glide"; }
    @Override public String getUltimateName() { return "Supernova"; }

    @Override
    public void onEquip(Player player) {
        UUID id = player.getUniqueId();
        flightStamina.put(id, MAX_STAMINA);
        BossBar bar = Bukkit.createBossBar("☀ Flight Stamina", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.addPlayer(player);
        staminaBars.put(id, bar);
        player.setAllowFlight(true);
    }

    @Override
    public void onUnequip(Player player) {
        UUID id = player.getUniqueId();
        gliding.remove(id);
        laserActive.remove(id);
        BossBar bar = staminaBars.remove(id);
        if (bar != null) bar.removeAll();
        flightStamina.remove(id);
        if (!player.getGameMode().equals(GameMode.CREATIVE))
            player.setAllowFlight(false);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        gliding.remove(id);
        laserActive.remove(id);
        novaFalling.remove(id);
        flightStamina.put(id, MAX_STAMINA);
        BossBar bar = staminaBars.get(id);
        if (bar != null) bar.setProgress(1.0);
    }

    // ── Passive: Solar Flight ─────────────────────────────────────────────────
    @Override
    public void tickPassive(Player player) {
        UUID id = player.getUniqueId();
        int stamina = flightStamina.getOrDefault(id, MAX_STAMINA);
        BossBar bar = staminaBars.get(id);

        if (player.isFlying() && !player.getGameMode().equals(GameMode.CREATIVE)) {
            if (stamina > 0) {
                stamina = Math.max(0, stamina - 2);
                flightStamina.put(id, stamina);
                if (stamina == 0) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                    player.sendActionBar(Component.text("☀ Flight stamina depleted! Recharging...", NamedTextColor.YELLOW));
                }
            }
        } else {
            if (stamina < MAX_STAMINA) {
                stamina = Math.min(MAX_STAMINA, stamina + 1);
                flightStamina.put(id, stamina);
                if (stamina >= MAX_STAMINA && !player.getAllowFlight() && !player.getGameMode().equals(GameMode.CREATIVE)) {
                    player.setAllowFlight(true);
                    player.sendActionBar(Component.text("☀ Flight recharged!", NamedTextColor.YELLOW));
                }
            }
            if (stamina > 0 && !player.getAllowFlight() && !player.getGameMode().equals(GameMode.CREATIVE))
                player.setAllowFlight(true);
        }

        if (bar != null) bar.setProgress((double) stamina / MAX_STAMINA);
    }

    // ── Ability 1: Laser Eyes – piercing beam ignoring shields ───────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "LaserEyes", 2 * MIN)) return;
        startCooldown(player, "LaserEyes", 2 * MIN);

        UUID id = player.getUniqueId();
        laserActive.add(id);

        player.sendActionBar(Component.text("👁 LASER EYES – 6s! Pierces shields!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> hitThisTick = new HashSet<>();
            @Override public void run() {
                if (!player.isOnline() || !laserActive.contains(id)) { cancel(); return; }
                if (++ticks > 120) { laserActive.remove(id); cancel(); return; }

                hitThisTick.clear();
                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                Location pos = eye.clone();

                for (int i = 0; i < 60; i++) {
                    pos.add(dir);
                    // Particle every 3 blocks for performance
                    if (i % 2 == 0)
                        player.getWorld().spawnParticle(Particle.FLAME, pos, 1, 0.05, 0.05, 0.05, 0);

                    for (Entity e : player.getWorld().getNearbyEntities(pos, 0.6, 0.6, 0.6)) {
                        if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
                        if (e instanceof Player p && hitThisTick.contains(p.getUniqueId())) continue;

                        // Deal true damage bypassing shields (setHealth directly, 2 hearts/sec)
                        if (ticks % 20 == 0) {
                            if (le instanceof Player p2) {
                                hitThisTick.add(p2.getUniqueId());
                                p2.setHealth(Math.max(0, p2.getHealth() - 4)); // 2 hearts
                                p2.setFireTicks(40);
                                p2.sendActionBar(Component.text("👁 Laser Eyes: 2 hearts!", NamedTextColor.RED));
                            } else {
                                le.setHealth(Math.max(0, le.getHealth() - 4));
                                le.setFireTicks(40);
                            }
                        }
                        break;
                    }
                    if (!pos.getBlock().getType().isAir()) break;
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Power Glide – short duration speed glide ──────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "PowerGlide", (long)(1.5 * MIN))) return;
        startCooldown(player, "PowerGlide", (long)(1.5 * MIN));

        UUID id = player.getUniqueId();
        gliding.add(id);

        // Boost in look direction with slight upward lift
        Vector dir = player.getLocation().getDirection().normalize();
        dir.setY(Math.max(dir.getY() + 0.3, 0.3));
        player.setVelocity(dir.multiply(2.8));
        player.setAllowFlight(true);
        player.setFlying(true);

        player.sendActionBar(Component.text("🦅 POWER GLIDE – 3s!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.3f);

        // Keep player aloft for 3 seconds with reduced gravity
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || !gliding.contains(id) || ++ticks > 60) {
                    gliding.remove(id);
                    // Restore flight state based on stamina
                    if (player.isOnline() && !player.getGameMode().equals(GameMode.CREATIVE)) {
                        int stamina = flightStamina.getOrDefault(id, 0);
                        if (stamina <= 0) { player.setFlying(false); player.setAllowFlight(false); }
                    }
                    cancel();
                    return;
                }
                // Apply gentle forward momentum
                Vector v = player.getVelocity();
                if (v.getY() < -0.1) player.setVelocity(v.setY(-0.1)); // reduce fall rate
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 1, 0.1, 0, 0.1, 0.02);
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ultimate: Supernova – crash down, shockwave + 6 hearts + fire ─────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Supernova", 5 * MIN)) return;
        startCooldown(player, "Supernova", 5 * MIN);

        UUID id = player.getUniqueId();
        novaFalling.add(id);

        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);

        // Launch upward
        player.setVelocity(new Vector(0, 3.5, 0));
        player.showTitle(Title.title(
                Component.text("☀ SUPERNOVA", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Hold sneak to crash down!", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(400))));
        player.sendActionBar(Component.text("☀ SUPERNOVA – Sneak to slam down!", NamedTextColor.YELLOW, TextDecoration.BOLD));

        // Check each tick: if player sneaks, slam down; auto-slam after 3s
        new BukkitRunnable() {
            int ticks = 0;
            boolean slamming = false;
            @Override public void run() {
                if (!player.isOnline()) { novaFalling.remove(id); cancel(); return; }
                ticks++;

                if (!slamming && (player.isSneaking() || ticks >= 60)) {
                    // Slam down
                    slamming = true;
                    player.setVelocity(new Vector(0, -5, 0));
                    player.sendActionBar(Component.text("☀ SLAMMING DOWN!", NamedTextColor.RED, TextDecoration.BOLD));
                }

                if (slamming && player.isOnGround()) {
                    novaFalling.remove(id);
                    cancel();
                    triggerNovaImpact(player);
                }

                if (ticks > 200) { novaFalling.remove(id); cancel(); } // safety
            }
        }.runTaskTimer(getPlugin(), 5L, 1L);
    }

    private void triggerNovaImpact(Player player) {
        Location loc = player.getLocation();
        World world  = player.getWorld();
        double radius = 15;

        // Huge FX
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 10, 3, 0, 3);
        world.spawnParticle(Particle.FLAME, loc, 150, 6, 2, 6, 0.15);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 60, 4, 2, 4);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);

        // Expanding ring
        new BukkitRunnable() {
            double r = 0;
            @Override public void run() {
                r += 2;
                for (int i = 0; i < 32; i++) {
                    double angle = (2 * Math.PI / 32) * i;
                    world.spawnParticle(Particle.FLAME,
                            loc.getX() + r * Math.cos(angle), loc.getY() + 0.1,
                            loc.getZ() + r * Math.sin(angle), 1, 0, 0, 0, 0.05);
                }
                if (r >= radius) cancel();
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        // Deal 6 hearts + 10s fire to all nearby
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.setHealth(Math.max(0, p.getHealth() - 12)); // 6 hearts
            p.setFireTicks(200);
            // Knockback outward
            Vector kb = p.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(2.5);
            kb.setY(0.5);
            p.setVelocity(kb);
            p.sendActionBar(Component.text("☀ SUPERNOVA! 6 hearts + fire!", NamedTextColor.RED, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("☀ SUPERNOVA IMPACT!", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    public boolean isNovaFalling(UUID id) { return novaFalling.contains(id); }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
