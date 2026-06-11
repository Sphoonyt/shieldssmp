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

    @Override public String getAbility1CooldownKey() { return "WardenBeam"; }
    @Override public String getAbility2CooldownKey() { return "WitherVolley"; }
    @Override public String getUltimateCooldownKey() { return "BossRaid"; }

    // ── Passive: Sovereign – mobs neutral ─────────────────────────────────────
    @Override
    public void tickPassive(Player player) {
        player.getWorld().getNearbyEntities(player.getLocation(), 24, 24, 24).forEach(e -> {
            if (e instanceof Mob mob && !(e instanceof Player) && mob.getTarget() != null
                    && mob.getTarget().equals(player)) {
                mob.setTarget(null);
            }
        });
    }

    // ── Ability 1: Warden Beam – damage + stun ────────────────────────────────
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
                    UUID eid = le.getUniqueId();
                    if (hit.contains(eid)) continue;
                    hit.add(eid);

                    // Direct damage (3 hearts) + stun effects
                    le.damage(6, player); // 3 hearts
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  80, 4));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  80, 3));

                    if (le instanceof Player p2)
                        p2.sendActionBar(Component.text("👁 Warden Beam: 3 hearts + Stunned!", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Wither Volley – 3 skulls that deal damage ─────────────────
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
                    // Slight spread
                    double spread = (Math.random() - 0.5) * 0.1;
                    WitherSkull skull = world.spawn(origin, WitherSkull.class);
                    skull.setShooter(player);
                    var v = origin.getDirection().normalize().add(new org.bukkit.util.Vector(spread, spread, spread)).multiply(1.5);
                    skull.setVelocity(v);
                    skull.setCharged(false);
                }
            }.runTaskLater(getPlugin(), (long)(i * 5));
        }
    }

    // ── Ultimate: Boss Raid – minions ONLY attack enemy players ───────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "BossRaid", 5 * MIN)) return;
        startCooldown(player, "BossRaid", 5 * MIN);

        Location loc = player.getLocation();
        World world = player.getWorld();
        UUID id = player.getUniqueId();

        List<Entity> spawned = new ArrayList<>();

        // 2 Iron Golems
        for (int i = 0; i < 2; i++) {
            IronGolem golem = (IronGolem) world.spawnEntity(loc.clone().add(i * 3 - 1.5, 0, 2), EntityType.IRON_GOLEM);
            golem.setCustomName("§6" + player.getName() + "'s Golem");
            golem.setCustomNameVisible(true);
            golem.setPlayerCreated(true); // Treat as player-created – won't attack owner
            setMinionTarget(golem, player);
            spawned.add(golem);
        }

        // 1 Wither with very low health (visual size proxy)
        Wither wither = (Wither) world.spawnEntity(loc.clone().add(0, 3, 0), EntityType.WITHER);
        wither.setMaxHealth(60);
        wither.setHealth(60);
        wither.setCustomName("§6" + player.getName() + "'s Wither");
        wither.setCustomNameVisible(true);
        // Prevent wither from targeting owner or other minions
        setMinionTarget(wither, player);
        spawned.add(wither);

        minions.put(id, spawned);

        // Re-target every 2s to keep on enemies ONLY
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 40;
                if (ticks >= 600) { cancel(); return; }
                List<Entity> list = minions.get(id);
                if (list == null) { cancel(); return; }
                for (Entity e : list) {
                    if (!(e instanceof Mob mob) || !mob.isValid()) continue;
                    // Only target enemy players (not owner, not other minions)
                    setMinionTarget(mob, player);
                }
            }
        }.runTaskTimer(getPlugin(), 40L, 40L);

        player.sendActionBar(Component.text("👑 BOSS RAID: 2 Golems + Wither summoned for 30s!", NamedTextColor.GOLD, TextDecoration.BOLD));
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

    /** Target nearest player that is NOT the owner and NOT another minion */
    private void setMinionTarget(Mob mob, Player owner) {
        UUID ownerId = owner.getUniqueId();
        List<UUID> minionIds = new ArrayList<>();
        List<Entity> myMinions = minions.get(ownerId);
        if (myMinions != null) myMinions.forEach(e -> minionIds.add(e.getUniqueId()));

        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : mob.getWorld().getNearbyEntities(mob.getLocation(), 30, 30, 30)) {
            if (!(e instanceof Player p)) continue;
            if (p.getUniqueId().equals(ownerId)) continue;
            double d = mob.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; nearest = p; }
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
