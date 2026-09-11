package net.jifenyouxi.service;

import net.jifenyouxi.JifenyouxiMod;
import net.jifenyouxi.database.DatabaseManager;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 5. 告示牌排行榜服务：在出生点自动生成积分榜告示牌，并定时刷新
 */
public class LeaderboardService {
    private static final Set<BlockPos> REGISTERED_SIGNS = new HashSet<>();
    private static boolean autoPlacedSign = false;

    public static void registerSign(BlockPos pos) {
        REGISTERED_SIGNS.add(pos);
    }

    public static void updateSigns(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world == null) return;

        List<DatabaseManager.PlayerRank> topPlayers = DatabaseManager.getTopPlayers(10);

        BlockPos spawn = world.getLevelProperties().getSpawnPoint().getPos();

        // 若尚未注册任何告示牌，先扫描出生点附近已有告示牌
        if (REGISTERED_SIGNS.isEmpty()) {
            for (BlockPos p : BlockPos.iterate(spawn.add(-15, -10, -15), spawn.add(15, 10, 15))) {
                BlockEntity be = world.getBlockEntity(p);
                if (be instanceof SignBlockEntity sign) {
                    String line0 = sign.getFrontText().getMessage(0, false).getString();
                    if (line0.contains("积分") || line0.contains("榜")) {
                        REGISTERED_SIGNS.add(p.toImmutable());
                    }
                }
            }
        }

        // 没有任何告示牌时，自动在出生点旁生成一块排行榜告示牌（仅一次）
        if (REGISTERED_SIGNS.isEmpty() && !autoPlacedSign) {
            BlockPos signPos = spawn.add(3, 0, 0);
            world.setBlockState(signPos, Blocks.OAK_SIGN.getDefaultState());
            if (world.getBlockEntity(signPos) instanceof SignBlockEntity) {
                REGISTERED_SIGNS.add(signPos.toImmutable());
                autoPlacedSign = true;
                JifenyouxiMod.LOGGER.info("已在出生点自动生成积分排行榜告示牌: X={} Y={} Z={}",
                        signPos.getX(), signPos.getY(), signPos.getZ());
                server.getPlayerManager().broadcast(
                        Text.literal("🏆 [积分榜] 全服积分排行榜告示牌已生成于出生点旁 ")
                                .append(Text.literal("[X=" + signPos.getX() + " Y=" + signPos.getY() + " Z=" + signPos.getZ() + "]").formatted(Formatting.AQUA))
                                .formatted(Formatting.GOLD),
                        false
                );
            }
        }

        // 清理已被破坏的告示牌
        REGISTERED_SIGNS.removeIf(pos -> !(world.getBlockEntity(pos) instanceof SignBlockEntity));

        // 更新全部注册的告示牌
        for (BlockPos pos : REGISTERED_SIGNS) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SignBlockEntity sign) {
                SignText text = sign.getFrontText();
                text = text.withMessage(0, Text.literal("🏆 全服积分排行").formatted(Formatting.GOLD, Formatting.BOLD));

                for (int i = 1; i <= 3; i++) {
                    int rankIdx = i - 1;
                    if (rankIdx < topPlayers.size()) {
                        DatabaseManager.PlayerRank r = topPlayers.get(rankIdx);
                        Formatting color = (i == 1) ? Formatting.YELLOW : (i == 2) ? Formatting.WHITE : Formatting.GOLD;
                        text = text.withMessage(i, Text.literal("#" + i + " " + r.name() + " " + r.balance()).formatted(color));
                    } else {
                        text = text.withMessage(i, Text.literal("#" + i + " 虚位以待").formatted(Formatting.GRAY));
                    }
                }

                sign.setText(text, true);
                sign.markDirty();
                world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
    }
}
