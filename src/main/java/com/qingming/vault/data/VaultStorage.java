package com.qingming.vault.data;

/** Storage backend contract. Everything here is called from the main thread. */
public interface VaultStorage {

    String describe();

    void init() throws Exception;

    void load(VaultData v);

    void save(VaultData v);

    void saveNow(VaultData v);

    void close();
}
