package com.qingming.vault.data;

import com.qingming.vault.VaultPlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory vault cache and the write-back schedule. */
public final class VaultManager {

    private final VaultPlugin plugin;
    private final VaultStorage storage;
    private final String ymlFolder;
    private final Map<UUID, VaultData> cache = new ConcurrentHashMap<UUID, VaultData>();
    private final Map<UUID, Long> dirty = new ConcurrentHashMap<UUID, Long>();

    public VaultManager(VaultPlugin plugin) {
        this.plugin = plugin;
        this.ymlFolder = plugin.getConfig().getString("data-folder", "data");

        String want = plugin.getConfig().getString("storage.type", "sqlite");
        if (want == null || want.trim().isEmpty()) {
            want = "sqlite";
        }
        want = want.trim().toLowerCase();

        VaultStorage created = null;
        if ("yml".equals(want) || "yaml".equals(want) || "file".equals(want)) {
            created = new YamlStorage(plugin, ymlFolder);
        } else {
            created = new SqlStorage(plugin, want);
            try {
                created.init();
            } catch (Throwable t) {
                plugin.getLogger().severe("数据库初始化失败（storage.type: " + want + "）-> " + t);
                plugin.getLogger().severe("已自动降级为 yml 存储，数据不会丢；修好配置后重启即可切回。");
                try {
                    created.close();
                } catch (Throwable ignored) {

                }
                created = new YamlStorage(plugin, ymlFolder);
            }
        }

        this.storage = created;
    }

    public VaultStorage storage() {
        return storage;
    }

    public boolean usingDatabase() {
        return storage instanceof SqlStorage;
    }

    public VaultData get(Player p) {
        return get(p.getUniqueId(), p.getName());
    }

    public VaultData get(UUID id, String name) {
        VaultData v = cache.get(id);
        if (v != null) {
            if (name != null && !name.isEmpty()) {
                v.ownerName(name);
            }
            return v;
        }
        v = new VaultData(id);
        v.ownerName(name == null ? "" : name);
        try {
            storage.load(v);
        } catch (Throwable t) {
            plugin.getLogger().warning("读取仓库失败 " + id + " -> " + t);
        }
        if (v.ownerName().isEmpty() && name != null) {
            v.ownerName(name);
        }
        cache.put(id, v);
        return v;
    }

    public void markDirty(UUID id) {
        dirty.put(id, Long.valueOf(System.currentTimeMillis()));
    }

    public void save(VaultData v) {
        if (v == null) {
            return;
        }
        try {
            storage.save(v);
            dirty.remove(v.owner());
        } catch (Throwable t) {
            plugin.getLogger().warning("保存仓库失败 " + v.owner() + " -> " + t);
        }
    }

    public int flushDirty() {
        int n = 0;
        for (UUID id : new ArrayList<UUID>(dirty.keySet())) {
            VaultData v = cache.get(id);
            if (v != null) {
                save(v);
                n++;
            } else {
                dirty.remove(id);
            }
        }
        return n;
    }

    public void saveAll() {
        for (VaultData v : cache.values()) {
            try {
                storage.saveNow(v);
            } catch (Throwable t) {
                plugin.getLogger().warning("保存仓库失败 " + v.owner() + " -> " + t);
            }
        }
        dirty.clear();
    }

    public void unload(UUID id) {
        VaultData v = cache.remove(id);
        if (v != null) {
            try {
                storage.saveNow(v);
            } catch (Throwable t) {
                plugin.getLogger().warning("保存仓库失败 " + id + " -> " + t);
            }
        }
        dirty.remove(id);
    }

    public Collection<VaultData> loaded() {
        return cache.values();
    }

    public List<UUID> dirtyIds() {
        return new ArrayList<UUID>(dirty.keySet());
    }

    public void put(VaultData v) {
        if (v != null) {
            cache.put(v.owner(), v);
        }
    }

    public void close() {
        saveAll();
        try {
            storage.close();
        } catch (Throwable t) {
            plugin.getLogger().warning("关闭存储后端失败 -> " + t);
        }
    }

    public int[] migrateFromYaml() {
        int players = 0;
        int kinds = 0;
        if (!usingDatabase()) {
            return new int[]{0, 0};
        }
        YamlStorage src = new YamlStorage(plugin, ymlFolder);
        List<UUID> ids = src.storedIds();
        for (UUID id : ids) {
            try {
                VaultData exist = new VaultData(id);
                storage.load(exist);
                if (exist.size() > 0) {
                    continue;
                }
                VaultData tmp = new VaultData(id);
                src.load(tmp);
                if (tmp.size() == 0) {
                    continue;
                }
                if (tmp.ownerName().isEmpty()) {
                    tmp.ownerName(exist.ownerName());
                }
                storage.saveNow(tmp);
                put(tmp);
                players++;
                kinds += tmp.size();
            } catch (Throwable t) {
                plugin.getLogger().warning("迁移失败 " + id + " -> " + t);
            }
        }
        return new int[]{players, kinds};
    }
}
