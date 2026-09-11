package net.jifenyouxi.service;

import net.jifenyouxi.database.DatabaseManager;
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
 * 5. 告示牌排行榜服务：自动发现并刷新出生点附近的积分排行榜告示牌
 */
public class LeaderboardService {
    private static final Set<BlockPos> REGISTERED_SIGNS = new HashSet<>();

    public static void registerSign(BlockPos pos) {
        REGISTERED_SIGNS.add(pos);
    }

    public static void updateSigns(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world == null) return;

        List<DatabaseManager.PlayerRank> topPlayers = DatabaseManager.getTopPlayers(10);

        // 如果没有显式注册的告示牌，在出生点周边 10 格内自动探测带 [积分榜] 标记的告示牌
        if (REGISTERED_SIGNS.isEmpty()) {
            BlockPos spawn = new BlockPos(0, 70, 0);
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

        // 更新全部注册的告示牌
        for (BlockPos pos : REGISTERED_SIGNS) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SignBlockEntity sign) {
                SignText text = sign.getFrontText();
                text = text.withMessage(0, Text.literal("🏆 全服积分排行榜").formatted(Formatting.GOLD, Formatting.BOLD));

                for (int i = 1; i <= 3; i++) {
                    int rankIdx = i - 1;
                    if (rankIdx < topPlayers.size()) {
                        DatabaseManager.PlayerRank r = topPlayers.get(rankIdx);
                        Formatting color = (i == 1) ? Formatting.YELLOW : (i == 2) ? Formatting.WHITE : Formatting.GOLD;
                        text = text.withMessage(i, Text.literal("#" + i + " " + r.name() + ": " + r.balance()).formatted(color));
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
