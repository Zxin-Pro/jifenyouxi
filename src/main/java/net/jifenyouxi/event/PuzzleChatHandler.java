package net.jifenyouxi.event;

import net.jifenyouxi.database.DatabaseManager;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 谜题系统：管理玩家答题状态和聊天栏回答拦截
 */
public class PuzzleChatHandler {
    public record ActivePuzzle(String question, String answer, int reward, BlockPos pos, long expireTime) {}

    private static final Map<UUID, ActivePuzzle> ACTIVE_PUZZLES = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    public static final String[][] PUZZLE_BANK = {
            // [分类, 问题, 标准答案]
            {"数学", "速算题：45 × 12 = ？", "540"},
            {"数学", "速算题：128 + 256 + 512 = ？", "896"},
            {"成语", "成语填空：一心( )用？", "一"},
            {"成语", "成语填空：狐( )虎威？", "假"},
            {"历史", "中国历史上第一个统一的中央集权封建国家是哪个朝代？", "秦朝"},
            {"历史", "汉武帝时期击败匈奴的名将，封狼居胥的是谁？", "霍去病"},
            {"地理", "世界上国土面积最大的国家是哪个国家？", "俄罗斯"},
            {"常识", "Minecraft中激活下界传送门需要什么工具？", "打火石"},
            {"常识", "酿造药水必须使用的基础地狱植物叫什么？", "地狱疣"},
            {"成语", "成语填空：望梅( )渴？", "止"}
    };

    public static ActivePuzzle createRandomPuzzle(BlockPos pos) {
        String[] q = PUZZLE_BANK[RANDOM.nextInt(PUZZLE_BANK.length)];
        int reward = 25 + RANDOM.nextInt(25); // 25~50积分
        long expire = System.currentTimeMillis() + 60000; // 60秒有效
        return new ActivePuzzle(q[1], q[2], reward, pos, expire);
    }

    public static void setPuzzle(UUID uuid, ActivePuzzle puzzle) {
        ACTIVE_PUZZLES.put(uuid, puzzle);
    }

    public static boolean hasPuzzle(UUID uuid) {
        ActivePuzzle ap = ACTIVE_PUZZLES.get(uuid);
        if (ap == null) return false;
        if (System.currentTimeMillis() > ap.expireTime()) {
            ACTIVE_PUZZLES.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * 处理玩家在聊天栏输入的答案
     * @return true 表示该消息是答题输入并已处理（阻止广播原消息）
     */
    public static boolean handleChat(ServerPlayerEntity player, String message) {
        UUID uuid = player.getUuid();
        ActivePuzzle puzzle = ACTIVE_PUZZLES.get(uuid);
        if (puzzle == null) return false;

        ACTIVE_PUZZLES.remove(uuid);

        if (System.currentTimeMillis() > puzzle.expireTime()) {
            player.sendMessage(Text.literal("⏰ 答题已超时，谜题失效！").formatted(Formatting.RED), false);
            return true;
        }

        String input = message.trim();
        ServerWorld world = player.getServerWorld();
        BlockPos pos = puzzle.pos();

        if (input.equalsIgnoreCase(puzzle.answer()) || input.contains(puzzle.answer())) {
            // 答对
            DatabaseManager.addPoints(uuid, puzzle.reward(), "答对谜题方块");
            world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 1.0f, 1.2f);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);

            // 谜题方块完成解答，破损消失
            world.breakBlock(pos, false);

            player.sendMessage(Text.literal("🎉 [回答正确] 恭喜你答对！获得 ")
                    .append(Text.literal(String.valueOf(puzzle.reward())).formatted(Formatting.YELLOW, Formatting.BOLD))
                    .append(Text.literal(" 积分！")), false);
        } else {
            // 答错
            world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 0.8f);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.05);

            // 答错方块直接自毁
            world.breakBlock(pos, false);

            player.sendMessage(Text.literal("❌ [回答错误] 正确答案是: ")
                    .append(Text.literal(puzzle.answer()).formatted(Formatting.GOLD))
                    .append(Text.literal("。方块已破碎自毁！")), false);
        }

        return true;
    }
}
