package net.jifenyouxi.block;

import net.jifenyouxi.database.DatabaseManager;
import net.jifenyouxi.item.CardItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 抽卡台方块：右键消耗 10 积分抽取卡牌
 */
public class CardTableBlock extends Block {
    private static final int DRAW_COST = 10;
    private static final Random RANDOM = new Random();

    public CardTableBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }

        if (player instanceof ServerPlayerEntity serverPlayer) {
            int balance = DatabaseManager.getBalance(serverPlayer.getUuid());
            if (balance < DRAW_COST) {
                serverPlayer.sendMessage(Text.literal("❌ 积分不足！抽卡需要 " + DRAW_COST + " 积分，当前余额: " + balance).formatted(Formatting.RED), false);
                world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0f, 1.0f);
                return ActionResult.CONSUME;
            }

            // 扣除积分
            if (!DatabaseManager.deductPoints(serverPlayer.getUuid(), DRAW_COST, "抽卡台抽卡")) {
                serverPlayer.sendMessage(Text.literal("❌ 扣费失败，请稍后重试！").formatted(Formatting.RED), false);
                return ActionResult.CONSUME;
            }

            // 抽卡概率：SSR 3%, SR 12%, R 25%, N 60%
            int roll = RANDOM.nextInt(100);
            ItemStack card;
            String rarity;
            Formatting color;
            String cardName;

            if (roll < 3) {
                rarity = "SSR";
                color = Formatting.LIGHT_PURPLE;
                String[] ssrPool = {"末影龙魂", "创世主权", "下界合金之翼", "深渊主宰", "寰宇之星"};
                cardName = ssrPool[RANDOM.nextInt(ssrPool.length)];
                card = CardItem.createCard(cardName, rarity, 950 + RANDOM.nextInt(50), color);

                // 全服播报
                Text broadcast = Text.literal("🌟 [抽卡台欧皇时刻] 恭喜玩家 ")
                        .append(Text.literal(serverPlayer.getName().getString()).formatted(Formatting.GOLD, Formatting.BOLD))
                        .append(Text.literal(" 一发入魂抽出了传说级卡牌【"))
                        .append(Text.literal(cardName).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                        .append(Text.literal("】！"));
                ((ServerWorld) world).getServer().getPlayerManager().broadcast(broadcast, false);

                // 欧皇音效与全屏粒子
                world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                if (world instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.2);
                }
            } else if (roll < 15) {
                rarity = "SR";
                color = Formatting.GOLD;
                String[] srPool = {"凋灵之骨", "潮涌核心", "远古守护者", "不死图腾之魂"};
                cardName = srPool[RANDOM.nextInt(srPool.length)];
                card = CardItem.createCard(cardName, rarity, 800 + RANDOM.nextInt(100), color);

                world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.8f, 1.2f);
                if (world instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.5);
                }
            } else if (roll < 40) {
                rarity = "R";
                color = Formatting.AQUA;
                String[] rPool = {"钻石剑灵", "附魔金苹果", "闪电苦力怕", "烈焰人法杖"};
                cardName = rPool[RANDOM.nextInt(rPool.length)];
                card = CardItem.createCard(cardName, rarity, 600 + RANDOM.nextInt(150), color);

                world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.8f, 1.0f);
            } else {
                rarity = "N";
                color = Formatting.GRAY;
                String[] nPool = {"铁傀儡", "流浪商人", "泥土狂想曲", "小僵尸", "末影珍珠"};
                cardName = nPool[RANDOM.nextInt(nPool.length)];
                card = CardItem.createCard(cardName, rarity, 200 + RANDOM.nextInt(300), color);

                world.playSound(null, pos, SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundCategory.BLOCKS, 0.8f, 1.0f);
            }

            // 发放卡牌
            if (!serverPlayer.getInventory().insertStack(card)) {
                serverPlayer.dropItem(card, false);
            }

            serverPlayer.sendMessage(Text.literal("🎴 抽卡完成！获得了 [")
                    .append(Text.literal(rarity + " - " + cardName).formatted(color, Formatting.BOLD))
                    .append(Text.literal("]！消耗了 10 积分。")), false);
        }

        return ActionResult.SUCCESS;
    }
}
