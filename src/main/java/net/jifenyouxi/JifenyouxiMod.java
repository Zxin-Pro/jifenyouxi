package net.jifenyouxi;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.jifenyouxi.block.ModBlocks;
import net.jifenyouxi.database.DatabaseManager;
import net.jifenyouxi.entity.ModVillagers;
import net.jifenyouxi.event.*;
import net.jifenyouxi.item.ModItems;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class JifenyouxiMod implements ModInitializer {
    public static final String MOD_ID = "jifenyouxi";
    public static final Logger LOGGER = LoggerFactory.getLogger("JifenyouxiMod");

    @Override
    public void onInitialize() {
        LOGGER.info("正在初始化【积分游戏】Fabric模组...");

        // 1. 注册方块与物品
        ModBlocks.registerBlocks();
        ModItems.registerItems();

        // 2. 注册各功能事件监听
        BedCheckInHandler.register();
        EnderChestBankHandler.register();
        DailyChestHandler.register();
        ModVillagers.registerVillagerEvents();
        ScheduledTasks.register();

        // 3. 拦截答题聊天消息
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (PuzzleChatHandler.hasPuzzle(sender.getUuid())) {
                boolean handled = PuzzleChatHandler.handleChat(sender, message.getContent().getString());
                if (handled) {
                    return false; // 拦截消息，不向全服原样广播谜底
                }
            }
            return true;
        });

        // 4. 服务端生命周期管理 (初始化 SQLite 数据库)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            File worldDir = server.getSavePath(WorldSavePath.ROOT).toFile();
            LOGGER.info("正在加载服务端存档目录: {}", worldDir.getAbsolutePath());
            DatabaseManager.initialize(worldDir);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LOGGER.info("正在保存并关闭积分数据库...");
            DatabaseManager.close();
        });

        // 5. 辅助指令支持 (/jifen 与管理员调分)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("jifen")
                    .executes(context -> {
                        if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                            int bal = DatabaseManager.getBalance(player.getUuid());
                            int streak = DatabaseManager.getSignStreak(player.getUuid());
                            player.sendMessage(Text.literal("💰 [积分资产] 当前余额: ")
                                    .append(Text.literal(String.valueOf(bal)).formatted(Formatting.GOLD, Formatting.BOLD))
                                    .append(Text.literal(" | 连续签到: "))
                                    .append(Text.literal(streak + " 天").formatted(Formatting.AQUA)), false);
                        }
                        return 1;
                    })
                    .then(CommandManager.literal("add")
                            .requires(source -> source.hasPermission(2))
                            .then(CommandManager.argument("player", EntityArgumentType.player())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                int amt = IntegerArgumentType.getInteger(context, "amount");
                                                DatabaseManager.addPoints(target.getUuid(), amt, "管理员指令增加");
                                                context.getSource().sendFeedback(() -> Text.literal("✅ 已成功给玩家 ")
                                                        .append(Text.literal(target.getName().getString()).formatted(Formatting.AQUA))
                                                        .append(Text.literal(" 增加 "))
                                                        .append(Text.literal(String.valueOf(amt)).formatted(Formatting.GOLD, Formatting.BOLD))
                                                        .append(Text.literal(" 积分。")), false);
                                                target.sendMessage(Text.literal("📨 管理员为你增加了 ")
                                                        .append(Text.literal(String.valueOf(amt)).formatted(Formatting.GOLD, Formatting.BOLD))
                                                        .append(Text.literal(" 积分！")), false);
                                                return 1;
                                            })
                                    )
                            )
                    )
                    .then(CommandManager.literal("pay")
                            .then(CommandManager.argument("player", EntityArgumentType.player())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                if (!(context.getSource().getEntity() instanceof ServerPlayerEntity sender)) {
                                                    context.getSource().sendError(Text.literal("该指令只能由玩家在游戏内使用。"));
                                                    return 0;
                                                }
                                                ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                int amt = IntegerArgumentType.getInteger(context, "amount");
                                                if (sender.getUuid().equals(target.getUuid())) {
                                                    context.getSource().sendError(Text.literal("❌ 不能给自己转账！"));
                                                    return 0;
                                                }
                                                DatabaseManager.ensurePlayer(target.getUuid(), target.getName().getString());
                                                if (DatabaseManager.deductPoints(sender.getUuid(), amt, "转账支出")) {
                                                    DatabaseManager.addPoints(target.getUuid(), amt, "转账收入");
                                                    context.getSource().sendFeedback(() -> Text.literal("✅ 转账成功！已向 ")
                                                            .append(Text.literal(target.getName().getString()).formatted(Formatting.AQUA))
                                                            .append(Text.literal(" 支付 "))
                                                            .append(Text.literal(String.valueOf(amt)).formatted(Formatting.GOLD, Formatting.BOLD))
                                                            .append(Text.literal(" 积分。")), false);
                                                    target.sendMessage(Text.literal("💰 收到玩家 ")
                                                            .append(Text.literal(sender.getName().getString()).formatted(Formatting.AQUA))
                                                            .append(Text.literal(" 转来的 "))
                                                            .append(Text.literal(String.valueOf(amt)).formatted(Formatting.GOLD, Formatting.BOLD))
                                                            .append(Text.literal(" 积分！")), false);
                                                } else {
                                                    context.getSource().sendError(Text.literal("❌ 积分不足，转账失败！当前余额: " + DatabaseManager.getBalance(sender.getUuid())));
                                                }
                                                return 1;
                                            })
                                    )
                            )
                    )
            );
        });

        LOGGER.info("【积分游戏】Fabric模组初始化完成！");
    }
}
