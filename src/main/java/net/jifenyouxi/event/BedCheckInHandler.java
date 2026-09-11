package net.jifenyouxi.event;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.jifenyouxi.database.DatabaseManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 1. 床铺签到系统
 * 玩家睡觉起床时自动判定当前 MC 游戏日是否已签到
 */
public class BedCheckInHandler {
    public static void register() {
        EntitySleepEvents.STOP_SLEEPING.register((entity, bedPos) -> {
            if (!(entity instanceof ServerPlayerEntity player) || player.getEntityWorld().isClient()) {
                return;
            }

            ServerWorld sw = (ServerWorld) player.getEntityWorld();
            // 获取当前世界的天数 (MC 天数 = tick / 24000)
            long currentDay = sw.getTimeOfDay() / 24000L;

            int reward = DatabaseManager.bedSignIn(player.getUuid(), player.getName().getString(), currentDay);
            if (reward > 0) {
                int streak = DatabaseManager.getSignStreak(player.getUuid());

                // 播放清脆升级音效
                sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9f, 1.2f);

                player.sendMessage(Text.literal("💤 你睡了一觉，获得 ")
                        .append(Text.literal(String.valueOf(reward)).formatted(Formatting.YELLOW, Formatting.BOLD))
                        .append(Text.literal(" 积分！连续签到 "))
                        .append(Text.literal(String.valueOf(streak)).formatted(Formatting.AQUA, Formatting.BOLD))
                        .append(Text.literal(" 天！")), false);
            }
        });
    }
}
