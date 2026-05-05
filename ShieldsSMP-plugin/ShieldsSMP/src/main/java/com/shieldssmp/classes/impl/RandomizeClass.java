package com.shieldssmp.classes.impl;

import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RandomizeClass extends PlayerClass {

    private final Random rng = new Random();

    private static final PotionEffectType[] TIER3_POSITIVE = {
        PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
        PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.NIGHT_VISION,
        PotionEffectType.JUMP_BOOST, PotionEffectType.SATURATION
    };

    private static final PotionEffectType[] TIER4_POSITIVE = {
        PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
        PotionEffectType.REGENERATION, PotionEffectType.HASTE, PotionEffectType.ABSORPTION
    };

    @Override public String getName()         { return "Randomize"; }
    @Override public String getDescription()  { return "Chaos incarnate — luck favours the bold"; }
    @Override public String getAbility1Name() { return "Stat Surge"; }
    @Override public String getAbility2Name() { return "Chaos Potion"; }
    @Override public String getUltimateName() { return "Jackpot"; }

    // ── Passive: Fortune Touch – 10% crit chance applied via damage boost ─────
    @Override
    public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, double damage) {
        if (rng.nextInt(10) == 0) {
            victim.damage(damage, attacker); // extra hit
            attacker.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0,1,0), 10);
            attacker.sendActionBar(Component.text("🎲 Fortune Crit!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
    }

    // ── Ability 1: Stat Surge ─────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "StatSurge", (long)(1.5 * MIN))) return;
        startCooldown(player, "StatSurge", (long)(1.5 * MIN));

        String[] stats = {"Attack Speed", "Damage", "Reach", "Speed", "Max Health", "Size"};
        String chosen = stats[rng.nextInt(stats.length)];

        switch (chosen) {
            case "Attack Speed" -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,     240, 2));
            case "Damage"       -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,  240, 3));
            case "Speed"        -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,     240, 3));
            case "Max Health"   -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 240, 1));
            }
            case "Size"         -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  240, -1)); // visual giant
            default             -> player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,     240, 2));
        }

        player.sendActionBar(Component.text("🎲 Stat Surge: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(chosen + " boosted for 12s!", NamedTextColor.YELLOW)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);

        new BukkitRunnable() { @Override public void run() {
            if (player.isOnline()) player.sendActionBar(Component.text("🎲 Stat Surge ended", NamedTextColor.GRAY));
        }}.runTaskLater(getPlugin(), 240L);
    }

    // ── Ability 2: Chaos Potion ───────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "ChaosPotion", 2 * MIN)) return;
        startCooldown(player, "ChaosPotion", 2 * MIN);

        PotionEffectType type = TIER3_POSITIVE[rng.nextInt(TIER3_POSITIVE.length)];
        player.addPotionEffect(new PotionEffect(type, 200, 2));

        player.sendActionBar(Component.text("⚗ Chaos Potion: ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(prettyName(type) + " III for 10s!", NamedTextColor.YELLOW)));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1f, 1.2f);
    }

    // ── Ultimate: Jackpot ─────────────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Jackpot", 5 * MIN)) return;
        startCooldown(player, "Jackpot", 5 * MIN);

        // All Stat Surge buffs
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,        160, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,     160, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        160, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 160, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,   160, 3));

        // 3 random Tier 4 potions
        List<PotionEffectType> pool = new ArrayList<>(Arrays.asList(TIER4_POSITIVE));
        Collections.shuffle(pool, rng);
        for (int i = 0; i < 3; i++) {
            player.addPotionEffect(new PotionEffect(pool.get(i), 160, 3));
        }

        player.sendActionBar(Component.text("🎰 JACKPOT! ALL buffs active for 8s!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0,1,0), 40, 0.5, 0.8, 0.5);
    }

    private String prettyName(PotionEffectType type) {
        return type.key().value().replace("_", " ");
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return com.shieldssmp.ShieldsSMP.getInstance();
    }
}
