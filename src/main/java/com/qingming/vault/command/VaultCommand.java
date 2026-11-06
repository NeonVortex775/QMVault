package com.qingming.vault.command;

import com.qingming.vault.VaultPlugin;
import com.qingming.vault.data.VaultData;
import com.qingming.vault.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /vault [open <player>] | reload | info [player] */
public final class VaultCommand implements CommandExecutor, TabCompleter {

    private final VaultPlugin plugin;

    public VaultCommand(VaultPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("\u00a77用法: /" + label + " open <玩家> | reload | info <玩家> | storage | migrate");
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission("qingmingvault.use")) {
                p.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.open(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("qingmingvault.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(plugin.msg("reloaded"));
            return true;
        }
        if ("open".equals(sub)) {
            if (!sender.hasPermission("qingmingvault.open")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("\u00a77用法: /" + label + " open <玩家>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("\u00a7c玩家不在线");
                return true;
            }
            plugin.open(target);
            sender.sendMessage("\u00a7a已为 " + target.getName() + " 打开仓库");
            return true;
        }
        if ("storage".equals(sub)) {
            if (!sender.hasPermission("qingmingvault.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            sender.sendMessage("\u00a76当前存储后端：\u00a7e" + plugin.vaults().storage().describe());
            sender.sendMessage("\u00a77已缓存 \u00a7e" + plugin.vaults().loaded().size() + " \u00a77个仓库，待写 \u00a7e"
                    + plugin.vaults().dirtyIds().size() + " \u00a77个");
            return true;
        }
        if ("migrate".equals(sub)) {
            if (!sender.hasPermission("qingmingvault.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            if (!plugin.vaults().usingDatabase()) {
                sender.sendMessage("\u00a7c当前是 yml 存储，不需要迁移。"
                        + "先把 config.yml 的 storage.type 改成 sqlite 或 mysql 再重启。");
                return true;
            }
            int[] r = plugin.vaults().migrateFromYaml();
            sender.sendMessage("\u00a7a迁移完成：\u00a7e" + r[0] + " \u00a7a个玩家 / \u00a7e" + r[1]
                    + " \u00a7a种物品（已有的玩家不会覆盖）");
            return true;
        }
        if ("info".equals(sub)) {
            if (!sender.hasPermission("qingmingvault.admin")) {
                sender.sendMessage(plugin.msg("no-permission"));
                return true;
            }
            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("\u00a7c玩家不在线");
                    return true;
                }
            } else if (sender instanceof Player) {
                target = (Player) sender;
            } else {
                sender.sendMessage("\u00a77用法: /" + label + " info <玩家>");
                return true;
            }
            VaultData v = plugin.vaults().get(target);
            sender.sendMessage("\u00a76" + target.getName() + " \u00a77的仓库：\u00a7e"
                    + v.size() + " \u00a77种，共 \u00a7e" + Text.num(v.totalCount()) + " \u00a77件");
            sender.sendMessage("\u00a78存储：\u00a77" + plugin.vaults().storage().describe());
            return true;
        }

        sender.sendMessage("\u00a77用法: /" + label + " [open <玩家>|reload|info <玩家>|storage|migrate]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<String>();
            for (String s : Arrays.asList("open", "reload", "info", "storage", "migrate")) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && ("open".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0]))) {
            List<String> out = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(p.getName());
                }
            }
            return out;
        }
        return new ArrayList<String>();
    }
}
