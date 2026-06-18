package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BossClass extends PlayerClass {

    private final Map<UUID, List<Entity>> minions   = new HashMap<>();
    /** All UUIDs of currently active minion entities */
    private final Set<UUID> minionEntityIds = new HashSet<>();

    @Override public String getName()         { return "Boss"; }
    @Override public String getDescription()  { return "Command the monsters; become the threat"; }
    @Override public String getAbility1Name() { return "Warden Beam"; }
    @Override public String getAbility2Name() { return "Wither Volley"; }
    @Override public String getUltimateName() { return "Boss Raid"; }
    @Override public String getAbility1CooldownKey() { return "WardenBeam"; }
    @Override public String getAbility2CooldownKey() { return "WitherVolley"; }
    @Override public String getUltimateCooldownKey() { return "BossRaid"; }

    // ── Passive: Sovereign – ALL mobs neutral to owner, including minions ──────
    @Override
    public void tickPassive(Player player) {
        UUID ownerId = player.getUniqueId();
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 32, 32, 32)) {
            if (!(e instanceof Mob mob)) continue;
            // Never target the owner
            if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(ownerId)) {
                mob.setTarget(null);
            }
        }
    }

    // ── Ability 1: Warden Beam – 3 hearts + stun ──────────────────────────────
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
                for (Entity e : world.getNearbyEntities(pos, 1.2, 1.2, 1.2)) {
                    if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
                    if (hit.contains(le.getUniqueId())) continue;
                    hit.add(le.getUniqueId());
                    le.damage(6, player); // 3 hearts
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  80, 4));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  80, 3));
                    if (le instanceof Player p2)
                        p2.sendActionBar(Component.text("👁 Warden Beam: Stunned!", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Wither Volley – 3 skulls ───────────────────────────────────
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
            }.runTaskLater(getPlugin(), (long)(i * 5));
        }
    }

    // ── Ultimate: Boss Raid – minions ONLY attack enemy players, never owner ───
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "BossRaid", 5 * MIN)) return;
        startCooldown(player, "BossRaid", 5 * MIN);

        Location loc = player.getLocation();
        World world  = player.getWorld();
        UUID ownerId = player.getUniqueId();

        List<Entity> spawned = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            IronGolem golem = (IronGolem) world.spawnEntity(loc.clone().add(i * 3 - 1.5, 0, 2), EntityType.IRON_GOLEM);
            golem.setPlayerCreated(true);
            golem.setCustomName("§6" + player.getName() + "'s Golem");
            golem.setCustomNameVisible(true);
            golem.setMetadata("boss_minion", new FixedMetadataValue(getPlugin(), ownerId.toString()));
            minionEntityIds.add(golem.getUniqueId());
            spawned.add(golem);
        }

        Wither wither = (Wither) world.spawnEntity(loc.clone().add(0, 4, 0), EntityType.WITHER);
        wither.setMaxHealth(60); wither.setHealth(60);
        wither.setCustomName("§6" + player.getName() + "'s Wither");
        wither.setCustomNameVisible(true);
        wither.setMetadata("boss_minion", new FixedMetadataValue(getPlugin(), ownerId.toString()));
        minionEntityIds.add(wither.getUniqueId());
        spawned.add(wither);

        minions.put(ownerId, spawned);

        // Re-target every 2s – strictly only non-owner players
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 40;
                if (t >= 600) { cancel(); return; }
                List<Entity> list = minions.get(ownerId);
                if (list == null) { cancel(); return; }
                for (Entity e : list) {
                    if (!(e instanceof Mob mob) || !mob.isValid()) continue;
                    mob.setTarget(null); // clear first
                    Player nearest = nearestEnemy(mob, ownerId);
                    if (nearest != null) mob.setTarget(nearest);
                }
            }
        }.runTaskTimer(getPlugin(), 10L, 40L);

        player.sendActionBar(Component.text("👑 BOSS RAID: Minions summoned for 30s!", NamedTextColor.GOLD, TextDecoration.BOLD));
        world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1f, 1f);

        new BukkitRunnable() {
            @Override public void run() {
                List<Entity> list = minions.remove(ownerId);
                if (list != null) list.forEach(e -> { minionEntityIds.remove(e.getUniqueId()); if (e.isValid()) e.remove(); });
                if (player.isOnline()) player.sendActionBar(Component.text("👑 Boss Raid ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 600L);
    }

    private Player nearestEnemy(Mob mob, UUID ownerId) {
        Player nearest = null; double best = Double.MAX_VALUE;
        for (Entity e : mob.getWorld().getNearbyEntities(mob.getLocation(), 30, 30, 30)) {
            if (!(e instanceof Player p) || p.getUniqueId().equals(ownerId)) continue;
            double d = mob.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; nearest = p; }
        }
        return nearest;
    }

    /** Called from GlobalListener to prevent minions targeting owner */
    public boolean isMinionOf(UUID entityId, UUID ownerId) {
        List<Entity> list = minions.get(ownerId);
        if (list == null) return false;
        return list.stream().anyMatch(e -> e.getUniqueId().equals(entityId));
    }

    public boolean isMinionEntity(UUID entityId) { return minionEntityIds.contains(entityId); }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        List<Entity> list = minions.remove(player.getUniqueId());
        if (list != null) list.forEach(e -> { minionEntityIds.remove(e.getUniqueId()); if (e.isValid()) e.remove(); });
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
