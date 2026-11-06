package com.qingming.vault.listener;

import com.qingming.vault.VaultPlugin;
import com.qingming.vault.gui.VaultSession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Drops the session and saves the vault when a player quits. */
public final class VaultListener implements Listener {

    private final VaultPlugin plugin;

    public VaultListener(VaultPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        VaultSession s = plugin.sessions().remove(e.getPlayer().getUniqueId());
        if (s != null) {
            plugin.vaults().markDirty(e.getPlayer().getUniqueId());
        }
        plugin.vaults().unload(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent e) {
        VaultSession s = plugin.sessions().remove(e.getPlayer().getUniqueId());
        if (s != null) {
            plugin.vaults().markDirty(e.getPlayer().getUniqueId());
        }
        plugin.vaults().unload(e.getPlayer().getUniqueId());
    }
}
