package net.jifenyouxi.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.jifenyouxi.database.DatabaseManager;
import net.jifenyouxi.item.CardItem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 6. 每日宝箱：每个 MC 游戏日于世界出生点刷新神秘宝箱，首开玩家获大奖
 */
public class DailyChestHandler {
    private static BlockPos currentChestPos = null;
    private static boolean claimedToday = false;
    private static long lastSpawnedDay = -1;
    private static final Random RANDOM = new Random();

    public static void register() {
        UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) -> {
            if (hand != Hand.MAIN_HAND || world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            BlockPos hitPos = hitResult.getBlockPos();
            if (currentChestPos != null && hitPos.equals(currentChestPos) && !claimedToday) {
                claimedToday = true;

                // 发放奖励：150~300积分 + 稀有物品
                int points = 150 + RANDOM.nextInt(151);
                DatabaseManager.addPoints(serverPlayer.getUuid(), points, "每日出生点幸运宝箱");

                ItemStack prizeItem = switch (RANDOM.nextInt(3)) {
                    case 0 -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
                    case 1 -> new ItemStack(Items.NETHERITE_INGOT);
                    default -> CardItem.createCard("宝箱守护者之魂", "SSR", 999, Formatting.LIGHT_PURPLE);
                };

                if (!serverPlayer.getInventory().insertStack(prizeItem)) {
                    serverPlayer.dropItem(prizeItem, false);
                }

                // 特效与音效
                ServerWorld sw = serverPlayer.getServerWorld();
                sw.playSound(null, hitPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                sw.spawnParticles(ParticleTypes.FIREWORK, hitPos.getX() + 0.5, hitPos.getY() + 1.2, hitPos.getZ() + 0.5, 50, 0.5, 0.5, 0.5, 0.15);

                // 全服广播
                Text broadcast = Text.literal("🎁 [每日宝箱] 玩家 ")
                        .append(Text.literal(serverPlayer.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD))
                        .append(Text.literal(" 第一个开启了出生点宝箱！斩获 "))
                        .append(Text.literal(points + " 积分").formatted(Formatting.YELLOW, Formatting.BOLD))
                        .append(Text.literal(" 与丰厚神秘大礼！"));
                serverPlayer.getServer().getPlayerManager().broadcast(broadcast, false);

                return ActionResult.PASS;
            }

            return ActionResult.PASS;
        });
    }

    public static void checkAndSpawnDailyChest(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world == null) return;

        long currentDay = world.getTimeOfDay() / 24000L;
        if (currentDay != lastSpawnedDay) {
            lastSpawnedDay = currentDay;
            claimedToday = false;

            // 放置在出生点正上方安全位置
            BlockPos spawn = world.getSpawnPos();
            currentChestPos = spawn.up();
            world.setBlockState(currentChestPos, Blocks.CHEST.getDefaultState());

            // 广播新宝箱刷新
            server.getPlayerManager().broadcast(
                    Text.literal("📦 [每日宝箱] 新的一天来临，出生点已刷新今日神秘宝箱！首位开启者有大奖！").formatted(Formatting.GOLD, Formatting.BOLD),
                    false
            );
        }
    }
}
