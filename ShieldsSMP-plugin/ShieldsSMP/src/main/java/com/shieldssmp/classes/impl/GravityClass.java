package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class GravityClass extends PlayerClass {

    private final Set<UUID> frozenInHorizon = new HashSet<>();
    private final Set<UUID> orbitalTargets  = new HashSet<>();

    @Override public String getName()         { return "Gravity"; }
    @Override public String getDescription()  { return "Bend the laws of physics"; }
    @Override public String getAbility1Name() { return "Vector Shift"; }
    @Override public String getAbility2Name() { return "Orbital Slam"; }
    @Override public String getUltimateName() { return "Event Horizon"; }

    // ── Passive: Moon Leap ────────────────────────────────────────────────────
    @Override
    public void tickPassive(Player player) {
        if (player.isSneaking())
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 5, false, false, false));
        else
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    // ── Ability 1: Vector Shift – nearest player within 20 blocks ─────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "VectorShift", 2 * MIN)) return;

        // Find nearest player (broad range, no aim required)
        Player target = getNearestPlayer(player, 20);
        if (target == null) {
            player.sendActionBar(Component.text("⚡ No player within 20 blocks!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "VectorShift", 2 * MIN);

        String[] dirs = {"Up", "Down", "Left", "Right"};
        String chosen = dirs[new Random().nextInt(dirs.length)];

        // Calculate directions relative to attacker's facing
        Vector look  = player.getLocation().getDirection().normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        Vector v = switch (chosen) {
            case "Up"    -> new Vector(0, 4.5, 0);
            case "Down"  -> new Vector(0, -5, 0);
            case "Left"  -> right.clone().multiply(-3.5).setY(0.4);
            default      -> right.clone().multiply(3.5).setY(0.4);
        };

        target.setVelocity(v);

        // Show AoE ring at target's feet
        showAoERing(target.getLocation(), 1.5, NamedTextColor.BLUE);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        player.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0,1,0), 25, 0.4, 0.8, 0.4);
        player.sendActionBar(Component.text("🌀 Vector Shift: " + target.getName() + " → " + chosen + "!", NamedTextColor.AQUA, TextDecoration.BOLD));
        target.sendActionBar(Component.text("🌀 Vector Shifted " + chosen + "!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
    }

    // ── Ability 2: Orbital Slam – clutchable, fall damage applies ────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "OrbitalSlam", 2 * MIN)) return;

        Player target = getNearestPlayer(player, 25);
        if (target == null) {
            player.sendActionBar(Component.text("⚡ No player within 25 blocks!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "OrbitalSlam", 2 * MIN);
        UUID tid = target.getUniqueId();
        orbitalTargets.add(tid);

        // Show 5-block AoE ring at current location (where they'll slam back down near)
        showAoERing(target.getLocation(), 5, NamedTextColor.RED);

        // Launch straight up
        target.setVelocity(new Vector(0, 5, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.5f);
        player.sendActionBar(Component.text("☄ Orbital Slam: " + target.getName() + " launched!", NamedTextColor.RED, TextDecoration.BOLD));
        target.showTitle(Title.title(
                Component.text("☄ ORBITAL SLAM!", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Use abilities to survive!", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(300))));

        // After 1.5s slam them down – fall damage applies (clutchable with abilities)
        new BukkitRunnable() {
            @Override public void run() {
                if (!target.isOnline()) { orbitalTargets.remove(tid); return; }
                target.setVelocity(new Vector(0, -6, 0));
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                target.sendActionBar(Component.text("☄ SLAMMING DOWN! (Fall damage applies)", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            }
        }.runTaskLater(getPlugin(), 30L);

        // Remove orbital tag after 5s (enough time to have landed)
        new BukkitRunnable() {
            @Override public void run() { orbitalTargets.remove(tid); }
        }.runTaskLater(getPlugin(), 100L);
    }

    public boolean isOrbitalTarget(UUID id) { return orbitalTargets.contains(id); }

    // ── Ultimate: Event Horizon ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "EventHorizon", 5 * MIN)) return;
        startCooldown(player, "EventHorizon", 5 * MIN);

        Location center = player.getLocation().clone();
        World world = player.getWorld();
        double radius = 15;

        // Show large AoE ring
        showLargeAoERing(center, radius, world);

        List<UUID> frozenThisTime = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            UUID pid = p.getUniqueId();
            frozenInHorizon.add(pid);
            frozenThisTime.add(pid);
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   200, 255, false, false, false));
            p.sendActionBar(Component.text("🌀 Caught in Event Horizon!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }

        // Caster gets free flight
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false, false));
        player.sendActionBar(Component.text("🌀 EVENT HORIZON – 10s! You can fly!", NamedTextColor.AQUA, TextDecoration.BOLD));
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.4f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 50) { cancel(); return; }
                double a = t * 0.3;
                for (int i = 0; i < 20; i++) {
                    double angle = (2 * Math.PI / 20) * i + a;
                    world.spawnParticle(Particle.PORTAL,
                            center.getX() + radius * Math.cos(angle),
                            center.getY() + 1,
                            center.getZ() + radius * Math.sin(angle), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);

        new BukkitRunnable() {
            @Override public void run() {
                frozenThisTime.forEach(pid -> {
                    frozenInHorizon.remove(pid);
                    Player p = Bukkit.getPlayer(pid);
                    if (p != null) {
                        p.removePotionEffect(PotionEffectType.LEVITATION);
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        p.sendActionBar(Component.text("🌀 Event Horizon ended", NamedTextColor.GRAY));
                    }
                });
                if (player.isOnline()) {
                    if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                    player.removePotionEffect(PotionEffectType.SPEED);
                }
            }
        }.runTaskLater(getPlugin(), 200L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Player getNearestPlayer(Player player, double range) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            double d = player.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }

    private void showAoERing(Location center, double radius, NamedTextColor color) {
        World world = center.getWorld();
        if (world == null) return;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 20) { cancel(); return; }
                for (int i = 0; i < 24; i++) {
                    double angle = (2 * Math.PI / 24) * i;
                    world.spawnParticle(Particle.CRIT,
                            center.getX() + radius * Math.cos(angle),
                            center.getY() + 0.05,
                            center.getZ() + radius * Math.sin(angle), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);
    }

    private void showLargeAoERing(Location center, double radius, World world) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 10) { cancel(); return; }
                for (int i = 0; i < 48; i++) {
                    double angle = (2 * Math.PI / 48) * i;
                    world.spawnParticle(Particle.PORTAL,
                            center.getX() + radius * Math.cos(angle),
                            center.getY() + 0.05,
                            center.getZ() + radius * Math.sin(angle), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
