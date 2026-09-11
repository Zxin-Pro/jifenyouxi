package net.jifenyouxi.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.jifenyouxi.database.DatabaseManager;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3. 末影箱银行：通过末影箱存入/取出金锭实现银行存取款与 5% 每日结息
 */
public class EnderChestBankHandler {
    // 记录玩家打开末影箱时的金锭数量
    private static final Map<UUID, Integer> PLAYER_GOLD_SNAPSHOT = new ConcurrentHashMap<>();

    public static void register() {
        UseBlockCallback.EVENT.register((PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) -> {
            if (hand != Hand.MAIN_HAND || world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            if (world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.ENDER_CHEST)) {
                EnderChestInventory enderInv = serverPlayer.getEnderChestInventory();
                int currentGold = countGoldIngots(enderInv);
                PLAYER_GOLD_SNAPSHOT.put(serverPlayer.getUuid(), currentGold);

                // 发送提示
                serverPlayer.sendMessage(Text.literal("🏦 [末影箱银行] 已激活！当前箱内存入金锭: ")
                        .append(Text.literal(String.valueOf(currentGold)).formatted(Formatting.GOLD))
                        .append(Text.literal(" 块（每块对应 10 积分，每日享有 5% 复利）")), true);
            }

            return ActionResult.PASS;
        });
    }

    /**
     * 每日利息结算：为在线玩家末影箱直接注入 5% 金锭利息
     */
    public static void distributeDailyInterest(ServerPlayerEntity player) {
        EnderChestInventory enderInv = player.getEnderChestInventory();
        int goldCount = countGoldIngots(enderInv);
        if (goldCount <= 0) return;

        int interestGold = Math.max(1, (int) Math.round(goldCount * 0.05));
        ItemStack interestStack = new ItemStack(Items.GOLD_INGOT, interestGold);

        // 尝试加入末影箱
        ItemStack remaining = enderInv.addStack(interestStack);
        int actualAdded = interestGold - remaining.getCount();

        if (actualAdded > 0) {
            DatabaseManager.addPoints(player.getUuid(), actualAdded * 10, "末影箱存款利息");
            ServerWorld sw = (ServerWorld) player.getEntityWorld();
            sw.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
            player.sendMessage(Text.literal("💰 [末影箱银行] 叮！你的末影箱收到了今日 5% 利息: ")
                    .append(Text.literal(actualAdded + " 块金锭").formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal("，已自动存入末影箱！")), false);
        }
    }

    public static int countGoldIngots(EnderChestInventory inv) {
        int count = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(Items.GOLD_INGOT)) {
                count += stack.getCount();
            } else if (stack.isOf(Items.GOLD_BLOCK)) {
                count += stack.getCount() * 9;
            }
        }
        return count;
    }
}
