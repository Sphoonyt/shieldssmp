package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class GravityClass extends PlayerClass {

    private final Set<UUID> frozenInHorizon = new HashSet<>();

    @Override public String getName()         { return "Gravity"; }
    @Override public String getDescription()  { return "Bend the laws of physics"; }
    @Override public String getAbility1Name() { return "Vector Shift"; }
    @Override public String getAbility2Name() { return "Orbital Slam"; }
    @Override public String getUltimateName() { return "Event Horizon"; }

    // ── Passive: Moon Leap – 200% jump height while sneaking ─────────────────
    @Override
    public void tickPassive(Player player) {
        if (player.isSneaking()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 5, false, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    // ── Ability 1: Vector Shift ────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "VectorShift", 2 * MIN)) return;

        Player target = getTargetPlayer(player, 20);
        if (target == null) {
            player.sendActionBar(Component.text("⚡ No player in range!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "VectorShift", 2 * MIN);

        String[] dirs = {"Up", "Down", "Left", "Right"};
        String dir = dirs[new Random().nextInt(dirs.length)];

        Vector v = switch (dir) {
            case "Up"    -> new Vector(0, 3, 0);
            case "Down"  -> new Vector(0, -3, 0);
            case "Left"  -> player.getLocation().getDirection().rotateAroundY(Math.PI / 2).multiply(3);
            default      -> player.getLocation().getDirection().rotateAroundY(-Math.PI / 2).multiply(3);
        };

        target.setVelocity(v);
        player.sendActionBar(Component.text("🌀 Vector Shift: " + target.getName() + " sent " + dir + "!", NamedTextColor.AQUA, TextDecoration.BOLD));
        target.sendActionBar(Component.text("🌀 Vector Shifted " + dir + "!", NamedTextColor.DARK_AQUA));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
    }

    // ── Ability 2: Orbital Slam ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "OrbitalSlam", 2 * MIN)) return;

        Player target = getTargetPlayer(player, 20);
        if (target == null) {
            player.sendActionBar(Component.text("⚡ No player in range!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "OrbitalSlam", 2 * MIN);

        Location up = target.getLocation().clone().add(0, 50, 0);
        target.setVelocity(new Vector(0, 5, 0));

        player.sendActionBar(Component.text("☄ Orbital Slam on " + target.getName() + "!", NamedTextColor.RED, TextDecoration.BOLD));
        target.sendActionBar(Component.text("☄ ORBITAL SLAM! Brace yourself!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.5f);

        // After 2s, slam them back down instantly
        new BukkitRunnable() {
            @Override public void run() {
                if (target.isOnline()) {
                    // Teleport to 2 blocks above where they were (gravity + fall damage handles the rest)
                    target.setVelocity(new Vector(0, -5, 0));
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                }
            }
        }.runTaskLater(getPlugin(), 40L);
    }

    // ── Ultimate: Event Horizon ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "EventHorizon", 5 * MIN)) return;
        startCooldown(player, "EventHorizon", 5 * MIN);

        Location center = player.getLocation();
        World world = player.getWorld();
        double radius = 15;

        // Activate zero-G + freeze for nearby enemies
        List<UUID> frozenThisTime = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            UUID pid = p.getUniqueId();
            frozenInHorizon.add(pid);
            frozenThisTime.add(pid);
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 0, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   200, 255, false, false, false));
            p.sendActionBar(Component.text("🌀 Caught in Event Horizon!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }

        // Player gets free flight
        player.setAllowFlight(true);
        player.setFlying(true);
        player.sendActionBar(Component.text("🌀 EVENT HORIZON active! 10s Zero-G!", NamedTextColor.AQUA, TextDecoration.BOLD));
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.4f);

        // Particle sphere effect
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                double a = t * 0.3;
                for (int i = 0; i < 16; i++) {
                    double angle = (2 * Math.PI / 16) * i + a;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    world.spawnParticle(Particle.PORTAL, x, center.getY() + 1, z, 1, 0, 0, 0, 0);
                }
                if (t >= 50) cancel();
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);

        new BukkitRunnable() {
            @Override public void run() {
                for (UUID pid : frozenThisTime) {
                    frozenInHorizon.remove(pid);
                    Player p = Bukkit.getPlayer(pid);
                    if (p != null && p.isOnline()) {
                        p.removePotionEffect(PotionEffectType.LEVITATION);
                        p.removePotionEffect(PotionEffectType.SLOWNESS);
                        p.sendActionBar(Component.text("🌀 Event Horizon ended", NamedTextColor.GRAY));
                    }
                }
                if (player.isOnline()) {
                    if (!player.getGameMode().equals(GameMode.CREATIVE)) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                    player.sendActionBar(Component.text("🌀 Event Horizon collapsed", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(getPlugin(), 200L);
    }

    private Player getTargetPlayer(Player player, double range) {
        Player closest = null;
        double bestDot = 0.6;
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
