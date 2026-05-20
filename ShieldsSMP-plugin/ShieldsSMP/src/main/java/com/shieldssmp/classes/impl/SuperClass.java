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

/**
 * Super – Solar Flight is elytra-style glide with boost, NOT infinite creative flight.
 * Ability 1: Laser Eyes – true damage, pierces shields, visible beam.
 * Ability 2: Heavy Punch – instant high-knockback melee strike (NOT a glide).
 * Ultimate: Supernova – fly up, sneak or wait 3s to slam down, shockwave on impact.
 */
public class SuperClass extends PlayerClass {

    // Solar Flight: max 8s boost stamina in ticks (160 ticks)
    private static final int MAX_STAMINA = 160;
    private final Map<UUID, Integer> flightStamina = new HashMap<>();
    private final Map<UUID, BossBar> staminaBars   = new HashMap<>();
    private final Set<UUID> laserActive             = new HashSet<>();
    private final Set<UUID> novaActive              = new HashSet<>();

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
        // Allow elytra-style flight (NOT creative fly – just allow so we can boost via velocity)
        player.setAllowFlight(false); // starts grounded
    }

    @Override
    public void onUnequip(Player player) {
        UUID id = player.getUniqueId();
        laserActive.remove(id);
        novaActive.remove(id);
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
        laserActive.remove(id);
        novaActive.remove(id);
        flightStamina.put(id, MAX_STAMINA);
        BossBar bar = staminaBars.get(id);
        if (bar != null) bar.setProgress(1.0);
    }

    // ── Passive: Solar Flight – elytra-style boost, 8s max, 30s recharge ──────
    // Press Space while in air (or use /glide) to boost upward/forward.
    // We simulate by monitoring when the player is airborne and has stamina.
    @Override
    public void tickPassive(Player player) {
        UUID id = player.getUniqueId();
        int stamina = flightStamina.getOrDefault(id, MAX_STAMINA);
        BossBar bar = staminaBars.get(id);

        if (!player.isOnGround()) {
            // Slowly drain stamina while airborne (not as fast as creative flight)
            if (stamina > 0) {
                stamina = Math.max(0, stamina - 1); // drain 1 per 10 ticks = ~16s max air time
                flightStamina.put(id, stamina);
                if (stamina == 0) {
                    player.sendActionBar(Component.text("☀ Flight stamina depleted! Recharging in 30s…", NamedTextColor.YELLOW));
                }
            }
        } else {
            // Recharge on ground (30s = 300 ticks / 30 per tick call = 10 calls/s = 3s per 30)
            if (stamina < MAX_STAMINA) {
                stamina = Math.min(MAX_STAMINA, stamina + 1);
                flightStamina.put(id, stamina);
            }
        }

        if (bar != null) bar.setProgress((double) stamina / MAX_STAMINA);
    }

    /**
     * Called by the keybind listener or a jump-boost trick.
     * Gives the player a strong upward + forward velocity burst (elytra-like boost).
     */
    public void activateFlightBoost(Player player) {
        int stamina = flightStamina.getOrDefault(player.getUniqueId(), 0);
        if (stamina <= 0) {
            player.sendActionBar(Component.text("☀ No flight stamina! Recharging…", NamedTextColor.YELLOW));
            return;
        }
        // Boost in look direction with strong upward component
        Vector dir = player.getLocation().getDirection().normalize();
        dir.setY(Math.max(dir.getY() + 0.5, 0.4)).multiply(2.2);
        player.setVelocity(dir);
        // Consume 20 ticks per boost (8 boosts max)
        flightStamina.put(player.getUniqueId(), Math.max(0, stamina - 20));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 5, 0.2, 0, 0.2, 0.04);
    }

    // ── Ability 1: Laser Eyes – true damage beam, pierces armor/shields ───────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "LaserEyes", 2 * MIN)) return;
        startCooldown(player, "LaserEyes", 2 * MIN);

        UUID id = player.getUniqueId();
        laserActive.add(id);
        player.sendActionBar(Component.text("👁 LASER EYES – 6s! Deals 2 hearts/s, pierces shields!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> hitThisSec = new HashSet<>();

            @Override public void run() {
                if (!player.isOnline() || !laserActive.contains(id)) { cancel(); return; }
                if (++ticks > 120) { laserActive.remove(id); cancel(); return; }

                // Fire beam every tick for visual, damage every 20 ticks
                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                Location pos = eye.clone();

                if (ticks % 20 == 0) hitThisSec.clear(); // reset per-second hit list

                for (int step = 0; step < 60; step++) {
                    pos.add(dir);

                    // Dense visual beam
                    if (step % 3 == 0) {
                        player.getWorld().spawnParticle(Particle.FLAME, pos, 1, 0.05, 0.05, 0.05, 0);
                        if (step % 6 == 0)
                            player.getWorld().spawnParticle(Particle.CRIT, pos, 1, 0.05, 0.05, 0.05, 0);
                    }

                    // Damage every second via setHealth (true damage, bypasses shields/armor)
                    if (ticks % 20 == 0) {
                        for (Entity e : player.getWorld().getNearbyEntities(pos, 0.7, 0.7, 0.7)) {
                            if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
                            UUID eid = le.getUniqueId();
                            if (hitThisSec.contains(eid)) continue;
                            hitThisSec.add(eid);
                            // TRUE damage – setHealth directly (not .damage() which armor reduces)
                            double newHp = le.getHealth() - 4; // 2 hearts
                            le.setHealth(Math.max(0, newHp));
                            le.setFireTicks(40);
                            if (le instanceof Player p2)
                                p2.sendActionBar(Component.text("👁 Laser Eyes: 2 hearts true damage!", NamedTextColor.RED));
                            break;
                        }
                    }

                    // Stop at solid non-transparent blocks
                    if (pos.getBlock().getType().isSolid()) break;
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Heavy Punch – instant melee strike, NOT a glide ───────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "HeavyPunch", (long)(1.5 * MIN))) return;

        // Find the nearest player within 5 blocks (melee range)
        Player target = null;
        double best = Double.MAX_VALUE;
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            double d = player.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; target = p; }
        }

        if (target == null) {
            player.sendActionBar(Component.text("💪 No player in melee range (5 blocks)!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "HeavyPunch", (long)(1.5 * MIN));

        // True damage: 5 hearts
        target.setHealth(Math.max(0, target.getHealth() - 10));

        // Massive knockback in look direction
        Vector kb = player.getLocation().getDirection().normalize().multiply(4.0);
        kb.setY(1.2);
        target.setVelocity(kb);

        player.sendActionBar(Component.text("💪 HEAVY PUNCH! 5 hearts + massive knockback!", NamedTextColor.GOLD, TextDecoration.BOLD));
        target.sendActionBar(Component.text("💢 HEAVY PUNCHED! 5 hearts!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 2f, 0.7f);
        player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0,1,0), 25, 0.5, 0.5, 0.5, 0.3);
        player.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation().add(0,1,0), 5, 0.3, 0.3, 0.3, 0);
    }

    // ── Ultimate: Supernova – fly up, sneak to crash, shockwave on impact ─────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Supernova", 5 * MIN)) return;
        startCooldown(player, "Supernova", 5 * MIN);

        UUID id = player.getUniqueId();
        novaActive.add(id);

        player.setVelocity(new Vector(0, 4.0, 0));
        player.setAllowFlight(true);
        player.setFlying(true);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);

        player.showTitle(Title.title(
                Component.text("☀ SUPERNOVA", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Sneak to slam down! (Auto in 3s)", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(4), Duration.ofMillis(300))));

        new BukkitRunnable() {
            int ticks = 0;
            boolean slamming = false;

            @Override public void run() {
                if (!player.isOnline() || !novaActive.contains(id)) { cancel(); return; }
                ticks++;

                // Flame trail while rising
                if (!slamming)
                    player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 3, 0.2, 0.2, 0.2, 0.05);

                // Sneak OR 3s timeout triggers slam
                if (!slamming && (player.isSneaking() || ticks >= 60)) {
                    slamming = true;
                    player.setVelocity(new Vector(0, -6, 0));
                    // Disable flight so they fall naturally
                    if (!player.getGameMode().equals(GameMode.CREATIVE)) {
                        player.setFlying(false);
                        player.setAllowFlight(false);
                    }
                    player.sendActionBar(Component.text("☀ SLAMMING DOWN!", NamedTextColor.RED, TextDecoration.BOLD));
                    // Trailing particles as they fall
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!player.isOnline() || !novaActive.contains(id)) { cancel(); return; }
                            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 3, 0.2, 0.2, 0.2, 0.02);
                        }
                    }.runTaskTimer(getPlugin(), 0L, 2L);
                }

                // Detect landing while slamming
                if (slamming && player.isOnGround()) {
                    novaActive.remove(id);
                    cancel();
                    triggerImpact(player);
                }

                if (ticks > 300) { novaActive.remove(id); cancel(); } // safety
            }
        }.runTaskTimer(getPlugin(), 5L, 1L);
    }

    private void triggerImpact(Player player) {
        Location loc   = player.getLocation();
        World    world = player.getWorld();
        double radius  = 15;

        // Huge impact FX
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 10, 3, 0, 3);
        world.spawnParticle(Particle.FLAME, loc, 200, 6, 2, 6, 0.15);
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 80, 5, 2, 5);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);

        // Expanding fire ring
        new BukkitRunnable() {
            double r = 0;
            @Override public void run() {
                r += 1.5;
                for (int i = 0; i < 36; i++) {
                    double a = (2 * Math.PI / 36) * i;
                    world.spawnParticle(Particle.FLAME,
                            loc.getX() + r * Math.cos(a), loc.getY() + 0.1,
                            loc.getZ() + r * Math.sin(a), 1, 0, 0, 0, 0.06);
                }
                if (r >= radius) cancel();
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        // 6 hearts damage + 10s fire + knockback to all nearby
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.setHealth(Math.max(0, p.getHealth() - 12)); // 6 hearts
            p.setFireTicks(200);
            Vector kb = p.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(3.0);
            kb.setY(0.8);
            p.setVelocity(kb);
            p.sendActionBar(Component.text("☀ SUPERNOVA: 6 hearts + fire!", NamedTextColor.RED, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("☀ SUPERNOVA IMPACT!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
    }

    public boolean isNovaActive(UUID id) { return novaActive.contains(id); }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
