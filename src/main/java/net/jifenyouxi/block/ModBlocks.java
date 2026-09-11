package net.jifenyouxi.block;

import net.jifenyouxi.JifenyouxiMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block CARD_TABLE_BLOCK = new CardTableBlock(
            AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(JifenyouxiMod.MOD_ID, "card_table")))
                    .strength(2.5f)
                    .requiresTool()
    );

    public static final Block PUZZLE_BLOCK = new PuzzleBlock(
            AbstractBlock.Settings.create()
                    .registryKey(RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(JifenyouxiMod.MOD_ID, "puzzle_block")))
                    .strength(1.5f)
                    .luminance(state -> 12)
    );

    public static void registerBlocks() {
        Registry.register(Registries.BLOCK, Identifier.of(JifenyouxiMod.MOD_ID, "card_table"), CARD_TABLE_BLOCK);
        Registry.register(Registries.BLOCK, Identifier.of(JifenyouxiMod.MOD_ID, "puzzle_block"), PUZZLE_BLOCK);
    }
}
