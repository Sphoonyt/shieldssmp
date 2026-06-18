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
    private final Map<UUID, Location> activeHorizons = new HashMap<>();
    private final Set<UUID> frozenInHorizon = new HashSet<>();

    @Override public String getName()         { return "Gravity"; }
    @Override public String getDescription()  { return "Bend the laws of physics"; }
    @Override public String getAbility1Name() { return "Vector Shift"; }
    @Override public String getAbility2Name() { return "Orbital Slam"; }
    @Override public String getUltimateName() { return "Event Horizon"; }
    @Override public String getAbility1CooldownKey() { return "VectorShift"; }
    @Override public String getAbility2CooldownKey() { return "OrbitalSlam"; }
    @Override public String getUltimateCooldownKey() { return "EventHorizon"; }

    @Override
    public void tickPassive(Player player) {
        if (player.isSneaking())
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 5, false, false, false));
        else
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    // ── Ability 1: Vector Shift – launch nearest player in a CHOSEN direction ──
    // Direction cycles on use: each press picks the next direction in order
    private final Map<UUID, Integer> dirIndex = new HashMap<>();

    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "VectorShift", 2 * MIN)) return;

        Player target = getNearestPlayer(player, 20);
        if (target == null) {
            player.sendActionBar(Component.text("⚡ No player within 20 blocks!", NamedTextColor.RED));
            return;
        }
        startCooldown(player, "VectorShift", 2 * MIN);

        // Direction is determined by which way the CASTER is looking (yaw-based)
        float yaw = player.getLocation().getYaw();
        // Normalise yaw to 0-360
        yaw = ((yaw % 360) + 360) % 360;

        // Based on caster facing: N/S/E/W determines left/right/forward/back on target
        String dir;
        Vector v;
        // Use attacker look direction to determine push direction on the target
        Vector look = player.getLocation().getDirection().setY(0).normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        // Shift+A1: launch UP. No sneak: launch in caster's forward direction.
        if (player.isSneaking()) {
            dir = "Up";
            v = new Vector(0, 5, 0);
        } else {
            // Always push target in the direction the caster is facing (predictable)
            dir = "Forward";
            v = look.clone().multiply(4.5).setY(0.5);
        }

        target.setVelocity(v);
        showRing(target.getLocation(), 1.5, Particle.CRIT);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        player.sendActionBar(Component.text("🌀 Vector Shift: launched " + target.getName() + " " + dir + "!", NamedTextColor.AQUA, TextDecoration.BOLD));
        target.sendActionBar(Component.text("🌀 Vector Shifted " + dir + "!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
    }

    // ── Ability 2: Orbital Slam ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "OrbitalSlam", 2 * MIN)) return;

        Player target = getNearestPlayer(player, 25);
        if (target == null) { player.sendActionBar(Component.text("⚡ No player within 25 blocks!", NamedTextColor.RED)); return; }

        startCooldown(player, "OrbitalSlam", 2 * MIN);
        showRing(target.getLocation(), 5, Particle.CRIT);
        target.setVelocity(new Vector(0, 5, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.5f);
        player.sendActionBar(Component.text("☄ Orbital Slam: " + target.getName() + " launched!", NamedTextColor.RED, TextDecoration.BOLD));
        target.showTitle(Title.title(
                Component.text("☄ ORBITAL SLAM!", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Use abilities to survive!", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(300))));

        new BukkitRunnable() {
            @Override public void run() {
                if (!target.isOnline()) return;
                target.setVelocity(new Vector(0, -7, 0));
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
                target.sendActionBar(Component.text("☄ SLAMMING DOWN!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            }
        }.runTaskLater(getPlugin(), 30L);
    }

    // ── Ultimate: Event Horizon – bounded blue zone, enemies frozen inside ────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "EventHorizon", 5 * MIN)) return;
        startCooldown(player, "EventHorizon", 5 * MIN);

        Location center = player.getLocation().clone();
        World world = player.getWorld();
        UUID casterId = player.getUniqueId();
        activeHorizons.put(casterId, center);

        // Blue particle boundary
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 50 || !activeHorizons.containsKey(casterId)) { cancel(); return; }
                for (int i = 0; i < 48; i++) {
                    double a = (2 * Math.PI / 48) * i + t * 0.1;
                    world.spawnParticle(Particle.DUST,
                            center.getX() + HORIZON_RADIUS * Math.cos(a), center.getY() + 0.1,
                            center.getZ() + HORIZON_RADIUS * Math.sin(a),
                            1, new Particle.DustOptions(Color.BLUE, 1.5f));
                }
                for (int i = 0; i < 32; i++) {
                    double a = (2 * Math.PI / 32) * i - t * 0.1;
                    world.spawnParticle(Particle.DUST,
                            center.getX() + HORIZON_RADIUS * Math.cos(a), center.getY() + 3,
                            center.getZ() + HORIZON_RADIUS * Math.sin(a),
                            1, new Particle.DustOptions(Color.AQUA, 1.2f));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);

        // Freeze enemies inside
        List<UUID> frozenList = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, HORIZON_RADIUS, HORIZON_RADIUS, HORIZON_RADIUS)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            UUID pid = p.getUniqueId();
            frozenInHorizon.add(pid); frozenList.add(pid);
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   200, 255, false, false, false));
            if (!p.getGameMode().equals(GameMode.CREATIVE)) { p.setFlying(false); p.setAllowFlight(false); }
            p.sendActionBar(Component.text("🌀 Caught in Event Horizon!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }

        // Caster flies inside zone only
        player.setAllowFlight(true); player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false, false));
        player.sendActionBar(Component.text("🌀 EVENT HORIZON – bounded zone! 10s!", NamedTextColor.AQUA, TextDecoration.BOLD));
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.4f);

        // Pull caster back if they leave
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || ++t > 100) { cancel(); return; }
                if (player.getLocation().distance(center) > HORIZON_RADIUS) {
                    player.setVelocity(center.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.5));
                    player.sendActionBar(Component.text("🌀 Cannot leave the Horizon!", NamedTextColor.RED));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);

        new BukkitRunnable() {
            @Override public void run() {
                activeHorizons.remove(casterId);
                frozenList.forEach(pid -> {
                    frozenInHorizon.remove(pid);
                    Player p = Bukkit.getPlayer(pid);
                    if (p != null) { p.removePotionEffect(PotionEffectType.LEVITATION); p.removePotionEffect(PotionEffectType.SLOWNESS); }
                });
                if (player.isOnline()) {
                    player.removePotionEffect(PotionEffectType.SPEED);
                    if (!player.getGameMode().equals(GameMode.CREATIVE)) { player.setAllowFlight(false); player.setFlying(false); }
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
                    world.spawnParticle(particle, center.getX() + radius * Math.cos(a),
                            center.getY() + 0.05, center.getZ() + radius * Math.sin(a), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
