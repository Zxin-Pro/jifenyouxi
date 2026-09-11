package net.jifenyouxi.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * SQLite 数据库管理工具类
 * 负责全部数据持久化、连接池及带并发锁的事务操作
 */
public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("JifenyouxiMod/Database");
    private static Connection connection;
    private static final Object DB_LOCK = new Object();

    public static synchronized void initialize(File worldDir) {
        try {
            if (!worldDir.exists()) {
                worldDir.mkdirs();
            }
            File dbFile = new File(worldDir, "jifenyouxi.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            // 加载 SQLite 驱动
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);

            // 启用 WAL 模式提高并发读写性能
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }

            createTables();
            LOGGER.info("数据库初始化成功，文件路径: {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("初始化 SQLite 数据库失败!", e);
        }
    }

    private static void createTables() throws SQLException {
        synchronized (DB_LOCK) {
            try (Statement stmt = connection.createStatement()) {
                // 1. 玩家主表
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        name TEXT,
                        balance INTEGER DEFAULT 0,
                        total_earned INTEGER DEFAULT 0,
                        total_spent INTEGER DEFAULT 0,
                        sign_date TEXT DEFAULT '',
                        sign_streak INTEGER DEFAULT 0
                    );
                """);

                // 2. 彩票表
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS lottery (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT,
                        numbers TEXT,
                        cost INTEGER,
                        period TEXT
                    );
                """);

                // 3. 银行账户表
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bank_accounts (
                        uuid TEXT PRIMARY KEY,
                        balance INTEGER DEFAULT 0,
                        total_interest INTEGER DEFAULT 0
                    );
                """);

                // 4. 贷款表
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS loans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT,
                        amount INTEGER,
                        interest INTEGER,
                        status TEXT,
                        due_date TEXT
                    );
                """);

                // 5. 钓鱼统计表
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS fishing_stats (
                        uuid TEXT PRIMARY KEY,
                        total_income INTEGER DEFAULT 0,
                        total_count INTEGER DEFAULT 0,
                        best_fish TEXT DEFAULT ''
                    );
                """);
            }
        }
    }

    public static synchronized void close() {
        synchronized (DB_LOCK) {
            if (connection != null) {
                try {
                    connection.close();
                    LOGGER.info("数据库连接已正常关闭。");
                } catch (SQLException e) {
                    LOGGER.error("关闭数据库连接异常!", e);
                }
            }
        }
    }

    /**
     * 确保玩家存在于表中
     */
    public static void ensurePlayer(UUID uuid, String name) {
        synchronized (DB_LOCK) {
            String sql = "INSERT OR IGNORE INTO players (uuid, name, balance, total_earned, total_spent, sign_date, sign_streak) VALUES (?, ?, 0, 0, 0, '', 0)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, name);
                pstmt.executeUpdate();

                // 更新玩家名
                try (PreparedStatement updateName = connection.prepareStatement("UPDATE players SET name = ? WHERE uuid = ?")) {
                    updateName.setString(1, name);
                    updateName.setString(2, uuid.toString());
                    updateName.executeUpdate();
                }
            } catch (SQLException e) {
                LOGGER.error("初始化玩家数据失败: {}", uuid, e);
            }
        }
    }

    /**
     * 查询积分余额
     */
    public static int getBalance(UUID uuid) {
        synchronized (DB_LOCK) {
            String sql = "SELECT balance FROM players WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("balance");
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("查询余额异常: {}", uuid, e);
            }
            return 0;
        }
    }

    /**
     * 增加积分 (带事务处理与逾期还贷自动代扣)
     */
    public static boolean addPoints(UUID uuid, int amount, String reason) {
        if (amount <= 0) return false;
        synchronized (DB_LOCK) {
            try {
                connection.setAutoCommit(false);

                // 检查该玩家是否有逾期未还的贷款，若有先强制扣除用于还款
                int actualCredit = amount;
                int deductedForLoan = autoRepayOverdueLoans(uuid, amount);
                actualCredit -= deductedForLoan;

                String sql = "UPDATE players SET balance = balance + ?, total_earned = total_earned + ? WHERE uuid = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, actualCredit);
                    pstmt.setInt(2, actualCredit);
                    pstmt.setString(3, uuid.toString());
                    pstmt.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    LOGGER.error("回滚失败", ex);
                }
                LOGGER.error("增加积分失败: uuid={}, amount={}, reason={}", uuid, amount, reason, e);
                return false;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * 扣除积分 (带事务处理与余额校验)
     */
    public static boolean deductPoints(UUID uuid, int amount, String reason) {
        if (amount <= 0) return false;
        synchronized (DB_LOCK) {
            try {
                connection.setAutoCommit(false);
                int current = getBalance(uuid);
                if (current < amount) {
                    connection.rollback();
                    return false;
                }

                String sql = "UPDATE players SET balance = balance - ?, total_spent = total_spent + ? WHERE uuid = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, amount);
                    pstmt.setInt(2, amount);
                    pstmt.setString(3, uuid.toString());
                    pstmt.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    LOGGER.error("回滚失败", ex);
                }
                LOGGER.error("扣除积分失败: uuid={}, amount={}", uuid, amount, e);
                return false;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * 床铺签到处理 (每个MC游戏日一次，判断连续天数)
     * 返回获得的分数，-1 表示今日已签过
     */
    public static int bedSignIn(UUID uuid, String name, long mcDay) {
        synchronized (DB_LOCK) {
            ensurePlayer(uuid, name);
            try {
                connection.setAutoCommit(false);
                String sql = "SELECT sign_date, sign_streak FROM players WHERE uuid = ?";
                long lastDay = -1;
                int streak = 0;
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            String dateStr = rs.getString("sign_date");
                            if (dateStr != null && !dateStr.isEmpty()) {
                                try {
                                    lastDay = Long.parseLong(dateStr);
                                } catch (NumberFormatException ignored) {}
                            }
                            streak = rs.getInt("sign_streak");
                        }
                    }
                }

                if (lastDay == mcDay) {
                    // 今日已签到
                    connection.rollback();
                    return -1;
                }

                if (lastDay == mcDay - 1) {
                    streak += 1;
                } else {
                    streak = 1;
                }

                // 每日首次 1-10 积分
                int reward = 1 + new Random().nextInt(10);
                boolean is7DayBonus = false;
                if (streak % 7 == 0) {
                    reward += 20; // 连续7日额外+20分
                    is7DayBonus = true;
                }

                String updateSql = "UPDATE players SET balance = balance + ?, total_earned = total_earned + ?, sign_date = ?, sign_streak = ? WHERE uuid = ?";
                try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                    update.setInt(1, reward);
                    update.setInt(2, reward);
                    update.setString(3, String.valueOf(mcDay));
                    update.setInt(4, streak);
                    update.setString(5, uuid.toString());
                    update.executeUpdate();
                }

                connection.commit();
                return reward;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {}
                LOGGER.error("床铺签到失败: {}", uuid, e);
                return -1;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
    }

    public static int getSignStreak(UUID uuid) {
        synchronized (DB_LOCK) {
            String sql = "SELECT sign_streak FROM players WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("sign_streak");
                    }
                }
            } catch (SQLException ignored) {}
            return 0;
        }
    }

    // ================== 银行系统 ==================

    public static void ensureBankAccount(UUID uuid) {
        synchronized (DB_LOCK) {
            String sql = "INSERT OR IGNORE INTO bank_accounts (uuid, balance, total_interest) VALUES (?, 0, 0)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("初始化银行账户失败: {}", uuid, e);
            }
        }
    }

    public static int getBankBalance(UUID uuid) {
        synchronized (DB_LOCK) {
            ensureBankAccount(uuid);
            String sql = "SELECT balance FROM bank_accounts WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("balance");
                    }
                }
            } catch (SQLException ignored) {}
            return 0;
        }
    }

    public static boolean bankDeposit(UUID uuid, int amount) {
        if (amount <= 0) return false;
        synchronized (DB_LOCK) {
            ensureBankAccount(uuid);
            try {
                connection.setAutoCommit(false);
                // 扣除玩家钱包
                if (!deductPoints(uuid, amount, "银行存款")) {
                    connection.rollback();
                    return false;
                }
                // 存入银行
                String sql = "UPDATE bank_accounts SET balance = balance + ? WHERE uuid = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, amount);
                    pstmt.setString(2, uuid.toString());
                    pstmt.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {}
                LOGGER.error("存款失败", e);
                return false;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
    }

    public static boolean bankWithdraw(UUID uuid, int amount) {
        if (amount <= 0) return false;
        synchronized (DB_LOCK) {
            ensureBankAccount(uuid);
            try {
                connection.setAutoCommit(false);
                int bankBal = getBankBalance(uuid);
                if (bankBal < amount) {
                    connection.rollback();
                    return false;
                }
                String sql = "UPDATE bank_accounts SET balance = balance - ? WHERE uuid = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, amount);
                    pstmt.setString(2, uuid.toString());
                    pstmt.executeUpdate();
                }
                addPoints(uuid, amount, "银行取款");
                connection.commit();
                return true;
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {}
                LOGGER.error("取款失败", e);
                return false;
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * 每日计息结算：所有活期账户获得 5% 利息
     */
    public static Map<UUID, Integer> applyDailyBankInterest(double rate) {
        Map<UUID, Integer> result = new HashMap<>();
        synchronized (DB_LOCK) {
            String query = "SELECT uuid, balance FROM bank_accounts WHERE balance > 0";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                List<Map.Entry<String, Integer>> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(Map.entry(rs.getString("uuid"), rs.getInt("balance")));
                }

                connection.setAutoCommit(false);
                String update = "UPDATE bank_accounts SET balance = balance + ?, total_interest = total_interest + ? WHERE uuid = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(update)) {
                    for (Map.Entry<String, Integer> entry : list) {
                        int interest = Math.max(1, (int) (entry.getValue() * rate));
                        pstmt.setInt(1, interest);
                        pstmt.setInt(2, interest);
                        pstmt.setString(3, entry.getKey());
                        pstmt.addBatch();
                        result.put(UUID.fromString(entry.getKey()), interest);
                    }
                    pstmt.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {}
                LOGGER.error("结算银行利息异常", e);
            } finally {
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
            }
        }
        return result;
    }

    // ================== 贷款与征信 ==================

    public static boolean applyLoan(UUID uuid, int amount, int interest, String dueDate) {
        synchronized (DB_LOCK) {
            String sql = "INSERT INTO loans (uuid, amount, interest, status, due_date) VALUES (?, ?, ?, 'ACTIVE', ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setInt(2, amount);
                pstmt.setInt(3, interest);
                pstmt.setString(4, dueDate);
                pstmt.executeUpdate();
                addPoints(uuid, amount, "银行借款");
                return true;
            } catch (SQLException e) {
                LOGGER.error("借款异常", e);
                return false;
            }
        }
    }

    public static boolean repayLoan(UUID uuid, int loanId) {
        synchronized (DB_LOCK) {
            String sql = "SELECT amount, interest FROM loans WHERE id = ? AND uuid = ? AND status IN ('ACTIVE', 'OVERDUE')";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, loanId);
                pstmt.setString(2, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int totalDue = rs.getInt("amount") + rs.getInt("interest");
                        if (deductPoints(uuid, totalDue, "偿还贷款")) {
                            try (PreparedStatement upd = connection.prepareStatement("UPDATE loans SET status = 'REPAID' WHERE id = ?")) {
                                upd.setInt(1, loanId);
                                upd.executeUpdate();
                                return true;
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("还款异常", e);
            }
            return false;
        }
    }

    public static List<LoanRecord> getPlayerLoans(UUID uuid) {
        List<LoanRecord> list = new ArrayList<>();
        synchronized (DB_LOCK) {
            String sql = "SELECT id, amount, interest, status, due_date FROM loans WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new LoanRecord(
                            rs.getInt("id"),
                            uuid,
                            rs.getInt("amount"),
                            rs.getInt("interest"),
                            rs.getString("status"),
                            rs.getString("due_date")
                        ));
                    }
                }
            } catch (SQLException ignored) {}
        }
        return list;
    }

    public static List<LoanRecord> checkAndMarkOverdueLoans(long currentDay) {
        List<LoanRecord> overdueList = new ArrayList<>();
        synchronized (DB_LOCK) {
            String sql = "SELECT id, uuid, amount, interest, status, due_date FROM loans WHERE status = 'ACTIVE'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    long dueDay = Long.parseLong(rs.getString("due_date"));
                    if (currentDay > dueDay) {
                        int id = rs.getInt("id");
                        overdueList.add(new LoanRecord(
                            id,
                            UUID.fromString(rs.getString("uuid")),
                            rs.getInt("amount"),
                            rs.getInt("interest"),
                            "OVERDUE",
                            rs.getString("due_date")
                        ));
                    }
                }

                if (!overdueList.isEmpty()) {
                    try (PreparedStatement upd = connection.prepareStatement("UPDATE loans SET status = 'OVERDUE' WHERE id = ?")) {
                        for (LoanRecord r : overdueList) {
                            upd.setInt(1, r.id());
                            upd.addBatch();
                        }
                        upd.executeBatch();
                    }
                }
            } catch (Exception e) {
                LOGGER.error("逾期检查异常", e);
            }
        }
        return overdueList;
    }

    private static int autoRepayOverdueLoans(UUID uuid, int availableIncome) {
        int totalDeducted = 0;
        String sql = "SELECT id, amount, interest FROM loans WHERE uuid = ? AND status = 'OVERDUE' ORDER BY id ASC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next() && availableIncome > 0) {
                    int loanId = rs.getInt("id");
                    int amount = rs.getInt("amount");
                    int interest = rs.getInt("interest");
                    int totalNeed = amount + interest;

                    if (availableIncome >= totalNeed) {
                        try (PreparedStatement upd = connection.prepareStatement("UPDATE loans SET status = 'REPAID' WHERE id = ?")) {
                            upd.setInt(1, loanId);
                            upd.executeUpdate();
                        }
                        availableIncome -= totalNeed;
                        totalDeducted += totalNeed;
                    }
                }
            }
        } catch (SQLException ignored) {}
        return totalDeducted;
    }

    // ================== 钓鱼统计 ==================

    public static void recordFishingCatch(UUID uuid, String fishName, int value) {
        synchronized (DB_LOCK) {
            String insert = "INSERT OR IGNORE INTO fishing_stats (uuid, total_income, total_count, best_fish) VALUES (?, 0, 0, '')";
            try (PreparedStatement ins = connection.prepareStatement(insert)) {
                ins.setString(1, uuid.toString());
                ins.executeUpdate();
            } catch (SQLException ignored) {}

            String update = "UPDATE fishing_stats SET total_income = total_income + ?, total_count = total_count + 1 WHERE uuid = ?";
            try (PreparedStatement upd = connection.prepareStatement(update)) {
                upd.setInt(1, value);
                upd.setString(2, uuid.toString());
                upd.executeUpdate();
            } catch (SQLException ignored) {}
        }
    }

    // ================== 排行榜 ==================

    public record PlayerRank(String name, int balance) {}
    public record LoanRecord(int id, UUID uuid, int amount, int interest, String status, String dueDate) {}

    public static List<PlayerRank> getTopPlayers(int limit) {
        List<PlayerRank> list = new ArrayList<>();
        synchronized (DB_LOCK) {
            String sql = "SELECT name, balance FROM players ORDER BY balance DESC LIMIT ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        list.add(new PlayerRank(rs.getString("name"), rs.getInt("balance")));
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("查询排行失败", e);
            }
        }
        return list;
    }
}
