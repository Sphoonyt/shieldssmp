package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.CooldownManager;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.items.ClassShieldBuilder;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Shows ability cooldowns in the action bar whenever a player holds their
 * class shield in their main hand OR has any ability on cooldown.
 *
 * Format:  A1 [✓]   A2 [14s]   U [47s]
 * Disabled: ⛔ Abilities Disabled
 */
public class AbilityHUDListener implements Listener {

    private final ShieldsSMP         plugin;
    private final ClassManager       cm;
    private final ClassShieldBuilder shieldBuilder;

    public AbilityHUDListener(ShieldsSMP plugin) {
        this.plugin        = plugin;
        this.cm            = plugin.getClassManager();
        this.shieldBuilder = plugin.getClassShieldBuilder();
        startHUDTicker();
    }

    private void startHUDTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateHUD(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 1 second refresh
    }

    private void updateHUD(Player player) {
        UUID id = player.getUniqueId();
        PlayerClass cls = cm.getPlayerClass(id);
        if (cls == null) return;

        boolean holdingShield = shieldBuilder.isClassShield(
                player.getInventory().getItemInMainHand());

        CooldownManager cd = cls.getCD();
        long a1  = cd.remainingSeconds(id, cls.getAbility1CooldownKey());
        long a2  = cd.remainingSeconds(id, cls.getAbility2CooldownKey());
        long ult = cd.remainingSeconds(id, cls.getUltimateCooldownKey());

        boolean anyCooldown = a1 > 0 || a2 > 0 || ult > 0;

        // Only send HUD if holding shield OR a cooldown is ticking
        if (!holdingShield && !anyCooldown) return;

        boolean disabled = cm.abilitiesDisabled(id)
                || !plugin.getLifeSystem().hasLives(id);

        player.sendActionBar(buildHUD(a1, a2, ult, disabled));
    }

    private Component buildHUD(long a1, long a2, long ult, boolean disabled) {
        if (disabled) {
            return Component.text("⛔ Abilities Disabled", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false);
        }

        return entry("A1", a1)
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(entry("A2", a2))
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(entry("U", ult));
    }

    private Component entry(String label, long remaining) {
        NamedTextColor labelCol = remaining > 0 ? NamedTextColor.RED   : NamedTextColor.GREEN;
        NamedTextColor timeCol  = remaining > 0 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
        String timeStr          = remaining > 0 ? remaining + "s"      : "✓";

        return Component.text(label, labelCol, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(" (", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(timeStr, timeCol)
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text(")", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
    }
}
