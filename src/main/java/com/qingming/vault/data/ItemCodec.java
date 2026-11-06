package com.qingming.vault.data;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** ItemStack <-> Base64 through Bukkit's object stream; enchantments and NBT survive. */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack item) {
        if (item == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
            out.writeObject(item);
            out.flush();
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Throwable t) {
            return "";
        }
    }

    public static ItemStack decode(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(text);
            BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(raw));
            Object o = in.readObject();
            in.close();
            return o instanceof ItemStack ? (ItemStack) o : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
