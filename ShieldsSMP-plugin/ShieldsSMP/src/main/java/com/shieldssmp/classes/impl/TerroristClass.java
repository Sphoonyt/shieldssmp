package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class TerroristClass extends PlayerClass {

    @Override public String getName()         { return "Combustion"; }
    @Override public String getDescription()  { return "Explosive devastation and collateral damage"; }
    @Override public String getAbility1Name() { return "Carpet Bomb"; }
    @Override public String getAbility2Name() { return "Void Breach"; }
    @Override public String getUltimateName() { return "Tactical Nuke"; }
    @Override public String getAbility1CooldownKey() { return "CarpetBomb"; }
    @Override public String getAbility2CooldownKey() { return "VoidBreach"; }
    @Override public String getUltimateCooldownKey() { return "TacticalNuke"; }

    public boolean hasBlastProof() { return true; }

    // ── Ability 1: Carpet Bomb – 24 TNT explode IMMEDIATELY around the player ─
    // Short fuse (3-6 ticks) so TNT explodes right where it lands near enemies
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "CarpetBomb", (long)(1.5 * MIN))) return;
        startCooldown(player, "CarpetBomb", (long)(1.5 * MIN));

        Location loc = player.getLocation();
        World world = player.getWorld();
        String ownerStr = player.getUniqueId().toString();

        // 3 rings of 8, very tight radius so they land near enemies
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.toRadians(i * 45.0 + ring * 15.0);
                double radius = 1.5 + ring * 1.0; // 1.5, 2.5, 3.5 blocks out
                double ox = Math.cos(angle) * radius;
                double oz = Math.sin(angle) * radius;

                TNTPrimed tnt = world.spawn(loc.clone().add(ox, 1, oz), TNTPrimed.class);
                tnt.setFuseTicks(3 + ring * 2); // very short: 3, 5, 7 ticks
                tnt.setVelocity(new Vector(0, 0.15, 0)); // tiny upward bounce
                tnt.setMetadata("carpet_bomb_owner", new FixedMetadataValue(getPlugin(), ownerStr));
            }
        }

        player.sendActionBar(Component.text("💣 CARPET BOMB – 24 TNT!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        world.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.5f, 0.8f);
    }

    // ── Ability 2: Void Breach – wide beam, blocks regenerate after 1 minute ──
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBreach", 2 * MIN)) return;
        startCooldown(player, "VoidBreach", 2 * MIN);

        Location start = player.getEyeLocation();
        Vector forward = start.getDirection().normalize();
        World world = player.getWorld();

        player.sendActionBar(Component.text("💥 Void Breach – blocks restore in 1min!", NamedTextColor.GOLD, TextDecoration.BOLD));
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.8f);

        // Track destroyed blocks so we can restore them
        final java.util.LinkedHashMap<Location, Material> destroyed = new java.util.LinkedHashMap<>();

        new BukkitRunnable() {
            final Location pos = start.clone();
            int steps = 0;
            final Set<UUID> hitPlayers = new HashSet<>();

            @Override public void run() {
                if (++steps > 50) {
                    cancel();
                    // Schedule block restoration after 1 minute
                    new BukkitRunnable() {
                        @Override public void run() {
                            // Restore in reverse order so overhanging blocks don't fall
                            var entries = new java.util.ArrayList<>(destroyed.entrySet());
                            java.util.Collections.reverse(entries);
                            for (var entry : entries) {
                                Location loc = entry.getKey();
                                // Only restore if still air (don't overwrite player-placed blocks)
                                if (loc.getBlock().getType().isAir()) {
                                    loc.getBlock().setType(entry.getValue());
                                }
                            }
                        }
                    }.runTaskLater(getPlugin(), 60 * 20L); // 1 minute = 1200 ticks
                    return;
                }
                pos.add(forward);

                // Wide destruction: 5x5 cross
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        Location blockLoc = pos.clone().add(dx, dy, 0);
                        Material mat = blockLoc.getBlock().getType();
                        if (mat.isSolid() && mat != Material.BEDROCK && mat != Material.BARRIER && mat != Material.OBSIDIAN) {
                            destroyed.put(blockLoc.clone(), mat); // save for restore
                            blockLoc.getBlock().setType(Material.AIR);
                        }
                        world.spawnParticle(Particle.EXPLOSION, blockLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }
                world.spawnParticle(Particle.LARGE_SMOKE, pos, 5, 0.3, 0.3, 0.3, 0.02);
                world.spawnParticle(Particle.FLAME,       pos, 3, 0.2, 0.2, 0.2, 0.05);

                for (Entity e : world.getNearbyEntities(pos, 3, 3, 3)) {
                    if (!(e instanceof Player p) || p.equals(player) || hitPlayers.contains(p.getUniqueId())) continue;
                    hitPlayers.add(p.getUniqueId());
                    p.damage(6, player);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ultimate: Tactical Nuke – ORBITAL: 40 TNT rain from above ─────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "TacticalNuke", 5 * MIN)) return;
        startCooldown(player, "TacticalNuke", 5 * MIN);

        Location target = player.getTargetBlock(null, 100).getLocation().add(0.5, 0, 0.5);
        World world = player.getWorld();
        String ownerStr = player.getUniqueId().toString();

        player.sendActionBar(Component.text("☢ ORBITAL NUKE in 5s!", NamedTextColor.RED, TextDecoration.BOLD));

        new BukkitRunnable() {
            int countdown = 5;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (countdown > 0) {
                    world.playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 1.5f);
                    world.spawnParticle(Particle.LARGE_SMOKE, target.clone().add(0, countdown * 5, 0), 8, 3, 1, 3);
                    for (Player p : world.getPlayers())
                        if (p.getLocation().distance(target) < 80)
                            p.sendActionBar(Component.text("☢ ORBITAL NUKE in " + countdown + "s!", NamedTextColor.RED, TextDecoration.BOLD));
                    countdown--;
                } else {
                    cancel();

                    // FX
                    world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.3f);
                    world.playSound(target, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.3f);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 30, 10, 10, 10);
                    world.spawnParticle(Particle.LARGE_SMOKE, target, 400, 15, 6, 15);

                    // Distance-based true damage (8 hearts at centre)
                    for (Entity e : world.getNearbyEntities(target, 20, 20, 20)) {
                        if (!(e instanceof Player p) || p.equals(player)) continue;
                        double dist = p.getLocation().distance(target);
                        double dmg  = Math.max(0, 16.0 * (1.0 - dist / 20.0));
                        if (dmg > 0) {
                            p.setHealth(Math.max(0, p.getHealth() - dmg));
                            p.sendActionBar(Component.text(String.format("☢ ORBITAL NUKE: %.1f hearts!", dmg / 2), NamedTextColor.DARK_RED, TextDecoration.BOLD));
                        }
                    }

                    // 40 TNT raining from the sky in expanding rings
                    double maxR = 30;
                    for (int i = 0; i < 40; i++) {
                        double angle  = (2 * Math.PI / 40) * i;
                        double r      = Math.random() * maxR;
                        double ox     = Math.cos(angle) * r;
                        double oz     = Math.sin(angle) * r;
                        double height = 25 + Math.random() * 15; // drops from 25-40 blocks up

                        TNTPrimed tnt = world.spawn(target.clone().add(ox, height, oz), TNTPrimed.class);
                        tnt.setFuseTicks((int)(height * 1.5)); // fuse timed to land timing
                        tnt.setVelocity(new Vector(0, -1.5, 0)); // fall downward fast
                        tnt.setMetadata("nuke_owner", new FixedMetadataValue(getPlugin(), ownerStr));
                    }

                    // Physical TNT drops too
                    for (int i = 0; i < 16; i++) {
                        double ox = (Math.random() - 0.5) * 20;
                        double oz = (Math.random() - 0.5) * 20;
                        world.dropItem(target.clone().add(ox, 20, oz), new org.bukkit.inventory.ItemStack(Material.TNT));
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 20L);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
