package net.jifenyouxi.gui;

import net.jifenyouxi.database.DatabaseManager;
import net.jifenyouxi.item.CardItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

/**
 * 幸运赌桌 GUI (基于原版 9x3 箱子界面)
 * 所有玩法期望收益为负（庄家优势），且每日限次 + 共享 5 秒冷却
 */
public class GamblerScreenHandler extends GenericContainerScreenHandler {
    private final PlayerEntity player;
    private static final Random RANDOM = new Random();

    public static final int SLOT_WHEEL = 10;
    public static final int SLOT_SLOTMACHINE = 12;
    public static final int SLOT_SCRATCH = 14;
    public static final int SLOT_CARD = 16;
    public static final int SLOT_INFO = 22;

    private static final int WHEEL_COST = 20, WHEEL_LIMIT = 10;
    private static final int SLOT_COST = 50, SLOT_LIMIT = 6;
    private static final int SCRATCH_COST = 10, SCRATCH_LIMIT = 10;
    private static final int CARD_COST = 15, CARD_LIMIT = 8;

    public GamblerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27));
    }

    public GamblerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.player = playerInventory.player;
        setupButtons();
    }

    private void setupButtons() {
        ItemStack grayGlass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        grayGlass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            getInventory().setStack(i, grayGlass);
        }

        ItemStack wheel = new ItemStack(Items.COMPASS);
        wheel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎡 幸运大转盘 (20积分/次)").formatted(Formatting.GOLD, Formatting.BOLD));
        wheel.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("每日限 " + WHEEL_LIMIT + " 次 · 头奖4%中100分").formatted(Formatting.GRAY)
        )));
        getInventory().setStack(SLOT_WHEEL, wheel);

        ItemStack slotMachine = new ItemStack(Items.GOLD_INGOT);
        slotMachine.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎰 疯狂水果机 (50积分/次)").formatted(Formatting.YELLOW, Formatting.BOLD));
        slotMachine.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("每日限 " + SLOT_LIMIT + " 次 · 钻石三连中400分").formatted(Formatting.GRAY)
        )));
        getInventory().setStack(SLOT_SLOTMACHINE, slotMachine);

        ItemStack scratch = new ItemStack(Items.PAPER);
        scratch.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎫 极速刮刮乐 (10积分/次)").formatted(Formatting.GREEN, Formatting.BOLD));
        scratch.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("每日限 " + SCRATCH_LIMIT + " 次 · 最高30分").formatted(Formatting.GRAY)
        )));
        getInventory().setStack(SLOT_SCRATCH, scratch);

        ItemStack card = new ItemStack(Items.NETHER_STAR);
        card.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎴 神秘抽卡 (15积分/次)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        card.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(java.util.List.of(
                Text.literal("每日限 " + CARD_LIMIT + " 次 · 5%得SR卡").formatted(Formatting.GRAY)
        )));
        getInventory().setStack(SLOT_CARD, card);

        updateInfoButton();
    }

    private void updateInfoButton() {
        int bal = DatabaseManager.getBalance(player.getUuid());
        ItemStack info = new ItemStack(Items.EMERALD);
        info.set(DataComponentTypes.CUSTOM_NAME, Text.literal("💰 当前积分余额: " + bal).formatted(Formatting.GREEN, Formatting.BOLD));
        getInventory().setStack(SLOT_INFO, info);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex < 0 || slotIndex >= 27) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        if (player.getEntityWorld().isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        if (slotIndex == SLOT_WHEEL) {
            playWheel(serverPlayer);
        } else if (slotIndex == SLOT_SLOTMACHINE) {
            playSlotMachine(serverPlayer);
        } else if (slotIndex == SLOT_SCRATCH) {
            playScratch(serverPlayer);
        } else if (slotIndex == SLOT_CARD) {
            playCard(serverPlayer);
        }

        updateInfoButton();
        sendContentUpdates();
    }

    /**
     * 统一入口校验：5 秒冷却 + 每日限次，返回错误消息或 null
     */
    private String checkPlay(ServerPlayerEntity p, String game, int dailyLimit, int cost) {
        long cd = DatabaseManager.getCooldownRemaining(p.getUuid(), game);
        if (cd > 0) {
            return "⏳ 操作太快啦，请 " + cd + " 秒后再试！";
        }
        if (DatabaseManager.getBalance(p.getUuid()) < cost) {
            return "❌ 积分不足！需要 " + cost + " 积分，当前余额: " + DatabaseManager.getBalance(p.getUuid());
        }
        long mcDay = ((ServerWorld) p.getEntityWorld()).getTime() / 24000L;
        if (!DatabaseManager.tryRecordDailyPlay(p.getUuid(), game, mcDay, dailyLimit)) {
            return "🚫 今日该玩法次数已用完（" + dailyLimit + " 次/日），明天再来吧！";
        }
        return null;
    }

    private void playWheel(ServerPlayerEntity p) {
        String err = checkPlay(p, "wheel", WHEEL_LIMIT, WHEEL_COST);
        if (err != null) { p.sendMessage(Text.literal(err).formatted(Formatting.RED), false); return; }

        if (!DatabaseManager.deductPoints(p.getUuid(), WHEEL_COST, "幸运大转盘")) return;
        DatabaseManager.markPlayed(p.getUuid(), "wheel");

        ServerWorld sw = (ServerWorld) p.getEntityWorld();
        int r = RANDOM.nextInt(100);
        // 期望回报 15.4 / 20 = 77% RTP（负期望）
        if (r < 4) {
            int win = 100;
            DatabaseManager.addPoints(p.getUuid(), win, "转盘头奖");
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            p.sendMessage(Text.literal("🎉 [大转盘] 命中头奖！获得 " + win + " 积分！").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        } else if (r < 20) {
            int win = 40;
            DatabaseManager.addPoints(p.getUuid(), win, "转盘双倍");
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
            p.sendMessage(Text.literal("✨ [大转盘] 命中 2 倍奖励！获得 " + win + " 积分！").formatted(Formatting.YELLOW), false);
        } else if (r < 45) {
            DatabaseManager.addPoints(p.getUuid(), WHEEL_COST, "转盘保本");
            p.sendMessage(Text.literal("🤝 [大转盘] 保本退回 " + WHEEL_COST + " 积分。").formatted(Formatting.GRAY), false);
        } else {
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.PLAYERS, 0.8f, 0.8f);
            p.sendMessage(Text.literal("💨 [大转盘] 谢谢惠顾，庄家永远笑到最后~").formatted(Formatting.RED), false);
        }
    }

    private void playSlotMachine(ServerPlayerEntity p) {
        String err = checkPlay(p, "slot", SLOT_LIMIT, SLOT_COST);
        if (err != null) { p.sendMessage(Text.literal(err).formatted(Formatting.RED), false); return; }

        if (!DatabaseManager.deductPoints(p.getUuid(), SLOT_COST, "水果机")) return;
        DatabaseManager.markPlayed(p.getUuid(), "slot");

        String[] fruits = {"🍎苹果", "🍉西瓜", "🍇葡萄", "🔔金钟", "💎钻石"};
        int f1 = RANDOM.nextInt(fruits.length);
        int f2 = RANDOM.nextInt(fruits.length);
        int f3 = RANDOM.nextInt(fruits.length);
        String line = "[" + fruits[f1] + " | " + fruits[f2] + " | " + fruits[f3] + "]";

        if (f1 == f2 && f2 == f3) {
            int win = (f1 == 4) ? 400 : 150;
            DatabaseManager.addPoints(p.getUuid(), win, "水果机三连");
            p.getEntityWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            p.sendMessage(Text.literal("🎰 [水果机] " + line + " 三连！赢得 " + win + " 积分！").formatted(Formatting.GOLD, Formatting.BOLD), false);
        } else if (f1 == f2 || f2 == f3 || f1 == f3) {
            int win = 50;
            DatabaseManager.addPoints(p.getUuid(), win, "水果机二连");
            p.sendMessage(Text.literal("🎰 [水果机] " + line + " 双连！赢得 " + win + " 积分！").formatted(Formatting.YELLOW), false);
        } else {
            p.sendMessage(Text.literal("🎰 [水果机] " + line + " 未能匹配，下次再来！").formatted(Formatting.GRAY), false);
        }
    }

    private void playScratch(ServerPlayerEntity p) {
        String err = checkPlay(p, "scratch", SCRATCH_LIMIT, SCRATCH_COST);
        if (err != null) { p.sendMessage(Text.literal(err).formatted(Formatting.RED), false); return; }

        if (!DatabaseManager.deductPoints(p.getUuid(), SCRATCH_COST, "刮刮乐")) return;
        DatabaseManager.markPlayed(p.getUuid(), "scratch");

        int n = RANDOM.nextInt(100);
        // 期望回报 5 / 10 = 50% RTP（负期望）
        if (n < 5) {
            DatabaseManager.addPoints(p.getUuid(), 30, "刮刮乐大奖");
            p.sendMessage(Text.literal("🎫 [刮刮乐] 头奖！获得 30 积分！").formatted(Formatting.GREEN, Formatting.BOLD), false);
        } else if (n < 15) {
            DatabaseManager.addPoints(p.getUuid(), 20, "刮刮乐中奖");
            p.sendMessage(Text.literal("🎫 [刮刮乐] 中奖！获得 20 积分！").formatted(Formatting.GREEN), false);
        } else if (n < 30) {
            DatabaseManager.addPoints(p.getUuid(), 10, "刮刮乐小奖");
            p.sendMessage(Text.literal("🎫 [刮刮乐] 小奖回血，获得 10 积分！").formatted(Formatting.YELLOW), false);
        } else {
            p.sendMessage(Text.literal("🎫 [刮刮乐] 刮出「感谢惠顾」~").formatted(Formatting.GRAY), false);
        }
    }

    private void playCard(ServerPlayerEntity p) {
        String err = checkPlay(p, "mystery_card", CARD_LIMIT, CARD_COST);
        if (err != null) { p.sendMessage(Text.literal(err).formatted(Formatting.RED), false); return; }

        if (!DatabaseManager.deductPoints(p.getUuid(), CARD_COST, "赌桌神秘抽卡")) return;
        DatabaseManager.markPlayed(p.getUuid(), "mystery_card");

        int roll = RANDOM.nextInt(100);
        ItemStack card;
        if (roll < 65) {
            String[] names = {"铁傀儡", "流浪商人", "泥土狂想曲", "末影珍珠"};
            card = CardItem.createCard(names[RANDOM.nextInt(names.length)], "N", 200 + RANDOM.nextInt(300), Formatting.GRAY);
        } else if (roll < 95) {
            String[] names = {"钻石剑灵", "附魔金苹果", "闪电苦力怕"};
            card = CardItem.createCard(names[RANDOM.nextInt(names.length)], "R", 600 + RANDOM.nextInt(150), Formatting.AQUA);
        } else {
            String[] names = {"凋灵之骨", "潮涌核心", "不死图腾之魂"};
            card = CardItem.createCard(names[RANDOM.nextInt(names.length)], "SR", 800 + RANDOM.nextInt(100), Formatting.GOLD);
        }

        if (!p.getInventory().insertStack(card)) {
            p.dropItem(card, false);
        }
        p.sendMessage(Text.literal("🎴 [神秘抽卡] 获得了一张卡牌，快去收集吧！").formatted(Formatting.LIGHT_PURPLE), false);
    }
}
