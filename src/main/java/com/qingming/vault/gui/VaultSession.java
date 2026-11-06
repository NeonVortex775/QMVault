package com.qingming.vault.gui;

import com.qingming.vault.data.Entry;
import com.qingming.vault.data.VaultData;

import java.util.UUID;

/** Per-player UI state while the vault is open: page, and the stack being edited. */
public final class VaultSession {

    private final UUID player;
    private final VaultData vault;
    private int page;

    private int customIndex = -1;
    private long customQty = 1;

    public VaultSession(UUID player, VaultData vault) {
        this.player = player;
        this.vault = vault;
    }

    public UUID player() {
        return player;
    }

    public VaultData vault() {
        return vault;
    }

    public int page() {
        return page;
    }

    public void page(int p, int perPage) {
        int pages = pages(perPage);
        this.page = Math.max(0, Math.min(pages - 1, p));
    }

    public int pages(int perPage) {
        int per = Math.max(1, perPage);
        int size = vault.size();
        return Math.max(1, (size + per - 1) / per);
    }

    public int firstIndex(int perPage) {
        return page * Math.max(1, perPage);
    }

    public int customIndex() {
        return customIndex;
    }

    public Entry customEntry() {
        return vault.at(customIndex);
    }

    public void customIndex(int index) {
        this.customIndex = index;
        Entry e = vault.at(index);
        this.customQty = e == null ? 1 : Math.min(64, e.count());
        if (this.customQty <= 0) {
            this.customQty = 1;
        }
    }

    public void clearCustom() {
        this.customIndex = -1;
    }

    public long customQty() {
        return customQty;
    }

    public void customQty(long q, long max) {
        long cap = max > 0 ? Math.min(max, customEntryMax()) : customEntryMax();
        this.customQty = Math.max(0, Math.min(cap, q));
    }

    private long customEntryMax() {
        Entry e = customEntry();
        return e == null ? 0 : e.count();
    }
}
