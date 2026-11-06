package com.qingming.vault.data;

import com.qingming.vault.VaultPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Old backend: one YAML file per player under data/. */
public final class YamlStorage implements VaultStorage {

    private final VaultPlugin plugin;
    private final File folder;

    public YamlStorage(VaultPlugin plugin, String folderName) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), folderName);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建仓库数据目录: " + folder.getAbsolutePath());
        }
    }

    @Override
    public String describe() {
        return "yml (" + folder.getName() + "/*.yml)";
    }

    @Override
    public void init() {

    }

    public File folder() {
        return folder;
    }

    private File fileOf(UUID id) {
        return new File(folder, id.toString() + ".yml");
    }

    public List<UUID> storedIds() {
        List<UUID> out = new ArrayList<UUID>();
        File[] files = folder.listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            String n = f.getName();
            if (!n.endsWith(".yml")) {
                continue;
            }
            try {
                out.add(UUID.fromString(n.substring(0, n.length() - 4)));
            } catch (IllegalArgumentException ignored) {

            }
        }
        return out;
    }

    @Override
    public void load(VaultData v) {
        File f = fileOf(v.owner());
        if (!f.exists()) {
            return;
        }
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            v.read(cfg);
        } catch (Throwable t) {
            plugin.getLogger().warning("读取仓库失败 " + f.getName() + " -> " + t);
        }
    }

    @Override
    public void save(VaultData v) {
        saveNow(v);
    }

    @Override
    public void saveNow(VaultData v) {
        if (v == null) {
            return;
        }
        File f = fileOf(v.owner());
        YamlConfiguration cfg = new YamlConfiguration();
        v.write(cfg);
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try {
            cfg.save(tmp);
            if (f.exists() && !f.delete()) {
                plugin.getLogger().warning("无法覆盖旧仓库文件: " + f.getName());
            }
            if (!tmp.renameTo(f)) {
                plugin.getLogger().warning("仓库文件改名失败: " + f.getName());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("保存仓库失败 " + f.getName() + " -> " + e);
        }
    }

    @Override
    public void close() {

    }
}
