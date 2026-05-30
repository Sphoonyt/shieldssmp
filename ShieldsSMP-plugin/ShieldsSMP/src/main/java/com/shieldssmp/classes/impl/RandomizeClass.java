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

    private static final PotionEffectType[] TIER3 = {
        PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
        PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.NIGHT_VISION
    };

    @Override public String getName()         { return "Randomize"; }
    @Override public String getDescription()  { return "Chaos incarnate – luck favours the bold"; }
    @Override public String getAbility1Name() { return "Stat Surge"; }
    @Override public String getAbility2Name() { return "Chaos Potion"; }
    @Override public String getUltimateName() { return "Jackpot"; }

    // ── Passive: 10% crit chance ──────────────────────────────────────────────
    @Override
    public void onDealDamage(Player attacker, LivingEntity victim, double damage) {
        if (rng.nextInt(10) == 0) {
            victim.damage(damage * 0.5, attacker);
            attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0,1,0), 8);
            attacker.sendActionBar(Component.text("🎲 Fortune Crit!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
    }

    // ── Ability 1: Stat Surge ─────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "StatSurge", (long)(1.5 * MIN))) return;
        startCooldown(player, "StatSurge", (long)(1.5 * MIN), org.bukkit.Material.SHIELD);

        String[] stats = {"Attack Speed", "Damage", "Speed", "Max Health", "Jump"};
        String chosen = stats[rng.nextInt(stats.length)];
        switch (chosen) {
            case "Attack Speed" -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,         240, 2));
            case "Damage"       -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,      240, 3));
            case "Speed"        -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,         240, 3));
            case "Max Health"   -> { player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST,240, 1));
                                      player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,240, 2)); }
            case "Jump"         -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,    240, 4));
        }
        player.sendActionBar(Component.text("🎲 Stat Surge: " + chosen + " for 12s!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }

    // ── Ability 2: Chaos Potion ───────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "ChaosPotion", 2 * MIN)) return;
        startCooldown(player, "ChaosPotion", 2 * MIN, org.bukkit.Material.SHIELD);

        PotionEffectType type = TIER3[rng.nextInt(TIER3.length)];
        player.addPotionEffect(new PotionEffect(type, 200, 2));
        player.sendActionBar(Component.text("⚗ Chaos Potion: " + type.key().value().replace("_"," ") + " III for 10s!", NamedTextColor.GREEN, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1f, 1.2f);
    }

    // ── Ultimate: Jackpot – 3 random buffs + 4-heart damage aura ─────────────
    // NERFED: removed instakill. Aura does 4 hearts (not 10), kept 3 random T4 potions.
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Jackpot", 5 * MIN)) return;
        startCooldown(player, "Jackpot", 5 * MIN, org.bukkit.Material.SHIELD);

        // All stat surge buffs
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,        160, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,      160, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,         160, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST,  160, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,  160, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,    160, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,    160, 4));

        // 3 random bonus potions
        List<PotionEffectType> pool = new ArrayList<>(Arrays.asList(TIER3));
        Collections.shuffle(pool, rng);
        for (int i = 0; i < 3; i++)
            player.addPotionEffect(new PotionEffect(pool.get(i), 160, 3));

        // Aura: 4 hearts damage to nearby (NOT instakill)
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 8, 8, 8)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.damage(8, player); // 4 hearts via normal damage (respects armor)
            p.sendActionBar(Component.text("🎰 Jackpot aura: 4 hearts!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("🎰 JACKPOT! All buffs + aura!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0,1,0), 40, 0.8, 1, 0.8);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
