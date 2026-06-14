package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.items.ClassShieldBuilder;
import com.shieldssmp.items.SpecialItems;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Keybind mappings:
 *
 *   Shift + Left-Click  (main hand, any item)      → Ability 1
 *   Shift + Right-Click (main hand, any item)      → Ability 2
 *   Right-Click         (main hand, Ultimate Upgrader held) → Ultimate
 *
 * Requires class shield somewhere in inventory for ability 1 & 2.
 * Requires Ultimate Upgrader held in main hand for ultimate.
 */
public class AbilityKeybindListener implements Listener {

    private final ClassManager       cm;
    private final ClassShieldBuilder shieldBuilder;
    private final SpecialItems       specialItems;

    public AbilityKeybindListener(ShieldsSMP plugin) {
        this.cm            = plugin.getClassManager();
        this.shieldBuilder = plugin.getClassShieldBuilder();
        this.specialItems  = plugin.getSpecialItems();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Only main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action    = event.getAction();
        boolean left     = action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK;
        boolean right    = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean sneaking = player.isSneaking();

        ItemStack held = player.getInventory().getItemInMainHand();
        boolean holdingShield = shieldBuilder.isClassShield(held);

        // ── Ultimate: Right-Click with class shield in MAIN HAND
        //             AND Ultimate Upgrader anywhere in inventory ──────────────
        if (right && !sneaking && holdingShield && hasUltimateUpgrader(player)) {
            event.setCancelled(true);
            cm.useUltimate(player);
            return;
        }

        // ── Abilities 1 & 2: require class shield in MAIN HAND ──────────────
        if (!holdingShield) return; // shield must be in main hand

        if (sneaking && left) {
            event.setCancelled(true);
            cm.useAbility1(player);
        } else if (sneaking && right) {
            event.setCancelled(true);
            cm.useAbility2(player);
        }
    }

    // ── Block dropping the class shield ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (shieldBuilder.isClassShield(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(
                    Component.text("⛔ Cannot drop your class shield!", NamedTextColor.RED));
        }
    }

    /** Shield must be in main hand for all ability activation */
    private boolean hasClassShield(Player player) {
        return shieldBuilder.isClassShield(player.getInventory().getItemInMainHand());
    }

    private boolean hasUltimateUpgrader(Player player) {
        for (ItemStack item : player.getInventory().getContents())
            if (specialItems.isUltimateUpgrader(item)) return true;
        if (specialItems.isUltimateUpgrader(player.getInventory().getItemInOffHand())) return true;
        return false;
    }
}
