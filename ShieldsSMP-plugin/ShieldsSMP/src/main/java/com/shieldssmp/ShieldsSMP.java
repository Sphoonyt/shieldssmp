package com.shieldssmp;

import com.shieldssmp.commands.MaceCommand;
import com.shieldssmp.listeners.MaceListener;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShieldsSMP extends JavaPlugin {

    // PDC key used to identify Shields SMP Mace items
    public static final String MACE_KEY_ID = "shields_smp_mace";

    private static ShieldsSMP instance;
    private NamespacedKey maceKey;
    private MaceListener maceListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        maceKey = new NamespacedKey(this, MACE_KEY_ID);

        // Register events
        maceListener = new MaceListener(this);
        getServer().getPluginManager().registerEvents(maceListener, this);

        // Register commands
        var maceCmd = getCommand("mace");
        if (maceCmd != null) maceCmd.setExecutor(new MaceCommand(this));

        var reloadCmd = getCommand("macereload");
        if (reloadCmd != null) reloadCmd.setExecutor((sender, cmd, label, args) -> {
            reloadConfig();
            maceListener.reloadSettings();
            sender.sendMessage(net.kyori.adventure.text.Component.text(
                    "[ShieldsSMP] Config reloaded.")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
            return true;
        });

        getLogger().info("╔══════════════════════════════╗");
        getLogger().info("║   Shields SMP Mace ENABLED   ║");
        getLogger().info("║  Dash | Windburst | Launch    ║");
        getLogger().info("╚══════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        getLogger().info("[ShieldsSMP] Disabled.");
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public static ShieldsSMP getInstance() {
        return instance;
    }

    public NamespacedKey getMaceKey() {
        return maceKey;
    }
}
