package com.qingming.vault;

import com.qingming.vault.command.VaultCommand;
import com.qingming.vault.data.VaultData;
import com.qingming.vault.data.VaultManager;
import com.qingming.vault.gui.GuiTemplate;
import com.qingming.vault.gui.GuiTemplateManager;
import com.qingming.vault.gui.VaultGui;
import com.qingming.vault.gui.VaultSession;
import com.qingming.vault.listener.VaultListener;
import com.qingming.vault.listener.VaultPacketListener;
import com.qingming.vault.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Plugin entry point: storage, sessions, GUI, command. */
public final class VaultPlugin extends JavaPlugin {

    public static final String PACKET = "QingMingVault";

    private VaultManager vaults;
    private GuiTemplateManager guiTemplates;
    private VaultCommand command;

    private final Map<UUID, VaultSession> sessions = new ConcurrentHashMap<UUID, VaultSession>();
    private int saveTask = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        vaults = new VaultManager(this);
        guiTemplates = new GuiTemplateManager(this);
        guiTemplates.loadAll();

        getLogger().info("存储后端: " + vaults.storage().describe());

        if (vaults.usingDatabase() && getConfig().getBoolean("storage.migrate-yml", true)) {
            int[] moved = vaults.migrateFromYaml();
            if (moved[0] > 0) {
                getLogger().info("已把 " + moved[0] + " 个玩家的旧 yml 仓库导入数据库（共 "
                        + moved[1] + " 种物品），原文件保留在 data/ 里没删");
            }
        }

        command = new VaultCommand(this);
        if (getCommand("vault") != null) {
            getCommand("vault").setExecutor(command);
            getCommand("vault").setTabCompleter(command);
        }
        Bukkit.getPluginManager().registerEvents(new VaultListener(this), this);
        Bukkit.getPluginManager().registerEvents(new VaultPacketListener(this), this);

        final VaultPlugin self = this;
        int period = Math.max(20, getConfig().getInt("autosave-seconds", 60) * 20);
        saveTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                self.vaults.flushDirty();
            }
        }, period, period).getTaskId();

        if (Bukkit.getPluginManager().getPlugin("DragonCore") == null) {
            getLogger().warning("未找到 DragonCore —— 仓库界面无法打开（本插件依赖龙核渲染）");
        }
        getLogger().info("青冥仓库已启用（每页 " + perPage() + " 格" + (guiTemplates.primary() == null
                ? "，没有自定义界面" : "，界面模板 " + guiTemplates.primary().describe()) + "）");
    }

    @Override
    public void onDisable() {
        if (saveTask != -1) {
            Bukkit.getScheduler().cancelTask(saveTask);
        }
        sessions.clear();
        if (vaults != null) {

            vaults.close();
        }
    }

    public VaultSession open(Player p) {
        VaultData v = vaults.get(p);
        VaultSession s = new VaultSession(p.getUniqueId(), v);
        sessions.put(p.getUniqueId(), s);
        GuiTemplate tpl = guiTemplates.primary();
        if (tpl == null) {
            p.sendMessage(msg("no-template"));
            return s;
        }
        if (!VaultGui.open(this, p, s, tpl)) {
            p.sendMessage(msg("no-template"));
            sessions.remove(p.getUniqueId());
            return null;
        }
        return s;
    }

    public void refresh(Player p, VaultSession s) {
        if (p == null || s == null) {
            return;
        }
        if (guiTemplates.primary() != null) {
            VaultGui.update(this, p, s);
        }
    }

    public VaultSession session(Player p) {
        return sessions.get(p.getUniqueId());
    }

    public Map<UUID, VaultSession> sessions() {
        return sessions;
    }

    public int width() {
        return getConfig().getInt("gui.width", 760);
    }

    public int height() {
        return getConfig().getInt("gui.height", 440);
    }

    public int cols() {
        return Math.max(1, getConfig().getInt("gui.cols", 8));
    }

    public int rows() {
        return Math.max(1, getConfig().getInt("gui.rows", 4));
    }

    public int perPage() {
        GuiTemplate tpl = guiTemplates == null ? null : guiTemplates.primary();
        int c = tpl != null && tpl.cols() > 0 ? tpl.cols() : cols();
        int r = tpl != null && tpl.rows() > 0 ? tpl.rows() : rows();
        return Math.max(1, c * r);
    }

    public int maxKinds() {
        return getConfig().getInt("vault.max-kinds", 240);
    }

    public long maxPerKind() {
        return getConfig().getLong("vault.max-per-kind", 0L);
    }

    public long customMax() {
        return getConfig().getLong("vault.custom-max", 2304L);
    }

    public boolean guiPackets() {
        return getConfig().getBoolean("gui.custom-enabled", true);
    }

    public VaultManager vaults() {
        return vaults;
    }

    public GuiTemplateManager guiTemplates() {
        return guiTemplates;
    }

    public VaultCommand command() {
        return command;
    }

    public void markDirty(Player p) {
        if (vaults != null && p != null) {
            vaults.markDirty(p.getUniqueId());
        }
    }

    public void reload() {
        reloadConfig();
        guiTemplates.loadAll();
    }

    public boolean blocked(ItemStack it) {
        if (it == null) {
            return true;
        }
        List<String> list = getConfig().getStringList("blacklist");
        if (list == null || list.isEmpty()) {
            return false;
        }
        String name = it.getType().name();
        for (String raw : list) {
            if (raw == null) {
                continue;
            }
            String s = raw.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (s.endsWith("*")) {
                if (name.startsWith(s.substring(0, s.length() - 1))) {
                    return true;
                }
            } else if (s.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public String msg(String key) {
        String prefix = Text.color(getConfig().getString("messages.prefix", ""));
        String s = getConfig().getString("messages." + key);
        if (s == null) {
            return prefix;
        }
        return prefix + Text.color(s);
    }

    public void sound(Player p, String kind) {
        if (p == null) {
            return;
        }
        Sound s;
        try {
            if ("store".equals(kind)) {
                s = Sound.BLOCK_CHEST_CLOSE;
            } else if ("take".equals(kind)) {
                s = Sound.ENTITY_ITEM_PICKUP;
            } else if ("fail".equals(kind)) {
                s = Sound.ENTITY_VILLAGER_NO;
            } else if ("click".equals(kind)) {
                s = Sound.UI_BUTTON_CLICK;
            } else {
                s = Sound.UI_BUTTON_CLICK;
            }
        } catch (Throwable t) {
            return;
        }
        p.playSound(p.getLocation(), s, 0.7f, "take".equals(kind) ? 1.2f : 1.0f);
    }
}
