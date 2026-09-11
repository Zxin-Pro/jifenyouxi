package net.jifenyouxi.mixin;

import net.jifenyouxi.event.FishingHandler;
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

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin {
    @Shadow
    private boolean caughtFish;

    @Shadow
    public abstract PlayerEntity getPlayerOwner();

    @Inject(method = "use", at = @At("HEAD"))
    private void onHookUse(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
        FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
        World world = bobber.getWorld();

        if (!world.isClient() && this.caughtFish) {
            PlayerEntity player = this.getPlayerOwner();
            if (player != null && world.getRandom().nextInt(100) < 45) { // 45% 几率收获定制特产鱼获
                ItemStack customFish = FishingHandler.rollCustomFish(player, bobber.getBlockPos());
                ItemEntity itemEntity = new ItemEntity(world, bobber.getX(), bobber.getY(), bobber.getZ(), customFish);

                double dx = player.getX() - bobber.getX();
                double dy = player.getY() - bobber.getY();
                double dz = player.getZ() - bobber.getZ();
                itemEntity.setVelocity(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);

                world.spawnEntity(itemEntity);
            }
        }
    }
}
