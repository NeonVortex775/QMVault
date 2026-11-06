package com.qingming.vault.data;

import com.qingming.vault.util.Text;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** One stack in a vault: a prototype item (always amount 1) and a count. */
public final class Entry {

    private final ItemStack proto;
    private long count;

    public Entry(ItemStack proto, long count) {
        ItemStack p = proto.clone();
        p.setAmount(1);
        this.proto = p;
        this.count = Math.max(0, count);
    }

    public boolean matches(ItemStack other) {
        return other != null && proto.isSimilar(other);
    }

    public ItemStack proto() {
        return proto;
    }

    public long count() {
        return count;
    }

    public void add(long n) {
        count += n;
    }

    public long take(long want) {
        long real = Math.min(Math.max(0, want), count);
        count -= real;
        return real;
    }

    public boolean empty() {
        return count <= 0;
    }

    public ItemStack icon() {
        ItemStack it = proto.clone();
        it.setAmount(1);
        return it;
    }

    public List<String> tipLines() {
        return tipLines(proto);
    }

    public static List<String> tipLines(ItemStack it) {
        List<String> out = new ArrayList<String>();
        if (it == null || it.getType() == null || it.getType() == org.bukkit.Material.AIR) {
            return out;
        }
        out.add(nameOf(it));
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                out.add(line == null ? "" : line);
            }
        }
        return out;
    }

    public String name() {
        return nameOf(proto);
    }

    private static String nameOf(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return "\u00a7f" + prettyType(it);
    }

    private static String prettyType(ItemStack it) {
        String n = it.getType().name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : n.split(" ")) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
