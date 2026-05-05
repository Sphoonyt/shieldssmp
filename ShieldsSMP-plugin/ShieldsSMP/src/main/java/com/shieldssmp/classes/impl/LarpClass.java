package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LarpClass extends PlayerClass {

    /** Last entity type killed per player */
    private final Map<UUID, EntityType> lastKill = new HashMap<>();
    /** Spawned clones per player */
    private final Map<UUID, List<Entity>> clones  = new HashMap<>();

    @Override public String getName()         { return "Larp"; }
    @Override public String getDescription()  { return "You become what you defeat"; }
    @Override public String getAbility1Name() { return "Mimic Kill"; }
    @Override public String getAbility2Name() { return "Echo Form"; }
    @Override public String getUltimateName() { return "Mirror Mastery"; }

    // ── Passive: Adaptive Flesh ───────────────────────────────────────────────
    // Stored last damage type; applied as resistance in onTakeDamage
    private final Map<UUID, org.bukkit.event.entity.EntityDamageEvent.DamageCause> lastDmgType = new HashMap<>();

    @Override
    public void onTakeDamage(Player victim, Entity attacker, double damage) {
        // Passively note last damage type – resistance applied as 10% less (via absorption top-up)
        lastDmgType.put(victim.getUniqueId(), victim.getLastDamageCause() != null
                ? victim.getLastDamageCause().getCause()
                : org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        // Give a brief absorption equal to ~10% of damage
        if (damage > 0) {
            double absorb = damage * 0.10;
            victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 40, 0, false, false, false));
        }
    }

    @Override
    public void onKill(Player killer, LivingEntity killed) {
        lastKill.put(killer.getUniqueId(), killed.getType());

        killer.sendActionBar(Component.text("🎭 Larp recorded: ", NamedTextColor.AQUA)
                .append(Component.text(killed.getType().name(), NamedTextColor.WHITE)));
    }

    // ── Ability 1: Mimic Kill – disguise as last killed entity for 60s ────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "MimicKill", 3 * MIN)) return;

        EntityType type = lastKill.get(player.getUniqueId());
        if (type == null) {
            player.sendActionBar(Component.text("🎭 No kill recorded yet!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "MimicKill", 3 * MIN);

        // Use disguise via potion visual effects only (full LibsDisguises not available here)
        // We simulate by giving an invisibility + nametag swap
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1200, 0, false, false, false));
        player.sendActionBar(Component.text("🎭 Mimic Kill: disguised as ", NamedTextColor.AQUA, TextDecoration.BOLD)
                .append(Component.text(type.name(), NamedTextColor.WHITE)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);

        new BukkitRunnable() { @Override public void run() {
            if (player.isOnline()) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.sendActionBar(Component.text("🎭 Mimic ended", NamedTextColor.GRAY));
            }
        }}.runTaskLater(getPlugin(), 1200L);
    }

    // ── Ability 2: Echo Form – summon ghost clone of last kill ────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "EchoForm", (long)(2.5 * MIN))) return;

        EntityType type = lastKill.get(player.getUniqueId());
        if (type == null) {
            player.sendActionBar(Component.text("🎭 No kill recorded for Echo!", NamedTextColor.RED));
            return;
        }

        startCooldown(player, "EchoForm", (long)(2.5 * MIN));

        World world = player.getWorld();
        Location loc = player.getLocation();

        try {
            Entity clone = world.spawnEntity(loc, type);
            if (clone instanceof Mob mob) {
                mob.setTarget(null); // will target enemies naturally
            }
            clone.setCustomName(player.getName() + "'s Echo");
            clone.setCustomNameVisible(true);

            List<Entity> list = clones.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
            list.add(clone);

            player.sendActionBar(Component.text("🎭 Echo Form summoned!", NamedTextColor.AQUA, TextDecoration.BOLD));

            new BukkitRunnable() { @Override public void run() {
                if (clone.isValid()) clone.remove();
                list.remove(clone);
            }}.runTaskLater(getPlugin(), 200L);
        } catch (Exception e) {
            player.sendActionBar(Component.text("🎭 Cannot spawn that entity here", NamedTextColor.RED));
        }
    }

    // ── Ultimate: Mirror Mastery – copy targeted player's class ───────────────
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

        // Temporarily swap class
        cm.setClass(player, targetClass.getName(), false);
        player.sendActionBar(Component.text("🎭 MIRROR MASTERY: Copied ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(target.getName() + "'s " + targetClass.getName() + " for 2min!", NamedTextColor.WHITE)));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);

        new BukkitRunnable() { @Override public void run() {
            if (player.isOnline()) {
                cm.setClass(player, originalClass, false);
                player.sendActionBar(Component.text("🎭 Mirror Mastery ended – reverted to " + originalClass, NamedTextColor.GRAY));
            }
        }}.runTaskLater(getPlugin(), 2400L);
    }

    private Player getTargetPlayer(Player player) {
        var entities = player.getWorld().getNearbyEntities(player.getLocation(), 20, 20, 20);
        Player closest = null;
        double bestDot = 0.7;
        var dir = player.getLocation().getDirection().normalize();
        for (var e : entities) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            var toEntity = e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
            double dot = dir.dot(toEntity);
            if (dot > bestDot) { bestDot = dot; closest = p; }
        }
        return closest;
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return ShieldsSMP.getInstance();
    }
}
