package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TerroristClass extends PlayerClass {

    @Override public String getName()         { return "Terrorist"; }
    @Override public String getDescription()  { return "Controlled chaos and superior firepower"; }
    @Override public String getAbility1Name() { return "Carpet Bomb"; }
    @Override public String getAbility2Name() { return "Void Breach"; }
    @Override public String getUltimateName() { return "Tactical Nuke"; }

    @Override public String getAbility1CooldownKey() { return "CarpetBomb"; }
    @Override public String getAbility2CooldownKey() { return "VoidBreach"; }
    @Override public String getUltimateCooldownKey() { return "TacticalNuke"; }

    public boolean hasBlastProof() { return true; }

    // ── Ability 1: Carpet Bomb – 24 TNT, close spread ────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "CarpetBomb", (long)(1.5 * MIN))) return;
        startCooldown(player, "CarpetBomb", (long)(1.5 * MIN));

        Location loc = player.getLocation();
        World world = player.getWorld();

        // 3 rings of 8 = 24 TNT, short fuse, tight spread close to player
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(i * 45.0 + ring * 15.0);
                double speed = 0.6 + ring * 0.3; // short range
                double yBoost = 0.2 + ring * 0.25;

                TNTPrimed tnt = world.spawn(loc.clone().add(0, 0.5, 0), TNTPrimed.class);
                tnt.setFuseTicks(20 + ring * 8); // short fuse ~1-1.5s
                tnt.setVelocity(new Vector(Math.cos(angle) * speed, yBoost, Math.sin(angle) * speed));
                tnt.setMetadata("carpet_bomb_owner", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
            }
        }

        player.sendActionBar(Component.text("💣 CARPET BOMB – 24 TNT!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        world.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.5f, 0.8f);
    }

    // ── Ability 2: Void Breach – wide beam, big destruction ──────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBreach", 2 * MIN)) return;
        startCooldown(player, "VoidBreach", 2 * MIN);

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        World world = player.getWorld();

        player.sendActionBar(Component.text("💥 Void Breach fired!", NamedTextColor.GOLD, TextDecoration.BOLD));
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.8f);

        new BukkitRunnable() {
            final Location pos = start.clone();
            int steps = 0;
            final Set<UUID> hitPlayers = new HashSet<>();

            @Override public void run() {
                if (++steps > 60) { cancel(); return; }
                pos.add(dir);

                // Thick beam – destroy 3x3 cross of blocks
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        Location blockLoc = pos.clone().add(dx, dy, 0);
                        Material mat = blockLoc.getBlock().getType();
                        if (mat.isSolid() && mat != Material.BEDROCK && mat != Material.BARRIER
                                && mat != Material.OBSIDIAN) {
                            blockLoc.getBlock().setType(Material.AIR);
                        }
                        // Side particles
                        world.spawnParticle(Particle.EXPLOSION, blockLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }
                // Main beam particles (every step)
                world.spawnParticle(Particle.LARGE_SMOKE, pos, 3, 0.2, 0.2, 0.2, 0);
                world.spawnParticle(Particle.FLAME, pos, 2, 0.1, 0.1, 0.1, 0.05);

                // Damage players in 2-block radius
                for (Entity e : world.getNearbyEntities(pos, 2, 2, 2)) {
                    if (!(e instanceof Player p) || p.equals(player) || hitPlayers.contains(p.getUniqueId())) continue;
                    hitPlayers.add(p.getUniqueId());
                    p.damage(4, player); // 2 hearts
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ultimate: Tactical Nuke – 20 TNT + true damage ────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "TacticalNuke", 5 * MIN)) return;
        startCooldown(player, "TacticalNuke", 5 * MIN);

        Location target = player.getTargetBlock(null, 100).getLocation().add(0.5, 0, 0.5);
        World world = player.getWorld();

        player.sendActionBar(Component.text("☢ NUKE in 5s!", NamedTextColor.RED, TextDecoration.BOLD));

        new BukkitRunnable() {
            int countdown = 5;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (countdown > 0) {
                    world.playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 1.5f);
                    world.spawnParticle(Particle.LARGE_SMOKE, target.clone().add(0, countdown * 4, 0), 6, 2, 1, 2);
                    for (Player p : world.getPlayers())
                        if (p.getLocation().distance(target) < 80)
                            p.sendActionBar(Component.text("☢ NUKE in " + countdown + "s!", NamedTextColor.RED, TextDecoration.BOLD));
                    countdown--;
                } else {
                    cancel();

                    // Massive FX
                    world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.3f);
                    world.playSound(target, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.3f);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 25, 8, 8, 8);
                    world.spawnParticle(Particle.LARGE_SMOKE, target, 300, 12, 6, 12);

                    // Distance-based damage: 8 hearts (16 HP) at point-blank, falls off with distance
                    double maxRadius = 20.0;
                    double maxDamage = 16.0; // 8 hearts at 0 distance
                    for (Entity e : world.getNearbyEntities(target, maxRadius, maxRadius, maxRadius)) {
                        if (!(e instanceof Player p) || p.equals(player)) continue;
                        double dist = p.getLocation().distance(target);
                        // Linear falloff: full damage at 0, zero at maxRadius
                        double fraction = Math.max(0, 1.0 - (dist / maxRadius));
                        double dmg = maxDamage * fraction;
                        if (dmg > 0) {
                            p.setHealth(Math.max(0, p.getHealth() - dmg));
                            p.sendActionBar(Component.text(
                                String.format("☢ NUKED! %.1f hearts damage!", dmg / 2),
                                NamedTextColor.DARK_RED, TextDecoration.BOLD));
                        }
                    }

                    // Drop physical TNT blocks + primed TNT spread over wide area
                    // Physical TNT items (dropped, can be picked up)
                    for (int i = 0; i < 16; i++) {
                        double ox = (Math.random() - 0.5) * 20;
                        double oz = (Math.random() - 0.5) * 20;
                        Location dropLoc = target.clone().add(ox, 8, oz);
                        world.dropItem(dropLoc, new org.bukkit.inventory.ItemStack(Material.TNT));
                    }

                    // Primed TNT for explosion chaining
                    for (int i = 0; i < 20; i++) {
                        double ox = (Math.random() - 0.5) * 26;
                        double oz = (Math.random() - 0.5) * 26;
                        double oy = Math.random() * 8 + 1;
                        TNTPrimed tnt = world.spawn(target.clone().add(ox, oy, oz), TNTPrimed.class);
                        tnt.setFuseTicks(3 + i * 2);
                        tnt.setMetadata("nuke_owner", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 20L);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
