package net.jifenyouxi.block;

import net.jifenyouxi.JifenyouxiMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block CARD_TABLE_BLOCK = new CardTableBlock(
            AbstractBlock.Settings.copy(Blocks.ENCHANTING_TABLE).strength(2.5f)
    );

    public static final Block PUZZLE_BLOCK = new PuzzleBlock(
            AbstractBlock.Settings.copy(Blocks.SEA_LANTERN).strength(1.5f).luminance(state -> 12)
    );

    public static void registerBlocks() {
        Registry.register(Registries.BLOCK, Identifier.of(JifenyouxiMod.MOD_ID, "card_table"), CARD_TABLE_BLOCK);
        Registry.register(Registries.BLOCK, Identifier.of(JifenyouxiMod.MOD_ID, "puzzle_block"), PUZZLE_BLOCK);
    }
}
