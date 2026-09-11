package net.jifenyouxi.item;

import net.jifenyouxi.database.DatabaseManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class CustomFishItem extends Item {
    public CustomFishItem(Settings settings) {
        super(settings);
    }

    public static ItemStack createFish(String fishName, int value, String rarity, Formatting color) {
        ItemStack stack = new ItemStack(ModItems.CUSTOM_FISH);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("FishName", fishName);
        nbt.putInt("FishValue", value);
        nbt.putString("FishRarity", rarity);

        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(fishName).formatted(color, Formatting.BOLD));
        return stack;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
            NbtCompound nbt = comp.copyNbt();

            String fishName = nbt.contains("FishName") ? nbt.getString("FishName") : "普通小鱼";
            int value = nbt.contains("FishValue") ? nbt.getInt("FishValue") : 5;
            String rarity = nbt.contains("FishRarity") ? nbt.getString("FishRarity") : "普通";

            // 增加积分
            DatabaseManager.addPoints(serverPlayer.getUuid(), value, "出售鱼获: " + fishName);
            DatabaseManager.recordFishingCatch(serverPlayer.getUuid(), fishName, value);

            // 消耗一条鱼
            stack.decrement(1);

            // 音效与反馈
            world.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
            serverPlayer.sendMessage(Text.literal("🐟 你出售了 [")
                    .append(Text.literal(fishName).formatted(Formatting.GOLD))
                    .append(Text.literal("]，获得 "))
                    .append(Text.literal(String.valueOf(value)).formatted(Formatting.YELLOW))
                    .append(Text.literal(" 积分！")), false);

            // 稀有鱼全服广播
            if (value >= 100 || "传说".equals(rarity) || "史诗".equals(rarity)) {
                Text broadcast = Text.literal("🎉 [渔翁喜报] 玩家 ")
                        .append(Text.literal(serverPlayer.getName().getString()).formatted(Formatting.AQUA))
                        .append(Text.literal(" 出售了一条传说级鱼获【"))
                        .append(Text.literal(fishName).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD))
                        .append(Text.literal("】，价值 "))
                        .append(Text.literal(String.valueOf(value)).formatted(Formatting.YELLOW, Formatting.BOLD))
                        .append(Text.literal(" 积分！"));

                serverPlayer.getServer().getPlayerManager().broadcast(broadcast, false);
            }

            return TypedActionResult.success(stack);
        }

        return TypedActionResult.consume(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound nbt = comp.copyNbt();

        if (nbt.contains("FishValue")) {
            int val = nbt.getInt("FishValue");
            String rarity = nbt.getString("FishRarity");
            tooltip.add(Text.literal("品质: " + rarity).formatted(Formatting.GRAY));
            tooltip.add(Text.literal("售价: " + val + " 积分").formatted(Formatting.GOLD));
            tooltip.add(Text.literal("👉 [右键] 直接售出兑换积分").formatted(Formatting.DARK_GREEN));
        } else {
            tooltip.add(Text.literal("野生鱼类，右键出售").formatted(Formatting.GRAY));
        }
    }
}
