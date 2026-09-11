package net.jifenyouxi.item;

import net.jifenyouxi.JifenyouxiMod;
import net.jifenyouxi.block.ModBlocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item CUSTOM_FISH = new CustomFishItem(
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "custom_fish")))
                    .maxCount(64)
    );

    public static final Item COLLECTIBLE_CARD = new CardItem(
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "collectible_card")))
                    .maxCount(16)
    );

    public static final Item CARD_TABLE_ITEM = new BlockItem(
            ModBlocks.CARD_TABLE_BLOCK,
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "card_table")))
    );

    public static final Item PUZZLE_BLOCK_ITEM = new BlockItem(
            ModBlocks.PUZZLE_BLOCK,
            new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "puzzle_block")))
    );

    public static void registerItems() {
        Registry.register(Registries.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "custom_fish"), CUSTOM_FISH);
        Registry.register(Registries.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "collectible_card"), COLLECTIBLE_CARD);

        Registry.register(Registries.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "card_table"), CARD_TABLE_ITEM);
        Registry.register(Registries.ITEM, Identifier.of(JifenyouxiMod.MOD_ID, "puzzle_block"), PUZZLE_BLOCK_ITEM);
    }
}
