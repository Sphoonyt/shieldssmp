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

    private final Map<UUID, EntityType>   lastKill  = new HashMap<>();
    private final Map<UUID, Entity>       disguises = new HashMap<>();
    private final Map<UUID, List<Entity>> clones    = new HashMap<>();
    private final Set<UUID> mimicActive = new HashSet<>();

    private static final List<EntityType> SAFE = List.of(
        EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER,
        EntityType.ENDERMAN, EntityType.BLAZE, EntityType.WITCH, EntityType.WITHER_SKELETON,
        EntityType.STRAY, EntityType.HUSK, EntityType.DROWNED, EntityType.PILLAGER,
        EntityType.VINDICATOR, EntityType.IRON_GOLEM, EntityType.CAVE_SPIDER);

    @Override public String getName()         { return "Larp"; }
    @Override public String getDescription()  { return "You become what you defeat"; }
    @Override public String getAbility1Name() { return "Mimic Kill"; }
    @Override public String getAbility2Name() { return "Echo Form"; }
    @Override public String getUltimateName() { return "Mirror Mastery"; }
    @Override public String getAbility1CooldownKey() { return "MimicKill"; }
    @Override public String getAbility2CooldownKey() { return "EchoForm"; }
    @Override public String getUltimateCooldownKey() { return "MirrorMastery"; }

    @Override
    public void onTakeDamage(Player victim, Entity attacker, double damage) {
        if (damage > 0) victim.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 25, 0, false, false, false));
    }

    @Override
    public void onKill(Player killer, LivingEntity killed) {
        lastKill.put(killer.getUniqueId(), killed.getType());
        killer.sendActionBar(Component.text("🎭 Recorded: " + killed.getType().name(), NamedTextColor.AQUA));
    }

    // ── Ability 1: Mimic Kill ─────────────────────────────────────────────────
    // Player becomes invisible. A mob of the recorded type rides on the player's
    // head. Stat buffs are applied. The rider mob has no AI.
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "MimicKill", 3 * MIN)) return;
        EntityType type = lastKill.get(player.getUniqueId());
        if (type == null) { player.sendActionBar(Component.text("🎭 Kill something first!", NamedTextColor.RED)); return; }
        EntityType spawn = SAFE.contains(type) ? type : EntityType.ZOMBIE;
        startCooldown(player, "MimicKill", 3 * MIN);

        // Remove old
        Entity old = disguises.remove(player.getUniqueId());
        if (old != null && old.isValid()) { player.removePassenger(old); old.remove(); }
        removeMimicBuffs(player);

        try {
            LivingEntity mob = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), spawn);
            mob.setAI(false); mob.setGravity(false); mob.setSilent(true);
            mob.setInvulnerable(true); mob.setCustomNameVisible(false);
            player.addPassenger(mob);   // mob rides on player's head
            disguises.put(player.getUniqueId(), mob);
            mimicActive.add(player.getUniqueId());

            // Invisibility so you look like the mob
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1200, 0, false, false, false));
            applyMimicBuffs(player, spawn);

            player.sendActionBar(Component.text("🎭 MIMIC: You are " + spawn.name() + " for 60s! Buffs applied.", NamedTextColor.AQUA, TextDecoration.BOLD));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);

            UUID pid = player.getUniqueId();
            new BukkitRunnable() {
                @Override public void run() {
                    Entity d = disguises.remove(pid);
                    if (d != null && d.isValid()) { player.removePassenger(d); d.remove(); }
                    mimicActive.remove(pid);
                    if (player.isOnline()) {
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);
                        removeMimicBuffs(player);
                        player.sendActionBar(Component.text("🎭 Mimic ended", NamedTextColor.GRAY));
                    }
                }
            }.runTaskLater(getPlugin(), 1200L);
        } catch (Exception e) {
            player.sendActionBar(Component.text("🎭 Cannot mimic that type!", NamedTextColor.RED));
        }
    }

    private void applyMimicBuffs(Player p, EntityType t) {
        switch (t) {
            case IRON_GOLEM -> { p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,  1200, 3));
                                  p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 1200, 2)); }
            case ENDERMAN   -> { p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      1200, 3));
                                  p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 1200, 3)); }
            case BLAZE, WITHER_SKELETON -> {
                                  p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,        1200, 2));
                                  p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,  1200, 0)); }
            case CREEPER    ->    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1200, 2));
            default         ->    p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 1200, 1));
        }
    }

    private void removeMimicBuffs(Player p) {
        for (PotionEffectType t : List.of(PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
                PotionEffectType.SPEED, PotionEffectType.JUMP_BOOST, PotionEffectType.FIRE_RESISTANCE))
            p.removePotionEffect(t);
    }

    // ── Ability 2: Echo Form – lives for 20s, hunts nearby enemies aggressively ─
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "EchoForm", (long)(2.5 * MIN))) return;
        EntityType type = lastKill.get(player.getUniqueId());
        if (type == null) { player.sendActionBar(Component.text("🎭 No kill recorded!", NamedTextColor.RED)); return; }
        EntityType spawn = SAFE.contains(type) ? type : EntityType.ZOMBIE;
        startCooldown(player, "EchoForm", (long)(2.5 * MIN));

        UUID pid = player.getUniqueId();
        World world = player.getWorld();

        try {
            LivingEntity echo = (LivingEntity) world.spawnEntity(player.getLocation().clone().add(2, 0, 0), spawn);
            echo.setCustomName("§b" + player.getName() + "'s Echo");
            echo.setCustomNameVisible(true);
            echo.setMetadata("larp_echo", new FixedMetadataValue(getPlugin(), pid.toString()));
            // Boost echo's health
            if (echo.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null)
                echo.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(40);
            echo.setHealth(40);

            List<Entity> list = clones.computeIfAbsent(pid, k -> new ArrayList<>());
            list.add(echo);

            // Aggressive retargeting every second for 20s
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!echo.isValid() || (ticks += 20) >= 400) { // 20s = 400 ticks
                        if (echo.isValid()) echo.remove();
                        list.remove(echo);
                        cancel(); return;
                    }
                    if (!(echo instanceof Mob mob)) return;
                    // Target nearest player that is NOT the owner
                    Player nearest = null; double best = Double.MAX_VALUE;
                    for (Entity e : world.getNearbyEntities(echo.getLocation(), 24, 24, 24)) {
                        if (!(e instanceof Player p) || p.getUniqueId().equals(pid)) continue;
                        double d = echo.getLocation().distanceSquared(p.getLocation());
                        if (d < best) { best = d; nearest = p; }
                    }
                    if (nearest != null) mob.setTarget(nearest);
                }
            }.runTaskTimer(getPlugin(), 5L, 20L);

            player.sendActionBar(Component.text("🎭 Echo Form – hunts enemies for 20s!", NamedTextColor.AQUA, TextDecoration.BOLD));
        } catch (Exception e) {
            player.sendActionBar(Component.text("🎭 Cannot spawn echo here!", NamedTextColor.RED));
        }
    }

    // ── Ultimate: Mirror Mastery ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "MirrorMastery", 5 * MIN)) return;
        Player target = getTargetPlayer(player);
        if (target == null) { player.sendActionBar(Component.text("🎭 Look at a player!", NamedTextColor.RED)); return; }
        ClassManager cm = ShieldsSMP.getInstance().getClassManager();
        PlayerClass targetClass = cm.getPlayerClass(target.getUniqueId());
        if (targetClass == null) { player.sendActionBar(Component.text("🎭 Target has no class!", NamedTextColor.RED)); return; }
        startCooldown(player, "MirrorMastery", 5 * MIN);
        String originalClass = cm.getPlayerData(player.getUniqueId()).getClassName();
        PlayerClass oldCls = cm.getPlayerClass(player.getUniqueId());
        if (oldCls != null) oldCls.onUnequip(player);
        cm.setClass(player, targetClass.getName(), false, true);
        player.sendActionBar(Component.text("🎭 MIRROR: Copied " + targetClass.getName() + " for 2min!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                PlayerClass copied = cm.getPlayerClass(player.getUniqueId());
                if (copied != null) copied.onUnequip(player);
                cm.setClass(player, originalClass, false, true);
                player.sendActionBar(Component.text("🎭 Reverted to " + originalClass, NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 2400L);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        Entity d = disguises.remove(id);
        if (d != null && d.isValid()) { player.removePassenger(d); d.remove(); }
        mimicActive.remove(id);
        removeMimicBuffs(player);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        List<Entity> list = clones.remove(id);
        if (list != null) list.forEach(e -> { if (e.isValid()) e.remove(); });
    }

    private Player getTargetPlayer(Player player) {
        Player closest = null; double bestDot = 0.7;
        var dir = player.getLocation().getDirection().normalize();
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 20, 20, 20)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            double dot = dir.dot(e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize());
            if (dot > bestDot) { bestDot = dot; closest = p; }
        }
        return closest;
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
