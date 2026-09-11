package net.jifenyouxi.block;

import net.jifenyouxi.event.PuzzleChatHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 谜题方块：右键触发随机问题，在聊天栏输入答案
 */
public class PuzzleBlock extends Block {
    public PuzzleBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (PuzzleChatHandler.hasPuzzle(serverPlayer.getUuid())) {
                serverPlayer.sendMessage(Text.literal("⏳ 你当前已有一个未解答的谜题，请先直接在聊天栏输入答案！").formatted(Formatting.YELLOW), false);
                return ActionResult.CONSUME;
            }

            PuzzleChatHandler.ActivePuzzle puzzle = PuzzleChatHandler.createRandomPuzzle(pos);
            PuzzleChatHandler.setPuzzle(serverPlayer.getUuid(), puzzle);

            world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 1.0f);

            serverPlayer.sendMessage(Text.literal("================== 🧩 智慧谜题 ==================").formatted(Formatting.GOLD), false);
            serverPlayer.sendMessage(Text.literal("【题目】: ").formatted(Formatting.AQUA)
                    .append(Text.literal(puzzle.question()).formatted(Formatting.WHITE, Formatting.BOLD)), false);
            serverPlayer.sendMessage(Text.literal("【奖励】: ").formatted(Formatting.YELLOW)
                    .append(Text.literal(puzzle.reward() + " 积分")), false);
            serverPlayer.sendMessage(Text.literal("💡 请在 60 秒内直接在聊天栏发送你的答案！答错方块将自毁！").formatted(Formatting.GRAY), false);
            serverPlayer.sendMessage(Text.literal("================================================").formatted(Formatting.GOLD), false);
        }

        return ActionResult.SUCCESS;
    }
}
