package net.jifenyouxi.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.jifenyouxi.JifenyouxiMod;
import net.jifenyouxi.database.DatabaseManager;
import net.jifenyouxi.item.CardItem;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
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
import net.minecraft.world.WorldProperties;

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
                ServerWorld sw = (ServerWorld) serverPlayer.getEntityWorld();
                sw.playSound(null, hitPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                sw.spawnParticles(ParticleTypes.FIREWORK, hitPos.getX() + 0.5, hitPos.getY() + 1.2, hitPos.getZ() + 0.5, 50, 0.5, 0.5, 0.5, 0.15);

                // 全服广播
                Text broadcast = Text.literal("🎁 [每日宝箱] 玩家 ")
                        .append(Text.literal(serverPlayer.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD))
                        .append(Text.literal(" 第一个开启了出生点宝箱！斩获 "))
                        .append(Text.literal(points + " 积分").formatted(Formatting.YELLOW, Formatting.BOLD))
                        .append(Text.literal(" 与丰厚神秘大礼！"));
                sw.getServer().getPlayerManager().broadcast(broadcast, false);

                return ActionResult.PASS;
            }

            return ActionResult.PASS;
        });
    }

    public static void checkAndSpawnDailyChest(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world == null) return;

        long currentDay = world.getTimeOfDay() / 24000L;
        if (currentDay == lastSpawnedDay) return;

        lastSpawnedDay = currentDay;
        claimedToday = false;

        // 清理昨日未被开启的旧宝箱
        if (currentChestPos != null) {
            world.removeBlock(currentChestPos, false);
            currentChestPos = null;
        }

        // 读取真实的世界出生点坐标 (替代旧版硬编码 0,70,0)
        WorldProperties props = world.getLevelProperties();
        BlockPos spawn = new BlockPos(props.getSpawnX(), props.getSpawnY(), props.getSpawnZ());

        // 放置宝箱并清理上方遮挡，确保任何地形下都可见可点
        currentChestPos = spawn;
        world.setBlockState(spawn, Blocks.CHEST.getDefaultState());
        world.setBlockState(spawn.up(), Blocks.AIR.getDefaultState());
        world.setBlockState(spawn.up(2), Blocks.AIR.getDefaultState());

        // 在宝箱内预填基础战利品，避免开箱空空如也
        if (world.getBlockEntity(spawn) instanceof ChestBlockEntity chest) {
            chest.setStack(4, new ItemStack(Items.GOLD_INGOT, 3 + RANDOM.nextInt(6)));
            chest.setStack(12, new ItemStack(Items.EMERALD, 2 + RANDOM.nextInt(5)));
            chest.setStack(22, new ItemStack(Items.EXPERIENCE_BOTTLE, 4 + RANDOM.nextInt(8)));
            chest.markDirty();
        }

        JifenyouxiMod.LOGGER.info("每日宝箱已刷新于出生点: X={} Y={} Z={}", spawn.getX(), spawn.getY(), spawn.getZ());

        // 广播新宝箱刷新（附上精确坐标提示）
        server.getPlayerManager().broadcast(
                Text.literal("📦 [每日宝箱] 新的一天来临！今日神秘宝箱已刷新在出生点 ")
                        .append(Text.literal("[X=" + spawn.getX() + " Y=" + spawn.getY() + " Z=" + spawn.getZ() + "]").formatted(Formatting.AQUA))
                        .append(Text.literal("，首位开启者有大奖！")).formatted(Formatting.GOLD, Formatting.BOLD),
                false
        );
    }
}
