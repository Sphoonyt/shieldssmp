package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RandomizeClass extends PlayerClass {

    private final Random rng = new Random();

    private static final PotionEffectType[] TIER3 = {
        PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
        PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.NIGHT_VISION,
        PotionEffectType.JUMP_BOOST, PotionEffectType.SATURATION
    };

    private static final PotionEffectType[] TIER4 = {
        PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
        PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.ABSORPTION
    };

    @Override public String getName()         { return "Randomize"; }
    @Override public String getDescription()  { return "Chaos incarnate — luck favours the bold"; }
    @Override public String getAbility1Name() { return "Stat Surge"; }
    @Override public String getAbility2Name() { return "Chaos Potion"; }
    @Override public String getUltimateName() { return "Jackpot"; }

    // ── Passive: Fortune Touch – 10% crit chance ──────────────────────────────
    @Override
    public void onDealDamage(Player attacker, LivingEntity victim, double damage) {
        if (rng.nextInt(10) == 0) {
            victim.damage(damage * 0.5, attacker);
            attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0,1,0), 10);
            attacker.sendActionBar(Component.text("🎲 Fortune Crit! (+50% damage)", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
    }

    // ── Ability 1: Stat Surge ─────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "StatSurge", (long)(1.5 * MIN))) return;
        startCooldown(player, "StatSurge", (long)(1.5 * MIN));

        String[] stats = {"Attack Speed", "Damage", "Speed", "Max Health", "Jump"};
        String chosen = stats[rng.nextInt(stats.length)];

        switch (chosen) {
            case "Attack Speed" -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,        240, 3));
            case "Damage"       -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,     240, 4));
            case "Speed"        -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        240, 4));
            case "Max Health"   -> { player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 240, 2));
                                     player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 240, 2)); }
            case "Jump"         -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,   240, 5));
        }

        player.sendActionBar(Component.text("🎲 Stat Surge: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(chosen + " for 12s!", NamedTextColor.YELLOW)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    // ── Ability 2: Chaos Potion ───────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "ChaosPotion", 2 * MIN)) return;
        startCooldown(player, "ChaosPotion", 2 * MIN);

        PotionEffectType type = TIER3[rng.nextInt(TIER3.length)];
        player.addPotionEffect(new PotionEffect(type, 200, 2));

        player.sendActionBar(Component.text("⚗ Chaos Potion: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(type.key().value().replace("_"," ") + " III for 10s!", NamedTextColor.YELLOW)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1f, 1.2f);
    }

    // ── Ultimate: Jackpot – ALL buffs + huge damage aura ─────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Jackpot", 5 * MIN)) return;
        startCooldown(player, "Jackpot", 5 * MIN);

        // All stat surge buffs maxed
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,        160, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,      160, 5));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,         160, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST,  160, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,  160, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,    160, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,    160, 5));

        // 3 random Tier 4 effects
        List<PotionEffectType> pool = new ArrayList<>(Arrays.asList(TIER4));
        Collections.shuffle(pool, rng);
        for (int i = 0; i < 3; i++)
            player.addPotionEffect(new PotionEffect(pool.get(i), 160, 4));

        // Damage aura – deal 10 hearts true damage to all nearby enemies
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 8, 8, 8)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.setHealth(Math.max(0, p.getHealth() - 20)); // 10 hearts
            p.sendActionBar(Component.text("🎰 Hit by Jackpot aura! 10 hearts damage!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("🎰 JACKPOT! ALL buffs + 10-heart aura!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0,1,0), 60, 0.8, 1, 0.8);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0,1,0), 40, 1, 1, 1, 0.3);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
