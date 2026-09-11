package net.jifenyouxi.entity;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.jifenyouxi.gui.BankerScreenHandler;
import net.jifenyouxi.gui.GamblerScreenHandler;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * 赌博村民与银行家村民的交互与识别
 */
public class ModVillagers {
    public static void registerVillagerEvents() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != Hand.MAIN_HAND || world.isClient()) {
                return ActionResult.PASS;
            }

            if (entity instanceof VillagerEntity villager && player instanceof ServerPlayerEntity serverPlayer) {
                String name = villager.getName().getString();

                if (name.contains("赌博") || name.contains("转盘") || name.equalsIgnoreCase("gambler")) {
                    serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inv, p) -> new GamblerScreenHandler(syncId, inv),
                            Text.literal("🎲 赌博村民 - 娱乐大厅")
                    ));
                    return ActionResult.SUCCESS;
                }

                if (name.contains("银行") || name.contains("贷款") || name.equalsIgnoreCase("banker")) {
                    serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inv, p) -> new BankerScreenHandler(syncId, inv),
                            Text.literal("🏦 银行家村民 - 金融服务中心")
                    ));
                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });
    }
}
