package com.shieldssmp.systems;

import com.shieldssmp.ShieldsSMP;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Central trust-check helper. Trust is one-directional and stored per player
 * in PlayerData. "isTrusted(caster, target)" answers: does caster trust target
 * (i.e. should caster's POSITIVE abilities affect target, and should caster's
 * NEGATIVE abilities SKIP target)?
 */
public class TrustSystem {

    private final ShieldsSMP plugin;

    public TrustSystem(ShieldsSMP plugin) {
        this.plugin = plugin;
    }

    /** True if casterId trusts targetId (or they are the same player) */
    public boolean isTrusted(UUID casterId, UUID targetId) {
        if (casterId.equals(targetId)) return true;
        return plugin.getClassManager().getPlayerData(casterId).isTrusted(targetId);
    }

    public boolean isTrusted(Player caster, Player target) {
        return isTrusted(caster.getUniqueId(), target.getUniqueId());
    }

    /** Toggle trust; returns true if now trusted. */
    public boolean toggleTrust(Player caster, Player target) {
        boolean nowTrusted = plugin.getClassManager().getPlayerData(caster.getUniqueId())
                .toggleTrust(target.getUniqueId());
        plugin.getClassManager().getPlayerData(caster.getUniqueId()).save(plugin);
        return nowTrusted;
    }
}
