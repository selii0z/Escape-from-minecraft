package fr.taczoperations.operations;

import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

enum GuardRole {
    RAIDER("Pillard", "glock_17", "9mm", 12, 18, 31, Items.LEATHER_CHESTPLATE, 20, .34),
    SCOUT("Éclaireur", "hk_mp5a5", "9mm", 24, 24, 18, Items.LEATHER_CHESTPLATE, 19, .43),
    SOLDIER("Soldat", "ak47", "762x39", 25, 32, 27, Items.CHAINMAIL_CHESTPLATE, 25, .33),
    MARKSMAN("Tireur d’élite", "ai_awp", "338", 5, 47, 58, Items.CHAINMAIL_CHESTPLATE, 23, .30),
    HEAVY("Soldat lourd", "m870", "12g", 5, 14, 42, Items.IRON_CHESTPLATE, 38, .26),
    LEADER("Chef d’escouade", "m4a1", "556x45", 28, 33, 22, Items.IRON_CHESTPLATE, 33, .35),
    TRACKER("Le Traqueur", "ak47", "762x39", 30, 36, 16, Items.DIAMOND_CHESTPLATE, 65, .39),
    PMC("Opérateur PMC", "m4a1", "556x45", 30, 38, 19, Items.IRON_CHESTPLATE, 34, .36),
    FOREMAN("Le Contremaître", "m870", "12g", 5, 28, 23, Items.DIAMOND_CHESTPLATE, 80, .33);

    private static final String TAG = "taczoperations_guard_role";
    final String title, gun, ammo;
    final int magazine, range, pause, health;
    final Item armor;
    final double speed;

    GuardRole(String title, String gun, String ammo, int magazine, int range, int pause, Item armor, int health, double speed) {
        this.title = title; this.gun = gun; this.ammo = ammo; this.magazine = magazine;
        this.range = range; this.pause = pause; this.armor = armor; this.health = health; this.speed = speed;
    }

    static GuardRole get(Pillager guard) {
        int i = guard.getPersistentData().getInt(TAG);
        return i >= 0 && i < values().length ? values()[i] : RAIDER;
    }

    void assign(Pillager guard) { guard.getPersistentData().putInt(TAG, ordinal()); }
    boolean elite() { return this == MARKSMAN || this == HEAVY || this == LEADER || this == TRACKER || this == FOREMAN;
    }
    boolean boss() { return this == TRACKER || this == FOREMAN; }
}
