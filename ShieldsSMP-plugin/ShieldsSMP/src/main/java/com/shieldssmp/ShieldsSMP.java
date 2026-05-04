package com.shieldssmp;

import com.shieldssmp.commands.MaceCooldownCommand;
import com.shieldssmp.commands.MaceCommand;
import com.shieldssmp.listeners.MaceListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShieldsSMP extends JavaPlugin {

    public static final String MACE_KEY_ID = "shields_smp_mace";

    private static ShieldsSMP instance;
    private NamespacedKey maceKey;
    private MaceListener maceListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        maceKey = new NamespacedKey(this, MACE_KEY_ID);

        maceListener = new MaceListener(this);
        getServer().getPluginManager().registerEvents(maceListener, this);

        var maceCmd = getCommand("mace");
        if (maceCmd != null) maceCmd.setExecutor(new MaceCommand(this));

        var cooldownCmd = getCommand("macecooldown");
        if (cooldownCmd != null) {
            var cmd = new MaceCooldownCommand(maceListener);
            cooldownCmd.setExecutor(cmd);
            cooldownCmd.setTabCompleter(cmd);
        }

        var reloadCmd = getCommand("macereload");
        if (reloadCmd != null) reloadCmd.setExecutor((sender, cmd, label, args) -> {
            reloadConfig();
            maceListener.reloadSettings();
            sender.sendMessage(Component.text("[ShieldsSMP] Config reloaded.", NamedTextColor.GREEN));
            return true;
        });

        getLogger().info("╔══════════════════════════════╗");
        getLogger().info("║   Shields SMP Mace ENABLED   ║");
        getLogger().info("║  Dash | 3-Hit Launch | Wave   ║");
        getLogger().info("╚══════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        getLogger().info("[ShieldsSMP] Disabled.");
    }

    public static ShieldsSMP getInstance() { return instance; }
    public NamespacedKey getMaceKey()      { return maceKey; }
}
