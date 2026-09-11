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
 * 每日限 10 次 + 5 秒冷却；按卡牌分解回收价计算，期望回报约 79%（负期望）
 */
public class CardTableBlock extends Block {
    private static final int DRAW_COST = 10;
    private static final int DAILY_LIMIT = 10;
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
            // 1. 冷却检查
            long cd = DatabaseManager.getCooldownRemaining(serverPlayer.getUuid(), "card_table");
            if (cd > 0) {
                serverPlayer.sendMessage(Text.literal("⏳ 抽卡太快啦，请 " + cd + " 秒后再试！").formatted(Formatting.RED), false);
                return ActionResult.CONSUME;
            }

            // 2. 余额检查
            int balance = DatabaseManager.getBalance(serverPlayer.getUuid());
            if (balance < DRAW_COST) {
                serverPlayer.sendMessage(Text.literal("❌ 积分不足！抽卡需要 " + DRAW_COST + " 积分，当前余额: " + balance).formatted(Formatting.RED), false);
                world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0f, 1.0f);
                return ActionResult.CONSUME;
            }

            // 3. 每日限次检查
            long mcDay = ((ServerWorld) world).getTime() / 24000L;
            if (!DatabaseManager.tryRecordDailyPlay(serverPlayer.getUuid(), "card_table", mcDay, DAILY_LIMIT)) {
                serverPlayer.sendMessage(Text.literal("🚫 今日抽卡次数已用完（" + DAILY_LIMIT + " 次/日），明天再来吧！").formatted(Formatting.RED), false);
                return ActionResult.CONSUME;
            }

            // 4. 扣除积分
            if (!DatabaseManager.deductPoints(serverPlayer.getUuid(), DRAW_COST, "抽卡台抽卡")) {
                serverPlayer.sendMessage(Text.literal("❌ 扣费失败，请稍后重试！").formatted(Formatting.RED), false);
                return ActionResult.CONSUME;
            }
            DatabaseManager.markPlayed(serverPlayer.getUuid(), "card_table");

            // 5. 抽卡概率：SSR 2%, SR 8%, R 20%, N 70%（期望回报约 7.9 / 10，负期望）
            int roll = RANDOM.nextInt(100);
            ItemStack card;
            String rarity;
            Formatting color;
            String cardName;

            if (roll < 2) {
                rarity = "SSR";
                color = Formatting.LIGHT_PURPLE;
                String[] ssrPool = {"末影龙魂", "创世主权", "下界合金之翼", "深渊主宰", "寰宇之星"};
                cardName = ssrPool[RANDOM.nextInt(ssrPool.length)];
                card = CardItem.createCard(cardName, rarity, 950 + RANDOM.nextInt(50), color);

                Text broadcast = Text.literal("🌟 [抽卡台欧皇时刻] 恭喜玩家 ")
                        .append(Text.literal(serverPlayer.getName().getString()).formatted(Formatting.GOLD, Formatting.BOLD))
                        .append(Text.literal(" 一发入魂抽出了传说级卡牌【"))
                        .append(Text.literal(cardName).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                        .append(Text.literal("】！"));
                ((ServerWorld) world).getServer().getPlayerManager().broadcast(broadcast, false);

                world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.0f, 1.0f);
                if (world instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.2);
                }
            } else if (roll < 10) {
                rarity = "SR";
                color = Formatting.GOLD;
                String[] srPool = {"凋灵之骨", "潮涌核心", "远古守护者", "不死图腾之魂"};
                cardName = srPool[RANDOM.nextInt(srPool.length)];
                card = CardItem.createCard(cardName, rarity, 800 + RANDOM.nextInt(100), color);

                world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.8f, 1.2f);
                if (world instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.5);
                }
            } else if (roll < 30) {
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

            if (!serverPlayer.getInventory().insertStack(card)) {
                serverPlayer.dropItem(card, false);
            }

            serverPlayer.sendMessage(Text.literal("🎴 抽卡完成！获得了 [")
                    .append(Text.literal(rarity + " - " + cardName).formatted(color, Formatting.BOLD))
                    .append(Text.literal("]！消耗了 " + DRAW_COST + " 积分。")), false);
        }

        return ActionResult.SUCCESS;
    }
}
