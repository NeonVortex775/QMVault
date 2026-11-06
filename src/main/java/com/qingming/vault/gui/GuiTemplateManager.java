package com.qingming.vault.gui;

import com.qingming.vault.VaultPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Chooses the template: main: true first, then the first file by name. */
public final class GuiTemplateManager {

    private final VaultPlugin plugin;
    private final List<GuiTemplate> all = new ArrayList<GuiTemplate>();
    private GuiTemplate primary;

    public GuiTemplateManager(VaultPlugin plugin) {
        this.plugin = plugin;
    }

    public File folder() {
        return new File(plugin.getDataFolder(), "gui");
    }

    public void loadAll() {
        all.clear();
        primary = null;
        File dir = folder();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建 gui 目录: " + dir.getAbsolutePath());
            return;
        }

        File def = new File(dir, "默认.yml");
        if (!def.exists()) {
            try {
                plugin.saveResource("gui/默认.yml", false);
            } catch (Throwable ignored) {

            }
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase().endsWith(".yml")) {
                continue;
            }
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
                all.add(new GuiTemplate(f, cfg));
            } catch (Throwable t) {
                plugin.getLogger().warning("gui/" + f.getName() + " 解析失败: " + t);
            }
        }
        for (GuiTemplate t : all) {
            if (t.isMain()) {
                primary = t;
                break;
            }
        }
        if (primary == null && !all.isEmpty()) {
            primary = all.get(0);
        }

        if (all.isEmpty()) {
            plugin.getLogger().info("gui 目录里没有可用模板，使用内置动态界面");
        } else {
            List<String> names = new ArrayList<String>();
            for (GuiTemplate t : all) {
                names.add(t.describe());
            }
            plugin.getLogger().info("已载入 " + names.size() + " 个自定义界面: " + names);
        }
    }

    public GuiTemplate primary() {
        return primary;
    }

    public List<GuiTemplate> all() {
        return new ArrayList<GuiTemplate>(all);
    }
}
