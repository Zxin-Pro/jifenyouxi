package net.jifenyouxi.gui;

import net.jifenyouxi.database.DatabaseManager;
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

import java.util.List;

/**
 * 银行家村民互动 GUI (支持借贷、还款、信用状态查询)
 */
public class BankerScreenHandler extends GenericContainerScreenHandler {
    private final PlayerEntity player;

    public static final int SLOT_LOAN_100 = 10;
    public static final int SLOT_LOAN_500 = 12;
    public static final int SLOT_LOAN_1000 = 14;
    public static final int SLOT_REPAY = 16;
    public static final int SLOT_STATUS = 22;

    public BankerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(27));
    }

    public BankerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
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

        // 1. 小额贷款
        ItemStack loan1 = new ItemStack(Items.COPPER_INGOT);
        loan1.set(DataComponentTypes.CUSTOM_NAME, Text.literal("💳 应急贷款 (100积分)").formatted(Formatting.AQUA, Formatting.BOLD));
        getInventory().setStack(SLOT_LOAN_100, loan1);

        // 2. 中额贷款
        ItemStack loan2 = new ItemStack(Items.IRON_INGOT);
        loan2.set(DataComponentTypes.CUSTOM_NAME, Text.literal("💳 商业周转贷款 (500积分)").formatted(Formatting.GOLD, Formatting.BOLD));
        getInventory().setStack(SLOT_LOAN_500, loan2);

        // 3. 大额贷款
        ItemStack loan3 = new ItemStack(Items.DIAMOND);
        loan3.set(DataComponentTypes.CUSTOM_NAME, Text.literal("💳 企业扶持贷款 (1000积分)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        getInventory().setStack(SLOT_LOAN_1000, loan3);

        // 4. 还款按钮
        ItemStack repay = new ItemStack(Items.EMERALD_BLOCK);
        repay.set(DataComponentTypes.CUSTOM_NAME, Text.literal("💵 偿还当前待还贷款").formatted(Formatting.GREEN, Formatting.BOLD));
        getInventory().setStack(SLOT_REPAY, repay);

        updateStatusButton();
    }

    private void updateStatusButton() {
        List<DatabaseManager.LoanRecord> loans = DatabaseManager.getPlayerLoans(player.getUuid());
        int totalDebt = 0;
        int activeLoans = 0;
        boolean hasOverdue = false;

        for (DatabaseManager.LoanRecord r : loans) {
            if ("ACTIVE".equals(r.status()) || "OVERDUE".equals(r.status())) {
                totalDebt += (r.amount() + r.interest());
                activeLoans++;
                if ("OVERDUE".equals(r.status())) {
                    hasOverdue = true;
                }
            }
        }

        ItemStack status = new ItemStack(hasOverdue ? Items.BARRIER : Items.WRITABLE_BOOK);
        String title = hasOverdue ? "⚠️ 征信警告：有逾期未还款项！" : "📊 个人信用与负债状况";
        status.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title).formatted(hasOverdue ? Formatting.RED : Formatting.YELLOW, Formatting.BOLD));
        getInventory().setStack(SLOT_STATUS, status);
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

        ServerWorld sw = (ServerWorld) serverPlayer.getEntityWorld();
        long currentDay = sw.getTime() / 24000L;

        if (slotIndex == SLOT_LOAN_100) {
            applyLoan(serverPlayer, 100, 10, currentDay + 3);
        } else if (slotIndex == SLOT_LOAN_500) {
            applyLoan(serverPlayer, 500, 50, currentDay + 5);
        } else if (slotIndex == SLOT_LOAN_1000) {
            applyLoan(serverPlayer, 1000, 150, currentDay + 7);
        } else if (slotIndex == SLOT_REPAY) {
            repayLoan(serverPlayer);
        }

        updateStatusButton();
        sendContentUpdates();
    }

    private void applyLoan(ServerPlayerEntity p, int amount, int interest, long dueDay) {
        List<DatabaseManager.LoanRecord> loans = DatabaseManager.getPlayerLoans(p.getUuid());
        for (DatabaseManager.LoanRecord r : loans) {
            if ("OVERDUE".equals(r.status())) {
                p.sendMessage(Text.literal("❌ 您有逾期款项未结清，已被银行列入黑名单，禁止申请新贷款！").formatted(Formatting.RED), false);
                return;
            }
            if ("ACTIVE".equals(r.status()) && loans.size() >= 3) {
                p.sendMessage(Text.literal("❌ 您已有 3 笔未结清贷款，已达贷款笔数上限！").formatted(Formatting.RED), false);
                return;
            }
        }

        boolean ok = DatabaseManager.applyLoan(p.getUuid(), amount, interest, String.valueOf(dueDay));
        if (ok) {
            ServerWorld sw = (ServerWorld) p.getEntityWorld();
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
            p.sendMessage(Text.literal("✅ [贷款成功] 成功向银行借款 ")
                    .append(Text.literal(String.valueOf(amount)).formatted(Formatting.GOLD))
                    .append(Text.literal(" 积分！到期需归还本息共计: "))
                    .append(Text.literal(String.valueOf(amount + interest)).formatted(Formatting.YELLOW))
                    .append(Text.literal(" 积分（于第 " + dueDay + " 个游戏日到期）。")), false);
        }
    }

    private void repayLoan(ServerPlayerEntity p) {
        List<DatabaseManager.LoanRecord> loans = DatabaseManager.getPlayerLoans(p.getUuid());
        DatabaseManager.LoanRecord toRepay = null;
        for (DatabaseManager.LoanRecord r : loans) {
            if ("OVERDUE".equals(r.status()) || "ACTIVE".equals(r.status())) {
                toRepay = r;
                break;
            }
        }

        if (toRepay == null) {
            p.sendMessage(Text.literal("👍 您当前没有任何待还贷款！信用良好！").formatted(Formatting.GREEN), false);
            return;
        }

        int totalDue = toRepay.amount() + toRepay.interest();
        if (DatabaseManager.getBalance(p.getUuid()) < totalDue) {
            p.sendMessage(Text.literal("❌ 积分不足！还款需要 " + totalDue + " 积分。").formatted(Formatting.RED), false);
            return;
        }

        boolean ok = DatabaseManager.repayLoan(p.getUuid(), toRepay.id());
        if (ok) {
            ServerWorld sw = (ServerWorld) p.getEntityWorld();
            sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
            p.sendMessage(Text.literal("🎉 [还款成功] 成功归还贷款 #" + toRepay.id() + "，本息合计: " + totalDue + " 积分！信用恢复！").formatted(Formatting.GREEN), false);
        }
    }
}
