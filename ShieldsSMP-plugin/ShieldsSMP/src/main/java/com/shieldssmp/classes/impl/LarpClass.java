package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LarpClass extends PlayerClass {

    private final Map<UUID, EntityType>   lastKill    = new HashMap<>();
    private final Map<UUID, Entity>       disguises   = new HashMap<>();
    private final Map<UUID, List<Entity>> clones      = new HashMap<>();

    // Safe entity types that can be mounted on the player
    private static final List<EntityType> SAFE_MOUNTS = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CREEPER, EntityType.ENDERMAN, EntityType.BLAZE,
            EntityType.WITCH, EntityType.WITHER_SKELETON, EntityType.STRAY,
            EntityType.HUSK, EntityType.DROWNED, EntityType.PILLAGER,
            EntityType.VINDICATOR, EntityType.PHANTOM, EntityType.VEX,
            EntityType.IRON_GOLEM, EntityType.CAVE_SPIDER
    );

    @Override public String getName()         { return "Larp"; }
    @Override public String getDescription()  { return "You become what you defeat"; }
    @Override public String getAbility1Name() { return "Mimic Kill"; }
    @Override public String getAbility2Name() { return "Echo Form"; }
    @Override public String getUltimateName() { return "Mirror Mastery"; }

    // ── Passive: Adaptive Flesh – brief resistance after taking damage ─────────
    @Override
    public void onTakeDamage(Player victim, Entity attacker, double damage) {
        if (damage > 0)
            victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 0, false, false, false));
    }

    @Override
    public void onKill(Player killer, LivingEntity killed) {
        lastKill.put(killer.getUniqueId(), killed.getType());
        killer.sendActionBar(Component.text("🎭 Recorded: " + killed.getType().name(), NamedTextColor.AQUA));
    }

    // ── Ability 1: Mimic Kill – become the mob (mob sits on top of player) ────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "MimicKill", 3 * MIN)) return;

        EntityType type = lastKill.get(player.getUniqueId());
        if (type == null) {
            player.sendActionBar(Component.text("🎭 Kill something first!", NamedTextColor.RED));
            return;
        }

        // Use safe fallback if recorded type can't be mounted
        EntityType spawnType = SAFE_MOUNTS.contains(type) ? type : EntityType.ZOMBIE;
        startCooldown(player, "MimicKill", 3 * MIN);

        // Remove existing disguise
        Entity old = disguises.remove(player.getUniqueId());
        if (old != null && old.isValid()) old.remove();

        World world = player.getWorld();

        try {
            LivingEntity mob = (LivingEntity) world.spawnEntity(player.getLocation(), spawnType);
            mob.setAI(false);
            mob.setGravity(false);
            mob.setSilent(true);
            mob.setInvulnerable(true);
            mob.setCustomNameVisible(false);
            // Remove all equipment so it looks natural
            if (mob instanceof Mob m) m.setTarget(null);

            // Sit the mob ON the player (player is vehicle, mob is passenger riding player)
            // This makes the mob appear on the player's head
            player.addPassenger(mob);

            disguises.put(player.getUniqueId(), mob);
            applyMimicStats(player, spawnType);

            player.sendActionBar(Component.text("🎭 MIMIC: You are now a " + spawnType.name() + "!", NamedTextColor.AQUA, TextDecoration.BOLD));
            world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);

            UUID pid = player.getUniqueId();
            new BukkitRunnable() {
                @Override public void run() {
                    Entity disguise = disguises.remove(pid);
                    if (disguise != null && disguise.isValid()) {
                        player.removePassenger(disguise);
                        disguise.remove();
                    }
                    if (player.isOnline()) {
                        removeMimicStats(player);
                        player.sendActionBar(Component.text("🎭 Mimic ended", NamedTextColor.GRAY));
                    }
                }
            }.runTaskLater(getPlugin(), 1200L);

        } catch (Exception e) {
            player.sendActionBar(Component.text("🎭 Cannot mimic that type!", NamedTextColor.RED));
        }
    }

    private void applyMimicStats(Player player, EntityType type) {
        switch (type) {
            case IRON_GOLEM      -> { player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,        1200, 3));
                                      player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,      1200, 2)); }
            case CREEPER, BLAZE  -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,             1200, 2));
            case ENDERMAN        -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,           1200, 3));
                                      player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,      1200, 3)); }
            case WITHER_SKELETON -> { player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,        1200, 2));
                                      player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 1200, 0)); }
            case PHANTOM         -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,           1200, 2));
                                      player.setAllowFlight(true); }
            default              -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,          1200, 1));
        }
    }

    private void removeMimicStats(Player player) {
        for (PotionEffectType t : List.of(PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
                PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST, PotionEffectType.FIRE_RESISTANCE))
            player.removePotionEffect(t);
        if (!player.getGameMode().equals(GameMode.CREATIVE))
            player.setAllowFlight(false);
    }

    // ── Ability 2: Echo Form – clone that ONLY targets enemy players ──────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "EchoForm", (long)(2.5 * MIN))) return;

        EntityType type = lastKill.get(player.getUniqueId());
        if (type == null) {
            player.sendActionBar(Component.text("🎭 No kill recorded!", NamedTextColor.RED));
            return;
        }

        EntityType spawnType = SAFE_MOUNTS.contains(type) ? type : EntityType.ZOMBIE;
        startCooldown(player, "EchoForm", (long)(2.5 * MIN));

        World world  = player.getWorld();
        UUID  pid    = player.getUniqueId();

        try {
            LivingEntity echo = (LivingEntity) world.spawnEntity(
                    player.getLocation().clone().add(2, 0, 0), spawnType);
            echo.setCustomName("§b" + player.getName() + "'s Echo");
            echo.setCustomNameVisible(true);
            echo.setMetadata("larp_echo", new FixedMetadataValue(getPlugin(), pid.toString()));

            List<Entity> list = clones.computeIfAbsent(pid, k -> new ArrayList<>());
            list.add(echo);

            // Target-finding ticker – finds nearest ENEMY player and sets as target
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!echo.isValid() || (ticks += 20) >= 200) {
                        if (echo.isValid()) echo.remove();
                        list.remove(echo);
                        cancel();
                        return;
                    }
                    if (!(echo instanceof Mob mob)) return;

                    // Nearest player that is NOT the owner
                    Player nearest = null;
                    double best = Double.MAX_VALUE;
                    for (Entity e : world.getNearbyEntities(echo.getLocation(), 24, 24, 24)) {
                        if (!(e instanceof Player p) || p.getUniqueId().equals(pid)) continue;
                        double d = echo.getLocation().distanceSquared(p.getLocation());
                        if (d < best) { best = d; nearest = p; }
                    }
                    if (nearest != null) mob.setTarget(nearest);
                }
            }.runTaskTimer(getPlugin(), 5L, 20L);

            player.sendActionBar(Component.text("🎭 Echo Form summoned – hunts your enemies!", NamedTextColor.AQUA, TextDecoration.BOLD));
            world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.9f);

        } catch (Exception e) {
            player.sendActionBar(Component.text("🎭 Cannot spawn echo here!", NamedTextColor.RED));
        }
    }

    // ── Ultimate: Mirror Mastery ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "MirrorMastery", 5 * MIN)) return;

        Player target = getTargetPlayer(player);
        if (target == null) {
            player.sendActionBar(Component.text("🎭 Look at a player to copy!", NamedTextColor.RED));
            return;
        }

        ClassManager cm = ShieldsSMP.getInstance().getClassManager();
        PlayerClass targetClass = cm.getPlayerClass(target.getUniqueId());
        if (targetClass == null) {
            player.sendActionBar(Component.text("🎭 That player has no class!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "MirrorMastery", 5 * MIN);
        String originalClass = cm.getPlayerData(player.getUniqueId()).getClassName();

        cm.setClass(player, targetClass.getName(), false, true);
        player.sendActionBar(Component.text("🎭 MIRROR: Copied " + target.getName() + "'s "
                + targetClass.getName() + " for 2min!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);

        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) {
                    cm.setClass(player, originalClass, false, true);
                    player.sendActionBar(Component.text("🎭 Reverted to " + originalClass, NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(getPlugin(), 2400L);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        Entity disguise = disguises.remove(id);
        if (disguise != null && disguise.isValid()) {
            player.removePassenger(disguise);
            disguise.remove();
        }
        removeMimicStats(player);
        List<Entity> list = clones.remove(id);
        if (list != null) list.forEach(e -> { if (e.isValid()) e.remove(); });
    }

    private Player getTargetPlayer(Player player) {
        Player closest = null;
        double bestDot = 0.7;
        var dir = player.getLocation().getDirection().normalize();
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 20, 20, 20)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            var toEntity = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            double dot = dir.dot(toEntity);
            if (dot > bestDot) { bestDot = dot; closest = p; }
        }
        return closest;
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
