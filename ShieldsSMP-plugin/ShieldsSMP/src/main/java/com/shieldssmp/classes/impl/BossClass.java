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

public class BossClass extends PlayerClass {

    private final Map<UUID, List<Entity>> minions = new HashMap<>();

    @Override public String getName()         { return "Boss"; }
    @Override public String getDescription()  { return "Command the monsters; become the threat"; }
    @Override public String getAbility1Name() { return "Warden Beam"; }
    @Override public String getAbility2Name() { return "Wither Volley"; }
    @Override public String getUltimateName() { return "Boss Raid"; }

    // ── Passive: Sovereign – mobs neutral ────────────────────────────────────
    // Enforced in combat listener; tickPassive ensures no hostile AI targets
    @Override
    public void tickPassive(Player player) {
        // Cancel any aggressive mob targeting via nearby mob entity goals
        player.getWorld().getNearbyEntities(player.getLocation(), 20, 20, 20).forEach(e -> {
            if (e instanceof Mob mob) {
                if (mob.getTarget() != null && mob.getTarget().equals(player)) {
                    mob.setTarget(null);
                }
            }
        });
    }

    // ── Ability 1: Warden Beam ────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "WardenBeam", 4 * MIN)) return;
        startCooldown(player, "WardenBeam", 4 * MIN);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        player.sendActionBar(Component.text("👁 Warden Beam!", NamedTextColor.DARK_GREEN, TextDecoration.BOLD));
        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.2f);

        new BukkitRunnable() {
            Location pos = eye.clone();
            int steps = 0;
            final Set<UUID> hit = new HashSet<>();

            @Override public void run() {
                steps++;
                if (steps > 40) { cancel(); return; }
                pos.add(dir);
                world.spawnParticle(Particle.SONIC_BOOM, pos, 1, 0, 0, 0, 0);

                for (Entity e : world.getNearbyEntities(pos, 1, 1, 1)) {
                    if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
                    if (le instanceof Player p && hit.contains(p.getUniqueId())) continue;
                    if (e instanceof Player p) hit.add(p.getUniqueId());

                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  80, 4));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,  80, 2)); // stun proxy
                    if (le instanceof Player p2)
                        p2.sendActionBar(Component.text("👁 Warden Beam: Stunned!", NamedTextColor.DARK_GREEN));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Wither Volley ──────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "WitherVolley", 1 * MIN)) return;
        startCooldown(player, "WitherVolley", 1 * MIN);

        Location eye = player.getEyeLocation();
        World world = player.getWorld();

        player.sendActionBar(Component.text("💀 Wither Volley!", NamedTextColor.DARK_GRAY, TextDecoration.BOLD));
        world.playSound(eye, Sound.ENTITY_WITHER_SHOOT, 1f, 1f);

        for (int i = 0; i < 3; i++) {
            final int idx = i;
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

    // ── Ultimate: Boss Raid ───────────────────────────────────────────────────
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
            IronGolem golem = (IronGolem) world.spawnEntity(loc.clone().add(i * 2 - 1, 0, 2), EntityType.IRON_GOLEM);
            golem.setCustomName(player.getName() + "'s Golem");
            golem.setCustomNameVisible(true);
            spawned.add(golem);
        }

        // 1 mini-Wither (full wither at low health to appear smaller)
        Wither wither = (Wither) world.spawnEntity(loc.clone().add(0, 3, 0), EntityType.WITHER);
        wither.setMaxHealth(40);
        wither.setHealth(40);
        wither.setCustomName(player.getName() + "'s Wither");
        wither.setCustomNameVisible(true);
        spawned.add(wither);

        minions.put(id, spawned);

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

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        List<Entity> list = minions.remove(player.getUniqueId());
        if (list != null) list.forEach(e -> { if (e.isValid()) e.remove(); });
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return ShieldsSMP.getInstance();
    }
}
