package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.items.ClassShieldBuilder;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Keybind mappings:
 *   F  (not sneaking) → Ability 1
 *   F  (sneaking)     → Ability 2
 *   Q  (drop key)     → Ultimate
 *
 * Both require a class shield somewhere in inventory to activate.
 */
public class AbilityKeybindListener implements Listener {

    private final ShieldsSMP plugin;
    private final ClassManager cm;
    private final ClassShieldBuilder shieldBuilder;

    public AbilityKeybindListener(ShieldsSMP plugin) {
        this.plugin        = plugin;
        this.cm            = plugin.getClassManager();
        this.shieldBuilder = plugin.getClassShieldBuilder();
    }

    // ── F key ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!hasClassShield(player)) return;

        event.setCancelled(true); // always cancel – we handle it

        if (player.isSneaking()) {
            cm.useAbility2(player);
        } else {
            cm.useAbility1(player);
        }
    }

    // ── Q key ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // If what they're dropping IS the class shield, block it (handled elsewhere)
        if (shieldBuilder.isClassShield(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("⛔ Cannot drop your class shield!", NamedTextColor.RED));
            return;
        }

        // If they have a shield, Q fires Ultimate
        if (!hasClassShield(player)) return;
        event.setCancelled(true);
        cm.useUltimate(player);
    }

    private boolean hasClassShield(Player player) {
        if (shieldBuilder.isClassShield(player.getInventory().getItemInOffHand())) return true;
        for (var item : player.getInventory().getContents())
            if (shieldBuilder.isClassShield(item)) return true;
        return false;
    }
}
