package net.jifenyouxi.mixin;

import net.jifenyouxi.event.FishingHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 完全替代原版钓鱼机制：
 * 起竿时不再产出原版鱼/垃圾/宝藏，而是始终获得按群系与昼夜判定的专属鱼获
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {
    @Shadow
    private boolean caughtFish;

    @Shadow
    public abstract PlayerEntity getPlayerOwner();

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onHookUse(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        World world = bobber.getEntityWorld();

        if (world.isClient() || !this.caughtFish) {
            return;
        }

        PlayerEntity player = this.getPlayerOwner();
        if (player == null) {
            return;
        }

        // 生成专属鱼获并抛向玩家
        ItemStack customFish = FishingHandler.rollCustomFish(player, bobber.getBlockPos());
        ItemEntity itemEntity = new ItemEntity(world, bobber.getX(), bobber.getY(), bobber.getZ(), customFish);

        double dx = player.getX() - bobber.getX();
        double dy = player.getY() - bobber.getY();
        double dz = player.getZ() - bobber.getZ();
        itemEntity.setVelocity(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
        world.spawnEntity(itemEntity);

        // 水花音效 + 鱼竿耐久消耗
        world.playSound(null, bobber.getX(), bobber.getY(), bobber.getZ(),
                net.minecraft.sound.SoundEvents.ENTITY_FISHING_BOBBER_SPLASH,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.4f, 1.0f);
        usedItem.damage(1, player, EquipmentSlot.MAINHAND);

        // 手动收竿并拦截原版逻辑（原版渔获不再生成）
        bobber.discard();
        cir.setReturnValue(0);
    }
}
