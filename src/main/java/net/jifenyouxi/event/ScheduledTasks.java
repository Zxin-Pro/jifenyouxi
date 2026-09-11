package net.jifenyouxi.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jifenyouxi.database.DatabaseManager;
import net.jifenyouxi.service.LeaderboardService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

/**
 * 定时任务调度器：使用 ServerTickEvents.END_SERVER_TICK 实现周期性检查与事件广播
 */
public class ScheduledTasks {
    private static long tickCounter = 0;
    private static long lastCalculatedDay = -1;
    private static final Random RANDOM = new Random();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ScheduledTasks::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;

        // 1. 每 5 秒检测一次 MC 日期变更（每日刷新与利息结算）
        if (tickCounter % 100 == 0) {
            if (server.getOverworld() != null) {
                long currentDay = server.getOverworld().getTimeOfDay() / 24000L;
                if (currentDay != lastCalculatedDay) {
                    lastCalculatedDay = currentDay;

                    // 刷新出生点宝箱
                    DailyChestHandler.checkAndSpawnDailyChest(server);

                    // 结算所有在线玩家末影箱利息
                    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                        EnderChestBankHandler.distributeDailyInterest(player);
                    }
                }
            }
        }

        // 2. 每分钟 (1200 ticks) 检查一次贷款逾期
        if (tickCounter % 1200 == 0) {
            if (server.getOverworld() != null) {
                long currentDay = server.getOverworld().getTime() / 24000L;
                List<DatabaseManager.LoanRecord> overdue = DatabaseManager.checkAndMarkOverdueLoans(currentDay);

                for (DatabaseManager.LoanRecord r : overdue) {
                    ServerPlayerEntity debtor = server.getPlayerManager().getPlayer(r.uuid());
                    String name = debtor != null ? debtor.getName().getString() : r.uuid().toString().substring(0, 8);

                    // 全服催收通告
                    Text debtNotice = Text.literal("🚨 [银行风控中心] 玩家 ")
                            .append(Text.literal(name).formatted(Formatting.RED, Formatting.BOLD))
                            .append(Text.literal(" 名下一笔金额为 "))
                            .append(Text.literal(String.valueOf(r.amount() + r.interest())).formatted(Formatting.YELLOW))
                            .append(Text.literal(" 积分的贷款已严重逾期！已被纳入失信名单，后续所有收入将被系统自动扣缴还债！"));
                    server.getPlayerManager().broadcast(debtNotice, false);
                }
            }
        }

        // 3. 每 5 分钟 (6000 ticks) 更新告示牌排行
        if (tickCounter % 6000 == 0) {
            LeaderboardService.updateSigns(server);
        }

        // 4. 随机红包 / 天降好运事件 (约每 20 分钟 24000 ticks 随机判定一次)
        if (tickCounter % 24000 == 0) {
            if (RANDOM.nextInt(100) < 50 && !server.getPlayerManager().getPlayerList().isEmpty()) {
                triggerLuckyAirdrop(server);
            }
        }
    }

    private static void triggerLuckyAirdrop(MinecraftServer server) {
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        if (players.isEmpty()) return;

        ServerPlayerEntity lucky = players.get(RANDOM.nextInt(players.size()));
        int bonus = 50 + RANDOM.nextInt(51);
        DatabaseManager.addPoints(lucky.getUuid(), bonus, "天降好运红包");

        Text airdropMsg = Text.literal("🧧 [天降鸿运] 幸运女神降临！在线玩家 ")
                .append(Text.literal(lucky.getName().getString()).formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(" 获得了天降红包，奖励 "))
                .append(Text.literal(bonus + " 积分").formatted(Formatting.YELLOW, Formatting.BOLD))
                .append(Text.literal("！"));
        server.getPlayerManager().broadcast(airdropMsg, false);
    }
}
