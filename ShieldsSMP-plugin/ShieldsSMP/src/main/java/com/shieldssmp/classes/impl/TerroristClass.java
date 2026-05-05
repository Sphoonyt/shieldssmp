package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TerroristClass extends PlayerClass {

    @Override public String getName()         { return "Terrorist"; }
    @Override public String getDescription()  { return "Controlled chaos and superior firepower"; }
    @Override public String getAbility1Name() { return "Carpet Bomb"; }
    @Override public String getAbility2Name() { return "Void Breach"; }
    @Override public String getUltimateName() { return "Tactical Nuke"; }

    // ── Passive: Blast Proof – immune to explosions ────────────────────────────
    // Handled in the main listener; stored for quick lookup
    public boolean hasBlastProof() { return true; }

    @Override
    public void onTakeDamage(Player victim, Entity attacker, double damage) {
        // Actual cancellation done in EventListener checking the class type
    }

    // ── Ability 1: Carpet Bomb ────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "CarpetBomb", (long)(1.5 * MIN))) return;
        startCooldown(player, "CarpetBomb", (long)(1.5 * MIN));

        Location loc = player.getLocation();
        World world = player.getWorld();

        double[] angles = {0, 45, 90, 135, 180, 225, 270, 315};
        for (double angle : angles) {
            double rad = Math.toRadians(angle);
            Vector v = new Vector(Math.cos(rad) * 1.2, 0.5, Math.sin(rad) * 1.2);

            TNTPrimed tnt = world.spawn(loc.clone().add(0, 1, 0), TNTPrimed.class);
            tnt.setFuseTicks(30);
            tnt.setVelocity(v);
            tnt.setIsIncendiary(false);
            // Mark as friendly (handled in explosion listener)
            tnt.setMetadata("carpet_bomb_owner", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
        }

        player.sendActionBar(Component.text("💣 Carpet Bomb launched!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        world.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1f, 1f);
    }

    // ── Ability 2: Void Breach ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBreach", 2 * MIN)) return;
        startCooldown(player, "VoidBreach", 2 * MIN);

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        World world = player.getWorld();

        player.sendActionBar(Component.text("💥 Void Breach fired!", NamedTextColor.ORANGE, TextDecoration.BOLD));
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.8f);

        new BukkitRunnable() {
            Location pos = start.clone();
            int steps = 0;

            @Override public void run() {
                steps++;
                if (steps > 60) { cancel(); return; }

                pos.add(dir);
                world.spawnParticle(Particle.EXPLOSION, pos, 3, 0.2, 0.2, 0.2, 0);

                // Delete non-player blocks
                Material mat = pos.getBlock().getType();
                if (mat.isSolid() && mat != Material.BEDROCK && mat != Material.BARRIER) {
                    pos.getBlock().setType(Material.AIR);
                }

                // Damage players
                for (Entity e : world.getNearbyEntities(pos, 1.5, 1.5, 1.5)) {
                    if (e instanceof Player p && !p.equals(player)) {
                        p.damage(4, player); // 2 hearts
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ultimate: Tactical Nuke ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "TacticalNuke", 5 * MIN)) return;
        startCooldown(player, "TacticalNuke", 5 * MIN);

        Location target = player.getTargetBlock(null, 100).getLocation().add(0.5, 0, 0.5);
        World world = player.getWorld();

        player.sendActionBar(Component.text("☢ NUKE designated! Detonating in 5s!", NamedTextColor.RED, TextDecoration.BOLD));

        // Countdown beeps
        new BukkitRunnable() {
            int countdown = 5;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (countdown > 0) {
                    world.playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 1.5f);
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distance(target) < 50) {
                            p.sendActionBar(Component.text("☢ NUKE in " + countdown + "s!", NamedTextColor.RED, TextDecoration.BOLD));
                        }
                    }
                    world.spawnParticle(Particle.LARGE_SMOKE, target.clone().add(0, countdown * 5, 0), 5, 2, 1, 2);
                    countdown--;
                } else {
                    // DETONATE
                    cancel();
                    world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.3f);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 20, 5, 5, 5);
                    world.spawnParticle(Particle.LARGE_SMOKE, target, 200, 10, 5, 10);

                    double radius = 10;
                    for (Entity e : world.getNearbyEntities(target, radius, radius, radius)) {
                        if (!(e instanceof Player p) || p.equals(player)) continue;
                        // 50 hearts true damage (bypasses armor via setHealth)
                        double newHp = p.getHealth() - 100.0;
                        if (newHp <= 0) {
                            p.setHealth(0);
                        } else {
                            p.setHealth(newHp);
                        }
                        p.sendActionBar(Component.text("☢ You were nuked!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 20L);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return ShieldsSMP.getInstance();
    }
}
