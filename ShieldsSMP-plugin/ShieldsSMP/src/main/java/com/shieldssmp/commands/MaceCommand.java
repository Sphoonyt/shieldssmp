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
import org.bukkit.inventory.ItemFlag;
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
        Player target;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("Console usage: /mace <player>", NamedTextColor.RED));
                return true;
            }
            target = (Player) sender;
        } else {
            target = plugin.getServer().getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
                return true;
            }
        }

        target.getInventory().addItem(buildMace());

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
                         .append(Component.text("Dash forward", NamedTextColor.GRAY)
                                          .decoration(TextDecoration.ITALIC, false)),
                Component.text("  3-Hit Combo  ", NamedTextColor.RED, TextDecoration.BOLD)
                         .append(Component.text("Launch yourself upward", NamedTextColor.GRAY)
                                          .decoration(TextDecoration.ITALIC, false)),
                Component.text("  On Landing  ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                         .append(Component.text("Shockwave + 30s cooldown", NamedTextColor.GRAY)
                                          .decoration(TextDecoration.ITALIC, false)),
                Component.text(""),
                Component.text("  Shockwave damages ½ armor of nearby players", NamedTextColor.DARK_GRAY)
                         .decoration(TextDecoration.ITALIC, false)
        ));

        // Enchantments
        meta.addEnchant(Enchantment.WIND_BURST,  1,  true); // Natural wind burst on hit
        meta.addEnchant(Enchantment.UNBREAKING,  10, true);
        meta.addEnchant(Enchantment.MENDING,     1,  true);
        meta.addEnchant(Enchantment.SHARPNESS,   5,  true);

        // Truly unbreakable – no durability loss at all
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // PDC tag so the plugin reliably identifies this item
        meta.getPersistentDataContainer().set(
                plugin.getMaceKey(),
                PersistentDataType.BOOLEAN,
                true);

        mace.setItemMeta(meta);
        return mace;
    }
}
