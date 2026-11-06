package com.qingming.vault.listener;

import com.qingming.vault.VaultPlugin;
import com.qingming.vault.data.Entry;
import com.qingming.vault.data.VaultData;
import com.qingming.vault.gui.VaultSession;
import eos.moe.dragoncore.api.gui.event.CustomPacketEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;

/** Clicks from the custom GUIs arrive here as DragonCore packets. */
public final class VaultPacketListener implements Listener {

    private final VaultPlugin plugin;

    public VaultPacketListener(VaultPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCustomPacket(CustomPacketEvent e) {
        if (e == null || !plugin.guiPackets()) {
            return;
        }
        if (!VaultPlugin.PACKET.equalsIgnoreCase(e.getIdentifier())) {
            return;
        }
        Player p = e.getPlayer();
        if (p == null || !p.isOnline()) {
            return;
        }
        List<String> data = e.getData();
        if (data == null || data.isEmpty()) {
            return;
        }
        VaultSession s = plugin.sessions().get(p.getUniqueId());
        if (s == null) {
            return;
        }

        String action = data.get(0);
        if ("取".equals(action)) {
            take(p, s, num(data, 2, -1), num(data, 1, 1));
        } else if ("全部".equals(action)) {
            int index = num(data, 1, -1);
            Entry en = s.vault().at(index);
            if (en != null) {
                take(p, s, index, en.count());
            }
        } else if ("自定".equals(action)) {
            int index = num(data, 1, -1);
            if (s.vault().at(index) != null) {
                s.customIndex(index);
                refresh(p, s);
            }
        } else if ("调量".equals(action)) {
            if (s.customIndex() < 0) {
                return;
            }
            long delta = num(data, 1, 0);
            s.customQty(s.customQty() + delta, plugin.customMax());
            refresh(p, s);
        } else if ("确定".equals(action)) {
            if (s.customIndex() < 0) {
                return;
            }
            int index = s.customIndex();
            long qty = s.customQty();
            s.clearCustom();
            take(p, s, index, qty);
        } else if ("收起".equals(action)) {
            s.clearCustom();
            refresh(p, s);
        } else if ("存".equals(action)) {

            storeSlot(p, s, num(data, 1, -1), data.size() > 2 && truthy(data.get(2)));
        } else if ("存背包".equals(action)) {
            store(p, s);
        } else if ("翻页".equals(action)) {
            s.page(s.page() + num(data, 1, 0), plugin.perPage());
            refresh(p, s);
        } else if ("关闭".equals(action)) {
            plugin.sessions().remove(p.getUniqueId());
            p.closeInventory();
            return;
        } else {
            return;
        }
    }

    private void take(Player p, VaultSession s, int index, long want) {
        VaultData v = s.vault();
        Entry en = v.at(index);
        if (en == null || want <= 0) {
            p.sendMessage(plugin.msg("take-empty"));
            plugin.sound(p, "fail");
            return;
        }
        long real = Math.min(want, en.count());
        ItemStack proto = en.proto().clone();
        String name = en.name();
        real = v.takeAt(index, real);
        if (real <= 0) {
            p.sendMessage(plugin.msg("take-empty"));
            plugin.sound(p, "fail");
            refresh(p, s);
            return;
        }

        boolean dropped = false;
        long left = real;
        while (left > 0) {
            int n = (int) Math.min(64L, left);
            ItemStack give = proto.clone();
            give.setAmount(n);
            HashMap<Integer, ItemStack> rest = p.getInventory().addItem(give);
            for (ItemStack r : rest.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), r);
                dropped = true;
            }
            left -= n;
        }
        if (dropped) {
            p.sendMessage(plugin.msg("full"));
        }
        p.sendMessage(plugin.msg("took").replace("%amount%", String.valueOf(real)).replace("%item%", name));
        plugin.sound(p, "take");
        plugin.markDirty(p);
        refresh(p, s);
    }

    private void storeSlot(Player p, VaultSession s, int inv, boolean all) {
        if (inv < 0 || inv > 35) {
            return;
        }
        ItemStack it = p.getInventory().getItem(inv);
        if (it == null || it.getType() == Material.AIR || it.getAmount() <= 0) {

            return;
        }
        if (plugin.blocked(it)) {
            p.sendMessage(plugin.msg("blocked"));
            plugin.sound(p, "fail");
            return;
        }

        int want = all ? it.getAmount() : 1;
        ItemStack copy = it.clone();
        copy.setAmount(want);

        VaultData v = s.vault();
        int before = v.size();
        int n = v.store(copy, plugin.maxKinds(), plugin.maxPerKind());
        if (n <= 0) {
            boolean kindsFull = plugin.maxKinds() > 0 && v.size() >= plugin.maxKinds() && before >= v.size();
            p.sendMessage(plugin.msg(kindsFull ? "kinds-full" : "kind-full")
                    .replace("%max%", String.valueOf(plugin.maxKinds()))
                    .replace("%kind%", String.valueOf(plugin.maxPerKind())));
            plugin.sound(p, "fail");
            return;
        }

        int left = it.getAmount() - n;
        if (left <= 0) {
            p.getInventory().setItem(inv, null);
        } else {
            it.setAmount(left);
            p.getInventory().setItem(inv, it);
        }
        if (v.size() > before) {
            plugin.sound(p, "store");
        } else {
            plugin.sound(p, "click");
        }
        plugin.markDirty(p);
        if (n > 1) {
            p.sendMessage(plugin.msg("stored")
                    .replace("%amount%", String.valueOf(n))
                    .replace("%kinds%", String.valueOf(Math.max(0, v.size() - before))));
        }
        refresh(p, s);
    }

    private void store(Player p, VaultSession s) {
        VaultData v = s.vault();
        ItemStack[] contents = p.getInventory().getStorageContents();
        long stored = 0;
        int kinds = 0;
        boolean kindsFull = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() == Material.AIR || it.getAmount() <= 0) {
                continue;
            }
            if (plugin.blocked(it)) {
                continue;
            }
            int before = v.size();
            int n = v.store(it, plugin.maxKinds(), plugin.maxPerKind());
            if (n <= 0) {
                if (v.size() > before) {

                    continue;
                }
                if (plugin.maxKinds() > 0 && v.size() >= plugin.maxKinds()) {
                    kindsFull = true;
                }
                continue;
            }
            if (v.size() > before) {
                kinds++;
            }
            if (n >= it.getAmount()) {
                contents[i] = null;
            } else {
                it.setAmount(it.getAmount() - n);
            }
            stored += n;
        }
        p.getInventory().setStorageContents(contents);

        if (stored <= 0) {
            p.sendMessage(plugin.msg(kindsFull ? "kinds-full" : "store-nothing")
                    .replace("%max%", String.valueOf(plugin.maxKinds())));
            plugin.sound(p, "fail");
            return;
        }
        p.sendMessage(plugin.msg("stored")
                .replace("%amount%", String.valueOf(stored))
                .replace("%kinds%", String.valueOf(kinds)));
        plugin.sound(p, "store");
        plugin.markDirty(p);
        refresh(p, s);
    }

    private void refresh(Player p, VaultSession s) {
        plugin.refresh(p, s);
    }

    private static int num(List<String> data, int i, int def) {
        if (data.size() <= i) {
            return def;
        }
        try {
            return Integer.parseInt(data.get(i).trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static boolean truthy(String s) {
        if (s == null) {
            return false;
        }
        String v = s.trim();
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }
}
