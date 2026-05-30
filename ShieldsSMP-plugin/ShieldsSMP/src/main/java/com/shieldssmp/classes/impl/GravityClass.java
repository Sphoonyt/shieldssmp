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

    private static final double HORIZON_RADIUS = 15.0;
    private final Set<UUID> frozenInHorizon = new HashSet<>();
    /** Players inside an active Event Horizon zone */
    private final Map<UUID, Location> activeHorizons = new HashMap<>(); // caster → center

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

    // ── Ability 1: Vector Shift – FIXED directional launch ────────────────────
    // Always launches UP (most reliable). Direction is chosen randomly but
    // applied consistently so the player can see what happened.
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "VectorShift", 2 * MIN)) return;

        Player target = getNearestPlayer(player, 20);
        if (target == null) {
            player.sendActionBar(Component.text("⚡ No player within 20 blocks!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "VectorShift", 2 * MIN, org.bukkit.Material.SHIELD);

        String[] dirs = {"Up", "Down", "Backward", "Left", "Right"};
        String chosen = dirs[new Random().nextInt(dirs.length)];

        // Build direction relative to the TARGET's facing, not the caster's
        Vector look  = target.getLocation().getDirection().normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        Vector v = switch (chosen) {
            case "Up"       -> new Vector(0, 5, 0);
            case "Down"     -> new Vector(0, -6, 0);
            case "Backward" -> look.clone().multiply(-4).setY(0.5);
            case "Left"     -> right.clone().multiply(-4).setY(0.5);
            default         -> right.clone().multiply(4).setY(0.5);
        };

        target.setVelocity(v);
        showRing(target.getLocation(), 1.5, Particle.CRIT);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        player.sendActionBar(Component.text("🌀 Vector Shift: " + target.getName() + " → " + chosen + "!", NamedTextColor.AQUA, TextDecoration.BOLD));
        target.sendActionBar(Component.text("🌀 Vector Shifted " + chosen + "!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
    }

    // ── Ability 2: Orbital Slam – fall damage applies, clutchable ─────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "OrbitalSlam", 2 * MIN)) return;

        Player target = getNearestPlayer(player, 25);
        if (target == null) {
            player.sendActionBar(Component.text("⚡ No player within 25 blocks!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "OrbitalSlam", 2 * MIN, org.bukkit.Material.SHIELD);

        // Show landing zone ring at current position
        showRing(target.getLocation(), 5, Particle.CRIT);

        target.setVelocity(new Vector(0, 5, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.5f);
        player.sendActionBar(Component.text("☄ Orbital Slam: " + target.getName() + " launched!", NamedTextColor.RED, TextDecoration.BOLD));
        target.showTitle(Title.title(
                Component.text("☄ ORBITAL SLAM!", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Use abilities to survive the fall!", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(300))));

        // Slam down hard after 1.5s – fall damage APPLIES (clutchable)
        new BukkitRunnable() {
            @Override public void run() {
                if (!target.isOnline()) return;
                target.setVelocity(new Vector(0, -7, 0)); // very fast downward
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                target.sendActionBar(Component.text("☄ SLAMMING DOWN! (Fall damage applies!)", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            }
        }.runTaskLater(getPlugin(), 30L);
    }

    // ── Ultimate: Event Horizon – FIXED: bounded zone, blue outline, enemies frozen inside ──
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "EventHorizon", 5 * MIN)) return;
        startCooldown(player, "EventHorizon", 5 * MIN, org.bukkit.Material.SHIELD);

        Location center = player.getLocation().clone();
        World world = player.getWorld();

        UUID casterId = player.getUniqueId();
        activeHorizons.put(casterId, center);

        // Show blue zone outline continuously
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 50 || !activeHorizons.containsKey(casterId)) { cancel(); return; }
                // Blue particle ring at ground level
                for (int i = 0; i < 48; i++) {
                    double angle = (2 * Math.PI / 48) * i + (t * 0.1);
                    world.spawnParticle(Particle.DUST,
                            center.getX() + HORIZON_RADIUS * Math.cos(angle),
                            center.getY() + 0.1,
                            center.getZ() + HORIZON_RADIUS * Math.sin(angle),
                            1, new Particle.DustOptions(Color.BLUE, 1.5f));
                }
                // Inner ring at mid height
                for (int i = 0; i < 32; i++) {
                    double angle = (2 * Math.PI / 32) * i - (t * 0.1);
                    world.spawnParticle(Particle.DUST,
                            center.getX() + HORIZON_RADIUS * Math.cos(angle),
                            center.getY() + 3,
                            center.getZ() + HORIZON_RADIUS * Math.sin(angle),
                            1, new Particle.DustOptions(Color.AQUA, 1.2f));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);

        // Freeze enemies INSIDE the zone. Caster flies freely.
        List<UUID> frozenThisTime = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, HORIZON_RADIUS, HORIZON_RADIUS, HORIZON_RADIUS)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            UUID pid = p.getUniqueId();
            frozenInHorizon.add(pid);
            frozenThisTime.add(pid);
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   200, 255, false, false, false));
            // Remove fly – they are FROZEN, cannot fly
            if (p.getAllowFlight() && !p.getGameMode().equals(GameMode.CREATIVE)) {
                p.setFlying(false);
                p.setAllowFlight(false);
            }
            p.sendActionBar(Component.text("🌀 Caught in Event Horizon!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }

        // Caster: flight only WITHIN the zone
        player.setAllowFlight(true);
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false, false));
        player.sendActionBar(Component.text("🌀 EVENT HORIZON – 10s bounded zone!", NamedTextColor.AQUA, TextDecoration.BOLD));
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.4f);

        // Tick: pull caster back into zone if they leave it
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || ++t > 100) { cancel(); return; }
                double dist = player.getLocation().distance(center);
                if (dist > HORIZON_RADIUS) {
                    // Pull back gently to edge
                    Vector pull = center.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.5);
                    player.setVelocity(pull);
                    player.sendActionBar(Component.text("🌀 You cannot leave the Horizon!", NamedTextColor.RED));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);

        new BukkitRunnable() {
            @Override public void run() {
                activeHorizons.remove(casterId);
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
                    player.removePotionEffect(PotionEffectType.SPEED);
                    if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                    }
                    player.sendActionBar(Component.text("🌀 Event Horizon collapsed", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(getPlugin(), 200L);
    }

    private Player getNearestPlayer(Player player, double range) {
        Player nearest = null; double best = Double.MAX_VALUE;
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            double d = player.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }

    private void showRing(Location center, double radius, Particle particle) {
        World world = center.getWorld(); if (world == null) return;
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 15) { cancel(); return; }
                for (int i = 0; i < 24; i++) {
                    double a = (2 * Math.PI / 24) * i;
                    world.spawnParticle(particle,
                            center.getX() + radius * Math.cos(a), center.getY() + 0.05,
                            center.getZ() + radius * Math.sin(a), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
