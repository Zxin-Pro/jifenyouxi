package net.jifenyouxi.block;

import net.jifenyouxi.gui.GamblerScreenHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 幸运赌桌方块：右键打开赌博 GUI（转盘 / 水果机 / 刮刮乐 / 抽卡）
 */
public class GamblingTableBlock extends Block {
    public GamblingTableBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inv, p) -> new GamblerScreenHandler(syncId, inv),
                    Text.literal("🎲 幸运赌桌 - 娱乐大厅")
            ));
        }

        return ActionResult.SUCCESS;
    }
}
