package com.qingming.vault.gui;

import com.qingming.vault.VaultPlugin;
import com.qingming.vault.data.Entry;
import com.qingming.vault.data.VaultData;
import com.qingming.vault.util.Text;
import eos.moe.dragoncore.network.PacketSender;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns gui/*.yml into a DragonCore screen and pushes it to the client. */
public final class VaultGui {

    public static final String PACKET = "QingMingVault";

    private static final Pattern CELL = Pattern.compile("^(?:格|物|数)(\\d+)$");
    private static final Pattern INV = Pattern.compile("^(?:背包格|背包物)(\\d+)$");

    public static final int INV_CELLS = 36;

    private VaultGui() {
    }

    public static int invIndex(int n) {
        int i = Math.max(1, n) - 1;
        int row = i / 9;
        int col = i % 9;
        return row < 3 ? 9 + row * 9 + col : col;
    }

    public static boolean open(VaultPlugin plugin, Player p, VaultSession s, GuiTemplate tpl) {
        Render r = render(plugin, p, s, tpl);
        if (r == null) {
            return false;
        }
        try {
            PacketSender.sendYaml(p, "Gui/" + tpl.guiName() + ".yml", r.cfg);
            PacketSender.sendOpenGui(p, tpl.guiName());
        } catch (Throwable t) {
            plugin.getLogger().warning("打开仓库界面失败: " + t);
            return false;
        }
        pushSlots(p, r);
        debug(plugin, tpl, s, r);
        return true;
    }

    public static void update(VaultPlugin plugin, Player p, VaultSession s) {
        GuiTemplate tpl = plugin.guiTemplates().primary();
        if (tpl == null) {
            return;
        }
        Render r = render(plugin, p, s, tpl);
        if (r == null) {
            return;
        }
        try {
            PacketSender.sendUpdateGui(p, r.cfg);
        } catch (Throwable t) {
            plugin.getLogger().warning("刷新仓库界面失败，改回重开: " + t);
            open(plugin, p, s, tpl);
            return;
        }
        pushSlots(p, r);
        debug(plugin, tpl, s, r);
    }

    private static void pushSlots(Player p, Render r) {
        for (Map.Entry<String, ItemStack> en : r.slots.entrySet()) {
            try {
                PacketSender.putClientSlotItem(p, en.getKey(), en.getValue());
            } catch (Throwable ignored) {

            }
        }
    }

    private static void debug(VaultPlugin plugin, GuiTemplate tpl, VaultSession s, Render r) {
        if (!plugin.getConfig().getBoolean("gui.debug", false)) {
            return;
        }
        plugin.getLogger().info("下发界面 gui/" + tpl.file().getName() + " -> " + tpl.guiName()
                + "（第 " + (s.page() + 1) + "/" + r.pages + " 页，" + r.perPage + " 格，"
                + r.kinds + " 种，" + r.slots.size() + " 个槽位物品）");
    }

    private static final class Render {
        YamlConfiguration cfg;
        final Map<String, ItemStack> slots = new LinkedHashMap<String, ItemStack>();
        int perPage;
        int pages;
        int kinds;
    }

    private static Render render(VaultPlugin plugin, Player p, VaultSession s, GuiTemplate tpl) {
        if (tpl == null) {
            return null;
        }
        YamlConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(tpl.file());
        } catch (Throwable t) {
            plugin.getLogger().warning("读取界面模板失败: " + tpl.file().getName() + " -> " + t);
            return null;
        }
        if (cfg.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("界面模板是空的: " + tpl.file().getName());
            return null;
        }

        cfg.set("match", tpl.guiName());

        int cols = tpl.cols() > 0 ? tpl.cols() : plugin.cols();
        int rows = tpl.rows() > 0 ? tpl.rows() : plugin.rows();
        int perPage = Math.max(1, cols * rows);
        int pages = s.pages(perPage);
        if (s.page() >= pages) {
            s.page(pages - 1, perPage);
        }

        VaultData v = s.vault();
        Render r = new Render();
        r.cfg = cfg;
        r.perPage = perPage;
        r.pages = pages;
        r.kinds = v.size();

        Map<String, String> ph = new HashMap<String, String>();
        ph.put("{w}", String.valueOf(tpl.width() > 0 ? tpl.width() : plugin.width()));
        ph.put("{h}", String.valueOf(tpl.height() > 0 ? tpl.height() : plugin.height()));
        ph.put("{page}", String.valueOf(s.page() + 1));
        ph.put("{pages}", String.valueOf(pages));
        ph.put("{kinds}", String.valueOf(v.size()));
        ph.put("{total}", Text.num(v.totalCount()));
        ph.put("{cols}", String.valueOf(cols));
        ph.put("{rows}", String.valueOf(rows));
        ph.put("{per}", String.valueOf(perPage));

        Entry custom = s.customEntry();
        ph.put("{qty}", String.valueOf(s.customQty()));
        ph.put("{name}", custom == null ? "" : custom.name());

        List<String> keys = new ArrayList<String>(cfg.getKeys(false));

        for (String key : keys) {
            Object raw = cfg.get(key);
            if (!(raw instanceof ConfigurationSection)) {
                if (raw instanceof String) {
                    cfg.set(key, apply((String) raw, ph));
                }
                continue;
            }
            ConfigurationSection sec = (ConfigurationSection) raw;

            if (key.startsWith("取数量")) {
                if (s.customIndex() < 0) {
                    sec.set("visible", false);
                    continue;
                }
                if ("取数量数".equals(key)) {
                    sec.set("texts", Arrays.asList("\u00a7e" + s.customQty()));
                }
                replaceIn(sec, ph);
                continue;
            }

            Matcher im = INV.matcher(key);
            if (im.matches()) {
                int n = Integer.parseInt(im.group(1));
                if (n < 1 || n > INV_CELLS) {
                    hide(sec);
                    continue;
                }
                int inv = invIndex(n);
                sec.set("visible", true);
                if (key.startsWith("背包物")) {
                    String ident = "qmv_inv_" + n;
                    sec.set("identifier", ident);
                    r.slots.put(ident, iconOf(p.getInventory().getItem(inv)));
                } else {

                    List<String> tip = Entry.tipLines(p.getInventory().getItem(inv));
                    if (!tip.isEmpty()) {
                        sec.set("tip", tip);
                    }
                }
                Map<String, String> rp = new HashMap<String, String>(ph);
                rp.put("{inv}", String.valueOf(inv));
                rp.put("{cell}", String.valueOf(n));
                replaceIn(sec, rp);
                continue;
            }

            Matcher m = CELL.matcher(key);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(1));
                int index = s.firstIndex(perPage) + n - 1;
                Entry e = n <= perPage ? v.at(index) : null;
                if (e == null) {
                    hide(sec);
                    continue;
                }
                sec.set("visible", true);
                Map<String, String> rp = new HashMap<String, String>(ph);
                rp.put("{index}", String.valueOf(index));
                rp.put("{cell}", String.valueOf(n));
                if (key.startsWith("物")) {
                    String ident = "qmv_slot_" + n;
                    sec.set("identifier", ident);
                    r.slots.put(ident, e.icon());
                } else if (key.startsWith("数")) {
                    sec.set("texts", Arrays.asList("\u00a7e" + Text.shortNum(e.count())));
                }

                if (key.startsWith("格")) {
                    sec.set("tip", e.tipLines());
                }
                if (key.startsWith("格") && s.customIndex() >= 0) {

                    sec.set("actions", null);
                }
                replaceIn(sec, rp);
                continue;
            }

            if ("页码".equals(key)) {
                sec.set("texts", Arrays.asList("\u00a77第 \u00a7e" + (s.page() + 1)
                        + " \u00a77/ \u00a7e" + pages + " \u00a77页"));
            } else if ("上一页".equals(key)) {
                sec.set("visible", s.page() > 0);
            } else if ("下一页".equals(key)) {
                sec.set("visible", s.page() < pages - 1);
            } else if ("种类".equals(key)) {
                sec.set("texts", Arrays.asList("\u00a77种类 \u00a7e" + v.size()));
            } else if ("总数".equals(key)) {
                sec.set("texts", Arrays.asList("\u00a77总数 \u00a7e" + Text.num(v.totalCount())));
            } else if ("空".equals(key)) {
                sec.set("visible", v.size() == 0);
            }
            replaceIn(sec, ph);
        }

        for (int n = 1; n <= perPage; n++) {
            String ident = "qmv_slot_" + n;
            if (!r.slots.containsKey(ident)) {
                r.slots.put(ident, new ItemStack(Material.AIR));
            }
        }
        return r;
    }

    private static ItemStack iconOf(ItemStack it) {
        if (it == null || it.getType() == null || it.getType() == Material.AIR || it.getAmount() <= 0) {
            return new ItemStack(Material.AIR);
        }
        return it.clone();
    }

    private static void hide(ConfigurationSection sec) {
        sec.set("visible", false);
    }

    private static void replaceIn(ConfigurationSection sec, Map<String, String> ph) {
        List<String> keys = new ArrayList<String>(sec.getKeys(true));
        for (String k : keys) {
            Object v = sec.get(k);
            if (v instanceof String) {
                String nv = apply((String) v, ph);
                if (!nv.equals(v)) {
                    sec.set(k, nv);
                }
            } else if (v instanceof List) {
                List<?> list = (List<?>) v;
                boolean changed = false;
                List<Object> out = new ArrayList<Object>();
                for (Object o : list) {
                    if (o instanceof String) {
                        String nv = apply((String) o, ph);
                        if (!nv.equals(o)) {
                            changed = true;
                        }
                        out.add(nv);
                    } else {
                        out.add(o);
                    }
                }
                if (changed) {
                    sec.set(k, out);
                }
            }
        }
    }

    private static String apply(String s, Map<String, String> ph) {
        if (s == null || s.indexOf('{') < 0) {
            return s;
        }
        String out = s;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            if (out.contains(e.getKey())) {
                out = out.replace(e.getKey(), e.getValue());
            }
        }
        return out;
    }
}
