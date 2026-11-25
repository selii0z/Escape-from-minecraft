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
    TRACKER("Shturman", "ak47", "762x39", 30, 36, 16, Items.DIAMOND_CHESTPLATE, 65, .39),
    PMC("Opérateur PMC", "m4a1", "556x45", 30, 38, 19, Items.IRON_CHESTPLATE, 34, .36),
    FOREMAN("Tagilla", "m870", "12g", 5, 28, 23, Items.DIAMOND_CHESTPLATE, 80, .33),
    KILLA("Killa","ak47","762x39",30,34,16,Items.DIAMOND_CHESTPLATE,84,.36),
    RESHALA("Reshala","ak47","762x39",30,30,25,Items.IRON_CHESTPLATE,60,.32),
    GLUKHAR("Glukhar","m4a1","556x45",30,36,20,Items.DIAMOND_CHESTPLATE,88,.32),
    SANITAR("Sanitar","hk_mp5a5","9mm",30,28,24,Items.IRON_CHESTPLATE,90,.34),
    KABAN("Kaban","m249","556x45",60,32,14,Items.DIAMOND_CHESTPLATE,108,.24),
    KOLLONTAY("Kollontay","m870","12g",5,24,25,Items.DIAMOND_CHESTPLATE,85,.35),
    PARTISAN("Partisan","ak47","762x39",30,38,20,Items.IRON_CHESTPLATE,76,.38),
    ZRYACHIY("Zryachiy","ai_awp","338",5,52,48,Items.IRON_CHESTPLATE,110,.28),
    KNIGHT("Knight","m4a1","556x45",30,40,18,Items.DIAMOND_CHESTPLATE,90,.37),
    BIG_PIPE("Big Pipe","m249","556x45",60,32,16,Items.DIAMOND_CHESTPLATE,92,.30),
    BIRDEYE("Birdeye","ai_awp","338",5,52,46,Items.IRON_CHESTPLATE,78,.36),
    CULTIST_PRIEST("Cultist Priest","ak47","762x39",30,28,24,Items.IRON_CHESTPLATE,70,.37),
    WEDGE("The Wedge","m4a1","556x45",30,36,20,Items.DIAMOND_CHESTPLATE,80,.33),
    WEDGE_LABS("The Wedge (Labs)","m4a1","556x45",30,36,18,Items.DIAMOND_CHESTPLATE,90,.34),
    SHADOW_TAGILLA("Shadow of Tagilla","m870","12g",5,24,19,Items.DIAMOND_CHESTPLATE,105,.37),
    VENGEFUL_KILLA("Vengeful Killa","ak47","762x39",30,34,16,Items.DIAMOND_CHESTPLATE,100,.36),
    INFECTED_TAGILLA("Infected Tagilla","m870","12g",5,22,20,Items.DIAMOND_CHESTPLATE,115,.38),
    BLACK_DIVISION("Black Div.","m4a1","556x45",30,36,22,Items.DIAMOND_CHESTPLATE,76,.33);

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
    boolean elite() { return this == MARKSMAN || this == HEAVY || this == LEADER || boss();
    }
    boolean boss() { return this == TRACKER || ordinal()>=FOREMAN.ordinal(); }
}
