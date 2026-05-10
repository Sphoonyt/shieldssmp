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

        // ── Ultimate: Right-Click while holding Ultimate Upgrader ─────────────
        if (right && !sneaking && specialItems.isUltimateUpgrader(held)) {
            event.setCancelled(true);
            cm.useUltimate(player);
            return;
        }

        // ── Abilities: require class shield in inventory ───────────────────────
        if (!hasClassShield(player)) return;

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

    private boolean hasClassShield(Player player) {
        if (shieldBuilder.isClassShield(player.getInventory().getItemInOffHand())) return true;
        for (ItemStack item : player.getInventory().getContents())
            if (shieldBuilder.isClassShield(item)) return true;
        return false;
    }
}
