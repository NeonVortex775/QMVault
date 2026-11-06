package com.qingming.vault.gui;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** A template out of gui/*.yml - layout hints plus the raw DragonCore YAML. */
public final class GuiTemplate {

    private final File file;
    private final String id;
    private final boolean main;
    private final int width;
    private final int height;
    private final int cols;
    private final int rows;

    public GuiTemplate(File file, YamlConfiguration cfg) {
        this.file = file;
        this.id = file.getName().replaceAll("(?i)\\.yml$", "");
        this.main = cfg.getBoolean("main", false);

        List<Integer> size = cfg.getIntegerList("size");
        if (size.size() >= 2) {
            this.width = Math.max(0, size.get(0).intValue());
            this.height = Math.max(0, size.get(1).intValue());
        } else {
            this.width = 0;
            this.height = 0;
        }
        this.cols = Math.max(0, cfg.getInt("cols", 0));
        this.rows = Math.max(0, cfg.getInt("rows", 0));
    }

    public String guiName() {

        return "qmvault_" + id.toLowerCase(Locale.ROOT);
    }

    public File file() {
        return file;
    }

    public String id() {
        return id;
    }

    public boolean isMain() {
        return main;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public String describe() {
        return file.getName() + (main ? " [主界面]" : "") + (width > 0 ? " " + width + "x" + height : "")
                + (cols > 0 && rows > 0 ? " " + cols + "x" + rows + "格" : "");
    }
}
