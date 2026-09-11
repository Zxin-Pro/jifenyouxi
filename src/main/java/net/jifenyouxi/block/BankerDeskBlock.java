package net.jifenyouxi.block;

import net.jifenyouxi.gui.BankerScreenHandler;
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
 * 银行柜台方块：右键打开贷款 GUI（借款 / 还款 / 征信）
 */
public class BankerDeskBlock extends Block {
    public BankerDeskBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, inv, p) -> new BankerScreenHandler(syncId, inv),
                    Text.literal("🏦 银行柜台 - 金融服务中心")
            ));
        }

        return ActionResult.SUCCESS;
    }
}
