package com.shieldssmp.commands;

import com.shieldssmp.ShieldsSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class MaceCommand implements CommandExecutor {

    private final ShieldsSMP plugin;

    public MaceCommand(ShieldsSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Determine target
        Player target;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text(
                        "Console usage: /mace <player>", NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
        } else {
            target = plugin.getServer().getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text(
                        "Player not found: " + args[0], NamedTextColor.RED));
                return true;
            }
        }

        ItemStack mace = buildMace();
        target.getInventory().addItem(mace);

        target.sendMessage(
                Component.text("You received the ", NamedTextColor.GRAY)
                         .append(Component.text("Shields SMP Mace", NamedTextColor.GOLD, TextDecoration.BOLD))
                         .append(Component.text("!", NamedTextColor.GRAY)));

        if (!target.equals(sender)) {
            sender.sendMessage(Component.text(
                    "Gave Shields SMP Mace to " + target.getName(), NamedTextColor.GREEN));
        }

        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Item builder  –  also used externally (e.g. custom crafting, starter kits)
    // ══════════════════════════════════════════════════════════════════════════

    public ItemStack buildMace() {
        ItemStack mace = new ItemStack(Material.MACE);
        ItemMeta  meta = mace.getItemMeta();

        // Display name
        meta.displayName(
                Component.text("Shields SMP Mace", NamedTextColor.GOLD, TextDecoration.BOLD)
                         .decoration(TextDecoration.ITALIC, false));

        // Lore
        meta.lore(List.of(
                Component.text(""),
                Component.text("  Right-Click  ", NamedTextColor.AQUA, TextDecoration.BOLD)
                         .append(Component.text("Dash forward", NamedTextColor.GRAY)),
                Component.text("  On Landing  ", NamedTextColor.AQUA, TextDecoration.BOLD)
                         .append(Component.text("Windburst push", NamedTextColor.GRAY)),
                Component.text("  3-Hit Combo  ", NamedTextColor.RED, TextDecoration.BOLD)
                         .append(Component.text("Launch + Shockwave", NamedTextColor.GRAY)),
                Component.text(""),
                Component.text("  Shockwave damages ½ armor durability", NamedTextColor.DARK_RED)
                         .decoration(TextDecoration.ITALIC, false)
        ));

        // Enchantments (purely cosmetic power boost)
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING,     1, true);
        meta.addEnchant(Enchantment.SHARPNESS,   5, true);

        // PDC tag so the plugin can reliably identify this item
        meta.getPersistentDataContainer().set(
                plugin.getMaceKey(),
                PersistentDataType.BOOLEAN,
                true);

        mace.setItemMeta(meta);
        return mace;
    }
}
