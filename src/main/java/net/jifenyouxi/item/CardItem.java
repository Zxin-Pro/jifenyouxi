package net.jifenyouxi.item;

import net.jifenyouxi.database.DatabaseManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

public class CardItem extends Item {
    public CardItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createCard(String cardName, String rarity, int power, Formatting color) {
        ItemStack stack = new ItemStack(ModItems.COLLECTIBLE_CARD);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("CardName", cardName);
        nbt.putString("CardRarity", rarity);
        nbt.putInt("CardPower", power);

        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("[" + rarity + "] " + cardName).formatted(color, Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("品质: " + rarity).formatted(Formatting.AQUA),
                Text.literal("战力: " + power).formatted(Formatting.GREEN),
                Text.literal("👉 [右键] 可将重复卡牌分解为保底积分").formatted(Formatting.GRAY)
        )));
        return stack;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound nbt = comp.copyNbt();

            String rarity = nbt.getString("CardRarity").orElse("N");
            int recycleValue = switch (rarity) {
                case "SSR" -> 300;
                case "SR" -> 80;
                case "R" -> 20;
                default -> 5;
            };

            // 玩家可选择右键分解多余卡牌换取保底积分
            DatabaseManager.addPoints(player.getUuid(), recycleValue, "分解卡牌: " + rarity);
            stack.decrement(1);

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.7f, 1.4f);
            player.sendMessage(Text.literal("✨ 分解卡牌成功，获得保底 ")
                    .append(Text.literal(String.valueOf(recycleValue)).formatted(Formatting.YELLOW))
                    .append(Text.literal(" 积分！")), false);

            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }
}
