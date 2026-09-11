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
                            .then(CommandManager.argument("player", EntityArgumentType.player())
                                    .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(context -> {
                                                ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                int amt = IntegerArgumentType.getInteger(context, "amount");
                                                DatabaseManager.addPoints(target.getUuid(), amt, "管理员指令增加");
                                                context.getSource().sendMessage(Text.literal("已成功给玩家 " + target.getName().getString() + " 增加 " + amt + " 积分。"));
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
