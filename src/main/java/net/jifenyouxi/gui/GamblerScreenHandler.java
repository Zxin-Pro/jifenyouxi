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
 * 赌博村民互动 GUI (基于原版 9x3 箱子界面)
 */
public class GamblerScreenHandler extends GenericContainerScreenHandler {
    private final PlayerEntity player;
    private static final Random RANDOM = new Random();

    public static final int SLOT_WHEEL = 10;
    public static final int SLOT_SLOTMACHINE = 12;
    public static final int SLOT_SCRATCH = 14;
    public static final int SLOT_CARD = 16;
    public static final int SLOT_INFO = 22;

    public GamblerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27));
    }

    public GamblerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
        this.player = playerInventory.player;
        setupButtons();
    }

    private void setupButtons() {
        // 装饰边框
        ItemStack grayGlass = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        grayGlass.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) {
            getInventory().setStack(i, grayGlass);
        }

        // 1. 幸运转盘 (20积分)
        ItemStack wheel = new ItemStack(Items.COMPASS);
        wheel.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎡 幸运大转盘").formatted(Formatting.GOLD, Formatting.BOLD));
        getInventory().setStack(SLOT_WHEEL, wheel);

        // 2. 水果老虎机 (50积分)
        ItemStack slotMachine = new ItemStack(Items.GOLD_INGOT);
        slotMachine.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎰 疯狂水果机").formatted(Formatting.YELLOW, Formatting.BOLD));
        getInventory().setStack(SLOT_SLOTMACHINE, slotMachine);

        // 3. 幸运刮刮乐 (10积分)
        ItemStack scratch = new ItemStack(Items.PAPER);
        scratch.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎫 极速刮刮乐").formatted(Formatting.GREEN, Formatting.BOLD));
        getInventory().setStack(SLOT_SCRATCH, scratch);

        // 4. 惊喜抽卡 (30积分)
        ItemStack card = new ItemStack(Items.NETHER_STAR);
        card.set(DataComponentTypes.CUSTOM_NAME, Text.literal("🎴 神秘抽卡").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        getInventory().setStack(SLOT_CARD, card);

        // 5. 个人信息
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

        // 拦截并处理功能按钮
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

    private void playWheel(ServerPlayerEntity p) {
        int cost = 20;
        if (!DatabaseManager.deductPoints(p.getUuid(), cost, "幸运大转盘")) {
            p.sendMessage(Text.literal("❌ 积分不足！参与大转盘需要 20 积分").formatted(Formatting.RED), false);
            return;
        }

        ServerWorld sw = (ServerWorld) p.getEntityWorld();
        int r = RANDOM.nextInt(100);
        if (r < 5) {
            // 5倍
            int win = cost * 5;
            DatabaseManager.addPoints(p.getUuid(), win, "转盘大奖");
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            p.sendMessage(Text.literal("🎉 [大转盘] 欧气爆发！命中 5 倍终极大奖！获得 " + win + " 积分！").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        } else if (r < 30) {
            // 2倍
            int win = cost * 2;
            DatabaseManager.addPoints(p.getUuid(), win, "转盘翻倍");
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
            p.sendMessage(Text.literal("✨ [大转盘] 恭喜中奖！命中 2 倍奖励！获得 " + win + " 积分！").formatted(Formatting.YELLOW), false);
        } else if (r < 60) {
            // 保本
            DatabaseManager.addPoints(p.getUuid(), cost, "转盘保底");
            p.sendMessage(Text.literal("🤝 [大转盘] 运气平平，退回本金 " + cost + " 积分。").formatted(Formatting.GRAY), false);
        } else {
            // 未中奖
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.PLAYERS, 0.8f, 0.8f);
            p.sendMessage(Text.literal("💨 [大转盘] 谢谢惠顾，距离大奖就差一点点了喵~").formatted(Formatting.RED), false);
        }
    }

    private void playSlotMachine(ServerPlayerEntity p) {
        int cost = 50;
        if (!DatabaseManager.deductPoints(p.getUuid(), cost, "水果机")) {
            p.sendMessage(Text.literal("❌ 积分不足！水果机需要 50 积分").formatted(Formatting.RED), false);
            return;
        }

        ServerWorld sw = (ServerWorld) p.getEntityWorld();
        String[] fruits = {"🍎苹果", "🍉西瓜", "🍇葡萄", "🔔金钟", "💎钻石"};
        int f1 = RANDOM.nextInt(fruits.length);
        int f2 = RANDOM.nextInt(fruits.length);
        int f3 = RANDOM.nextInt(fruits.length);

        String line = "[" + fruits[f1] + " | " + fruits[f2] + " | " + fruits[f3] + "]";
        if (f1 == f2 && f2 == f3) {
            int win = (f1 == 4) ? 500 : 200;
            DatabaseManager.addPoints(p.getUuid(), win, "水果机三连");
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            p.sendMessage(Text.literal("🎰 [水果机] " + line + " ！！！完美三连！赢得 " + win + " 积分！").formatted(Formatting.GOLD, Formatting.BOLD), false);
        } else if (f1 == f2 || f2 == f3 || f1 == f3) {
            int win = 60;
            DatabaseManager.addPoints(p.getUuid(), win, "水果机二连");
            p.sendMessage(Text.literal("🎰 [水果机] " + line + " 双连成功！赢得 " + win + " 积分！").formatted(Formatting.YELLOW), false);
        } else {
            p.sendMessage(Text.literal("🎰 [水果机] " + line + " 未能匹配，请再接再厉！").formatted(Formatting.GRAY), false);
        }
    }

    private void playScratch(ServerPlayerEntity p) {
        int cost = 10;
        if (!DatabaseManager.deductPoints(p.getUuid(), cost, "刮刮乐")) {
            p.sendMessage(Text.literal("❌ 积分不足！刮刮乐需要 10 积分").formatted(Formatting.RED), false);
            return;
        }

        int win = switch (RANDOM.nextInt(10)) {
            case 0 -> 100;
            case 1, 2 -> 30;
            case 3, 4, 5 -> 15;
            default -> 0;
        };

        if (win > 0) {
            DatabaseManager.addPoints(p.getUuid(), win, "刮刮乐中奖");
            p.sendMessage(Text.literal("🎫 [刮刮乐] 刮开涂层发现中奖代码！恭喜获得 " + win + " 积分！").formatted(Formatting.GREEN), false);
        } else {
            p.sendMessage(Text.literal("🎫 [刮刮乐] 刮出「感谢惠顾」，下次一定！").formatted(Formatting.GRAY), false);
        }
    }

    private void playCard(ServerPlayerEntity p) {
        int cost = 30;
        if (!DatabaseManager.deductPoints(p.getUuid(), cost, "赌博村民抽卡")) {
            p.sendMessage(Text.literal("❌ 积分不足！神秘抽卡需要 30 积分").formatted(Formatting.RED), false);
            return;
        }

        ItemStack card = CardItem.createCard("神秘赌徒之证", "SR", 888, Formatting.GOLD);
        if (!p.getInventory().insertStack(card)) {
            p.dropItem(card, false);
        }
        p.sendMessage(Text.literal("🎴 [抽卡] 赌博村民向你递了一张珍贵的卡牌！").formatted(Formatting.LIGHT_PURPLE), false);
    }
}
