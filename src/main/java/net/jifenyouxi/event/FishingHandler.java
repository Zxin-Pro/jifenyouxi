package net.jifenyouxi.event;

import net.jifenyouxi.item.CustomFishItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.Random;

/**
 * 2. 钓鱼扩展系统：根据群系、昼夜环境生成特色鱼类
 */
public class FishingHandler {
    private static final Random RANDOM = new Random();

    public static ItemStack rollCustomFish(PlayerEntity player, BlockPos pos) {
        World world = player.getEntityWorld();
        long time = world.getTimeOfDay() % 24000;
        boolean isNight = time >= 13000 && time <= 23000;

        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        boolean isOcean = biomeEntry.matchesKey(BiomeKeys.OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.DEEP_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.WARM_OCEAN) ||
                biomeEntry.matchesKey(BiomeKeys.LUKEWARM_OCEAN);

        boolean isJungle = biomeEntry.matchesKey(BiomeKeys.JUNGLE) ||
                biomeEntry.matchesKey(BiomeKeys.BAMBOO_JUNGLE);

        int roll = RANDOM.nextInt(100);

        // 1. 夜间专属 (20% 几率)
        if (isNight && roll < 20) {
            if (RANDOM.nextBoolean()) {
                return CustomFishItem.createFish("🌙 暗月幽灵鱼", 160, "史诗", Formatting.DARK_PURPLE);
            } else {
                return CustomFishItem.createFish("✨ 星夜荧光鳗", 75, "稀有", Formatting.AQUA);
            }
        }

        // 2. 海洋环境专属
        if (isOcean) {
            if (roll < 2) {
                return CustomFishItem.createFish("🔱 远古利维坦幻影", 666, "传说", Formatting.LIGHT_PURPLE);
            } else if (roll < 12) {
                return CustomFishItem.createFish("👑 深海皇带鱼", 130, "史诗", Formatting.DARK_AQUA);
            } else if (roll < 35) {
                return CustomFishItem.createFish("🐟 蓝鳍金枪鱼", 60, "稀有", Formatting.BLUE);
            }
        }

        // 3. 丛林环境专属
        if (isJungle) {
            if (roll < 5) {
                return CustomFishItem.createFish("🐉 黄金七彩龙鱼", 280, "史诗", Formatting.GOLD);
            } else if (roll < 30) {
                return CustomFishItem.createFish("🐠 亚马逊食人鱼", 50, "稀有", Formatting.RED);
            }
        }

        // 4. 通用淡水鱼池
        if (roll < 3) {
            return CustomFishItem.createFish("🌟 祥瑞锦鲤王", 300, "史诗", Formatting.GOLD);
        } else if (roll < 20) {
            return CustomFishItem.createFish("🎏 幸运锦鲤", 50, "稀有", Formatting.YELLOW);
        } else if (roll < 50) {
            return CustomFishItem.createFish("🐟 彩虹鳟鱼", 25, "普通", Formatting.GREEN);
        } else {
            return CustomFishItem.createFish("🐟 肥美草鱼", 10, "普通", Formatting.WHITE);
        }
    }
}
