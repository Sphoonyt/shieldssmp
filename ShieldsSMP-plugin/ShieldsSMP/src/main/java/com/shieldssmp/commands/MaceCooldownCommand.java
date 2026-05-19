package com.shieldssmp.commands;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.listeners.MaceListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public final class MaceCooldownCommand implements CommandExecutor, TabCompleter {

    private final MaceListener listener;

    public MaceCooldownCommand(MaceListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /macecooldown  →  print current value
        if (args.length == 0) {
            long current = listener.getLandingCooldownSeconds();
            sender.sendMessage(
                    Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                             .append(Component.text("Landing cooldown is currently ", NamedTextColor.GRAY))
                             .append(Component.text(current + "s", NamedTextColor.YELLOW, TextDecoration.BOLD))
                             .append(Component.text(". Usage: /macecooldown <seconds>", NamedTextColor.GRAY)));
            return true;
        }

        // /macecooldown <seconds>
        long seconds;
        try {
            seconds = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text(
                    "[ShieldsSMP] Invalid number: " + args[0], NamedTextColor.RED));
            return true;
        }

        if (seconds < 0) {
            sender.sendMessage(Component.text(
                    "[ShieldsSMP] Cooldown must be 0 or greater.", NamedTextColor.RED));
            return true;
        }

        listener.setLandingCooldown(seconds);

        sender.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                         .append(Component.text("Landing cooldown set to ", NamedTextColor.GREEN))
                         .append(Component.text(seconds + "s", NamedTextColor.YELLOW, TextDecoration.BOLD))
                         .append(Component.text(" and saved to config.", NamedTextColor.GREEN)));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("10", "15", "20", "30", "45", "60");
        }
        return List.of();
    }
}
