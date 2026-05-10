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

    @Override public String getName()         { return "Terrorist"; }
    @Override public String getDescription()  { return "Controlled chaos and superior firepower"; }
    @Override public String getAbility1Name() { return "Carpet Bomb"; }
    @Override public String getAbility2Name() { return "Void Breach"; }
    @Override public String getUltimateName() { return "Tactical Nuke"; }

    public boolean hasBlastProof() { return true; }

    // ── Ability 1: Carpet Bomb – 16 TNT in two rings ─────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "CarpetBomb", (long)(1.5 * MIN))) return;
        startCooldown(player, "CarpetBomb", (long)(1.5 * MIN));

        Location loc = player.getLocation();
        World world = player.getWorld();

        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < 8; i++) {
                double angle  = Math.toRadians(i * 45.0);
                double speed  = 1.4 + ring * 0.5;
                double yBoost = ring == 0 ? 0.3 : 0.9;

                TNTPrimed tnt = world.spawn(loc.clone().add(0, 1, 0), TNTPrimed.class);
                tnt.setFuseTicks(30 + ring * 12);
                tnt.setVelocity(new Vector(Math.cos(angle) * speed, yBoost, Math.sin(angle) * speed));
                tnt.setMetadata("carpet_bomb_owner", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
            }
        }

        player.sendActionBar(Component.text("💣 CARPET BOMB – 16 TNT!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        world.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.5f, 0.8f);
    }

    // ── Ability 2: Void Breach ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBreach", 2 * MIN)) return;
        startCooldown(player, "VoidBreach", 2 * MIN);

        Location start = player.getEyeLocation();
        Vector dir = start.getDirection().normalize();
        World world = player.getWorld();

        player.sendActionBar(Component.text("💥 Void Breach!", NamedTextColor.GOLD, TextDecoration.BOLD));
        world.playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.8f);

        new BukkitRunnable() {
            final Location pos = start.clone();
            int steps = 0;
            final Set<UUID> hitPlayers = new HashSet<>();

            @Override public void run() {
                if (++steps > 60) { cancel(); return; }
                pos.add(dir);
                world.spawnParticle(Particle.EXPLOSION, pos, 2, 0.1, 0.1, 0.1, 0);

                Material mat = pos.getBlock().getType();
                if (mat.isSolid() && mat != Material.BEDROCK && mat != Material.BARRIER)
                    pos.getBlock().setType(Material.AIR);

                for (Entity e : world.getNearbyEntities(pos, 1.5, 1.5, 1.5)) {
                    if (!(e instanceof Player p) || p.equals(player) || hitPlayers.contains(p.getUniqueId())) continue;
                    hitPlayers.add(p.getUniqueId());
                    p.damage(4, player);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ultimate: Tactical Nuke – griefing explosion + true damage ───────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "TacticalNuke", 5 * MIN)) return;
        startCooldown(player, "TacticalNuke", 5 * MIN);

        Location target = player.getTargetBlock(null, 100).getLocation().add(0.5, 0, 0.5);
        World world = player.getWorld();

        player.sendActionBar(Component.text("☢ NUKE designated! 5s!", NamedTextColor.RED, TextDecoration.BOLD));

        new BukkitRunnable() {
            int countdown = 5;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (countdown > 0) {
                    world.playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL, 2f, 1.5f);
                    world.spawnParticle(Particle.LARGE_SMOKE, target.clone().add(0, countdown * 4, 0), 6, 2, 1, 2);
                    for (Player p : world.getPlayers())
                        if (p.getLocation().distance(target) < 60)
                            p.sendActionBar(Component.text("☢ NUKE in " + countdown + "s!", NamedTextColor.RED, TextDecoration.BOLD));
                    countdown--;
                } else {
                    cancel();
                    world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 5f, 0.3f);
                    world.playSound(target, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.3f);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, target, 25, 8, 8, 8);
                    world.spawnParticle(Particle.LARGE_SMOKE, target, 250, 12, 6, 12);

                    // 50 hearts true damage to all nearby players
                    for (Entity e : world.getNearbyEntities(target, 10, 10, 10)) {
                        if (!(e instanceof Player p) || p.equals(player)) continue;
                        p.setHealth(Math.max(0, p.getHealth() - 100.0));
                        p.sendActionBar(Component.text("☢ NUKED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                    }

                    // Griefing: spawn 10 TNT at impact for actual block destruction
                    for (int i = 0; i < 10; i++) {
                        double ox = (Math.random() - 0.5) * 16;
                        double oz = (Math.random() - 0.5) * 16;
                        TNTPrimed tnt = world.spawn(target.clone().add(ox, 2, oz), TNTPrimed.class);
                        tnt.setFuseTicks(4 + i * 2);
                        tnt.setMetadata("nuke_owner", new FixedMetadataValue(getPlugin(), player.getUniqueId().toString()));
                    }
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 20L);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
