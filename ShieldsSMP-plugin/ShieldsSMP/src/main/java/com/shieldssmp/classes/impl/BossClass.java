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

import java.util.*;

public class BossClass extends PlayerClass {

    private final Map<UUID, List<Entity>> minions = new HashMap<>();

    @Override public String getName()         { return "Boss"; }
    @Override public String getDescription()  { return "Command the monsters; become the threat"; }
    @Override public String getAbility1Name() { return "Warden Beam"; }
    @Override public String getAbility2Name() { return "Wither Volley"; }
    @Override public String getUltimateName() { return "Boss Raid"; }

    // ── Passive: Sovereign – mobs neutral ─────────────────────────────────────
    @Override
    public void tickPassive(Player player) {
        player.getWorld().getNearbyEntities(player.getLocation(), 20, 20, 20).forEach(e -> {
            if (e instanceof Mob mob && mob.getTarget() != null && mob.getTarget().equals(player))
                mob.setTarget(null);
        });
    }

    // ── Ability 1: Warden Beam ─────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "WardenBeam", 4 * MIN)) return;
        startCooldown(player, "WardenBeam", 4 * MIN);

        Location eye = player.getEyeLocation();
        var dir = eye.getDirection().normalize();
        World world = player.getWorld();

        player.sendActionBar(Component.text("👁 Warden Beam!", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.2f);

        new BukkitRunnable() {
            final Location pos = eye.clone();
            int steps = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override public void run() {
                if (++steps > 40) { cancel(); return; }
                pos.add(dir);
                world.spawnParticle(Particle.SONIC_BOOM, pos, 1, 0, 0, 0, 0);

                for (Entity e : world.getNearbyEntities(pos, 1, 1, 1)) {
                    if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
                    UUID eid = le.getUniqueId();
                    if (hit.contains(eid)) continue;
                    hit.add(eid);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  80, 4));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  80, 2));
                    if (le instanceof Player p2)
                        p2.sendActionBar(Component.text("👁 Warden Beam: Stunned!", NamedTextColor.DARK_GREEN));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Wither Volley ───────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "WitherVolley", 1 * MIN)) return;
        startCooldown(player, "WitherVolley", 1 * MIN);

        World world = player.getWorld();
        player.sendActionBar(Component.text("💀 Wither Volley!", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        world.playSound(player.getEyeLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 1f);

        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline()) return;
                    Location origin = player.getEyeLocation();
                    WitherSkull skull = world.spawn(origin, WitherSkull.class);
                    skull.setShooter(player);
                    skull.setVelocity(origin.getDirection().normalize().multiply(1.5));
                    skull.setCharged(false);
                }
            }.runTaskLater(getPlugin(), (long)(i * 6));
        }
    }

    // ── Ultimate: Boss Raid – minions attack enemies only ─────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "BossRaid", 5 * MIN)) return;
        startCooldown(player, "BossRaid", 5 * MIN);

        Location loc = player.getLocation();
        World world = player.getWorld();
        UUID id = player.getUniqueId();

        List<Entity> spawned = new ArrayList<>();

        // 2 Iron Golems – target nearest non-Boss-owner player
        for (int i = 0; i < 2; i++) {
            IronGolem golem = (IronGolem) world.spawnEntity(loc.clone().add(i * 3 - 1, 0, 2), EntityType.IRON_GOLEM);
            golem.setCustomName(player.getName() + "'s Golem");
            golem.setCustomNameVisible(true);
            spawned.add(golem);

            // Assign target immediately
            setMinionTarget(golem, player);
        }

        // 1 Mini-Wither
        Wither wither = (Wither) world.spawnEntity(loc.clone().add(0, 3, 0), EntityType.WITHER);
        wither.setMaxHealth(60);
        wither.setHealth(60);
        wither.setCustomName(player.getName() + "'s Wither");
        wither.setCustomNameVisible(true);
        spawned.add(wither);
        setMinionTarget(wither, player);

        minions.put(id, spawned);

        // Re-target every 2 seconds to keep minions on enemies
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 40;
                if (ticks >= 600) { cancel(); return; }
                List<Entity> list = minions.get(id);
                if (list == null) { cancel(); return; }
                for (Entity e : list) {
                    if (!(e instanceof Mob mob) || !mob.isValid()) continue;
                    setMinionTarget(mob, player);
                }
            }
        }.runTaskTimer(getPlugin(), 40L, 40L);

        player.sendActionBar(Component.text("👑 BOSS RAID: Minions summoned for 30s!", NamedTextColor.GOLD, TextDecoration.BOLD));
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 1f);

        new BukkitRunnable() {
            @Override public void run() {
                List<Entity> list = minions.remove(id);
                if (list != null) list.forEach(e -> { if (e.isValid()) e.remove(); });
                if (player.isOnline())
                    player.sendActionBar(Component.text("👑 Boss Raid ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 600L);
    }

    /** Point a mob at the nearest player that is NOT the owner */
    private void setMinionTarget(Mob mob, Player owner) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : mob.getWorld().getNearbyEntities(mob.getLocation(), 30, 30, 30)) {
            if (!(e instanceof Player p) || p.equals(owner)) continue;
            double dist = mob.getLocation().distanceSquared(p.getLocation());
            if (dist < best) { best = dist; nearest = p; }
        }
        if (nearest != null) mob.setTarget(nearest);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        List<Entity> list = minions.remove(player.getUniqueId());
        if (list != null) list.forEach(e -> { if (e.isValid()) e.remove(); });
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
