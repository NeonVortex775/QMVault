package com.qingming.vault.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A player's vault: a list of entries, no fixed slots. */
public final class VaultData {

    private final UUID owner;
    private String ownerName = "";
    private final List<Entry> entries = new ArrayList<Entry>();

    public VaultData(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void ownerName(String name) {
        this.ownerName = name == null ? "" : name;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    public Entry at(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    public long totalCount() {
        long n = 0;
        for (Entry e : entries) {
            n += e.count();
        }
        return n;
    }

    public void addEntry(ItemStack proto, long count) {
        if (proto == null || count <= 0) {
            return;
        }
        entries.add(new Entry(proto, count));
    }

    public int indexOf(ItemStack stack) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).matches(stack)) {
                return i;
            }
        }
        return -1;
    }

    public int store(ItemStack stack, int maxKinds, long maxPerKind) {
        if (stack == null || stack.getType() == null) {
            return 0;
        }
        int i = indexOf(stack);
        if (i >= 0) {
            Entry e = entries.get(i);
            long room = maxPerKind > 0 ? maxPerKind - e.count() : Long.MAX_VALUE;
            if (room <= 0) {
                return 0;
            }
            int n = (int) Math.min(stack.getAmount(), room);
            e.add(n);
            return n;
        }
        if (maxKinds > 0 && entries.size() >= maxKinds) {
            return 0;
        }
        int n = stack.getAmount();
        if (maxPerKind > 0) {
            n = (int) Math.min(n, maxPerKind);
        }
        if (n <= 0) {
            return 0;
        }
        entries.add(new Entry(stack, n));
        return n;
    }

    public long takeAt(int index, long want) {
        Entry e = at(index);
        if (e == null) {
            return 0;
        }
        long real = e.take(want);
        if (e.empty()) {
            entries.remove(index);
        }
        return real;
    }

    public void write(YamlConfiguration cfg) {
        cfg.set("name", ownerName);
        List<java.util.Map<String, Object>> list = new ArrayList<java.util.Map<String, Object>>();
        for (Entry e : entries) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("item", e.proto());
            m.put("count", Long.valueOf(e.count()));
            list.add(m);
        }
        cfg.set("items", list);
    }

    @SuppressWarnings("unchecked")
    public void read(YamlConfiguration cfg) {
        entries.clear();
        ownerName = cfg.getString("name", "");
        List<?> list = cfg.getList("items");
        if (list == null) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof java.util.Map)) {
                continue;
            }
            Object it = ((java.util.Map<?, ?>) o).get("item");
            Object ct = ((java.util.Map<?, ?>) o).get("count");
            if (!(it instanceof ItemStack)) {
                continue;
            }
            long count = ct instanceof Number ? ((Number) ct).longValue() : 1L;
            if (count <= 0) {
                continue;
            }
            entries.add(new Entry((ItemStack) it, count));
        }
    }

    public void readSection(ConfigurationSection sec) {
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            ItemStack it = s.getItemStack("item");
            long count = s.getLong("count", 0L);
            if (it != null && count > 0) {
                entries.add(new Entry(it, count));
            }
        }
    }
}
