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

public class RandomizeClass extends PlayerClass {

    private final Random rng = new Random();

    // PvP-only stat surges
    private static final PotionEffectType[] STAT_SURGES = {
        PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.HASTE,
        PotionEffectType.JUMP_BOOST, PotionEffectType.RESISTANCE
    };

    // PvP-only chaos potions (no night vision / saturation)
    private static final PotionEffectType[] CHAOS_POTIONS = {
        PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
        PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.ABSORPTION
    };

    @Override public String getName()         { return "Randomize"; }
    @Override public String getDescription()  { return "Chaos incarnate – luck favours the bold"; }
    @Override public String getAbility1Name() { return "Stat Surge"; }
    @Override public String getAbility2Name() { return "Chaos Potion"; }
    @Override public String getUltimateName() { return "Jackpot"; }
    @Override public String getAbility1CooldownKey() { return "StatSurge"; }
    @Override public String getAbility2CooldownKey() { return "ChaosPotion"; }
    @Override public String getUltimateCooldownKey() { return "Jackpot"; }

    @Override
    public void onDealDamage(Player attacker, LivingEntity victim, double damage) {
        if (rng.nextInt(10) == 0) {
            victim.damage(damage * 0.5, attacker);
            attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0,1,0), 8);
            attacker.sendActionBar(Component.text("🎲 Fortune Crit!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
    }

    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "StatSurge", (long)(1.5 * MIN))) return;
        startCooldown(player, "StatSurge", (long)(1.5 * MIN));

        PotionEffectType chosen = STAT_SURGES[rng.nextInt(STAT_SURGES.length)];
        player.addPotionEffect(new PotionEffect(chosen, 240, 3));
        player.sendActionBar(Component.text("🎲 Stat Surge: " + chosen.key().value().replace("_"," ") + " for 12s!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "ChaosPotion", 2 * MIN)) return;
        startCooldown(player, "ChaosPotion", 2 * MIN);

        PotionEffectType type = CHAOS_POTIONS[rng.nextInt(CHAOS_POTIONS.length)];
        player.addPotionEffect(new PotionEffect(type, 200, 2));
        player.sendActionBar(Component.text("⚗ Chaos Potion: " + type.key().value().replace("_"," ") + " III for 10s!", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1f, 1.2f);
    }

    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Jackpot", 5 * MIN)) return;
        startCooldown(player, "Jackpot", 5 * MIN);

        for (PotionEffectType t : STAT_SURGES)
            player.addPotionEffect(new PotionEffect(t, 160, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 160, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,  160, 2));

        List<PotionEffectType> pool = new ArrayList<>(Arrays.asList(CHAOS_POTIONS));
        Collections.shuffle(pool, rng);
        for (int i = 0; i < 3; i++) player.addPotionEffect(new PotionEffect(pool.get(i), 160, 3));

        // 4-heart aura via normal damage (armor applies)
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 8, 8, 8)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.damage(8, player);
            p.sendActionBar(Component.text("🎰 Jackpot aura: 4 hearts!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("🎰 JACKPOT! All PvP buffs + aura!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0,1,0), 40, 0.8, 1, 0.8);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
