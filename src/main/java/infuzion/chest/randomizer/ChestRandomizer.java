package infuzion.chest.randomizer;

import infuzion.chest.randomizer.command.CommandMain;
import infuzion.chest.randomizer.event.*;
import infuzion.chest.randomizer.storage.ChestLocation;
import infuzion.chest.randomizer.storage.ChestManager;
import infuzion.chest.randomizer.storage.DatabaseChestManager;
import infuzion.chest.randomizer.storage.FileChestManager;
import infuzion.chest.randomizer.util.BlockFaceWrapper;
import infuzion.chest.randomizer.util.Metrics;
import infuzion.chest.randomizer.util.configuration.ChestRandomizationItem;
import infuzion.chest.randomizer.util.configuration.ConfigManager;
import infuzion.chest.randomizer.util.messages.Messages;
import infuzion.chest.randomizer.util.messages.MessagesManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.inventivetalent.update.spiget.SpigetUpdate;
import org.inventivetalent.update.spiget.UpdateCallback;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public class ChestRandomizer extends JavaPlugin {

    @SuppressWarnings("FieldCanBeLocal")
    private final double version = 3.5d;
    private final Map<CommandSender, String> confirmationGroups = new HashMap<>();
    private MessagesManager messagesManager;
    private ConfigManager configManager;
    private ChestManager chestManager;
    private Random random;
    private String prefix;
    private java.sql.Connection connection;
    private Map<CommandSender, Integer> confirmations = new HashMap<>();

    public void addToConfirmationGroups(CommandSender commandSender, String string) {
        confirmationGroups.put(commandSender, string);
    }

    public ChestManager getChestManager() {
        return chestManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public String getConfirmationGroups(CommandSender commandSender) {
        return confirmationGroups.get(commandSender);
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public String getPrefix() {
        return prefix;
    }

    public double getVersion() {
        return version;
    }

    public List<String> possibilityChecker(List<String> possible, String toCompare) {
        List<String> toReturn = new ArrayList<>();
        if (toCompare.trim().equalsIgnoreCase("")) {
            toReturn.addAll(possible);
        } else {
            for (String e : possible) {
                if (e.startsWith(toCompare)) {
                    toReturn.add(e);
                }
            }
        }
        Collections.sort(toReturn);
        return toReturn;
    }

    public boolean randomize(int percent) {
        if (percent >= 100) {
            return true;
        } else if (percent < 0) {
            getLogger().severe("Negative Value detected in configuration file. This value will be ignored.");
        } else if (random.nextInt(101) <= percent) {
            return true;
        }
        return false;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (configManager != null) {
            configManager.reload();
        }
        if (messagesManager != null) {
            messagesManager.reload();
        }
    }

    @Override
    public void onLoad() {
        random = new Random();
    }

    @Override
    public void onDisable() {
        chestManager.cleanUp();
    }

    @Override
    public void onEnable() {
        ConfigurationSerialization.registerClass(ChestRandomizationItem.class, "ChestRandomizationItem");
        ConfigurationSerialization.registerClass(ChestLocation.class, "CRChestLocation");
        ConfigurationSerialization.registerClass(BlockFaceWrapper.class, "CRBlockFaceDirection");

        messagesManager = new MessagesManager(this);
        new Messages(this);

        prefix = Messages.variable_prefix;

        configManager = new ConfigManager(this);
        if (configManager.getBoolean("DataBase.using")) {
            int port = getConfig().getInt("ChestRandomizer.DataBase.port");
            String host = getConfig().getString("ChestRandomizer.DataBase.host");
            String user = getConfig().getString("ChestRandomizer.DataBase.user");
            String driver = getConfig().getString("ChestRandomizer.DataBase.driver");
            String pass = getConfig().getString("ChestRandomizer.DataBase.pass");
            String database = getConfig().getString("ChestRandomizer.DataBase.database");
            String table = getConfig().getString("ChestRandomizer.DataBase.tableName");
            if (openConnection(driver, host, port, database, user, pass)) {
                chestManager = new DatabaseChestManager(this, connection, database, table);
                getLogger().info("Using database storage for chests.");
            } else {
                chestManager = new FileChestManager(this);
                getLogger().info("Using yml storage for chests.");
            }
        } else {
            chestManager = new FileChestManager(this);
            getLogger().info("Using yml storage for chests.");
        }


        getCommand("chestrandomizer").setExecutor(new CommandMain(this));
        getCommand("chestrandomizer").setTabCompleter(new tabCompleter(this));

        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new onPlayerCommandPreprocess(this), this);
        pluginManager.registerEvents(new onPlayerDisconnect(this), this);
        pluginManager.registerEvents(new onConsoleCommand(this), this);
        pluginManager.registerEvents(new onBlockBreak(this), this);


        if (!getConfig().getBoolean("ChestRandomizer.Metrics.Opt-Out")) {
            try {
                Metrics metrics = new Metrics(this);
                metrics.start();
            } catch (IOException e) {
                getLogger().warning("Could not Log Metric Statistics! Verify internet connection is available and try again!");
            }
        }
        if (!(getConfig().getBoolean("ChestRandomizer.Updater.Opt-Out"))) {
            final SpigetUpdate updater = new SpigetUpdate(this, 30534);
            updater.checkForUpdate(new UpdateCallback() {
                public void updateAvailable(String newVersion, String downloadUrl, boolean hasDirectDownload) {
                    if (hasDirectDownload) {
                        updater.downloadUpdate();
                    }
                }

                public void upToDate() {
                }
            });
        }
        verifyConfirmations();

    }

    private boolean openConnection(String driver, String host, int port, String database, String username, String password) {
        try {
            if (connection != null && !connection.isClosed()) {
                return false;
            }

            synchronized (this) {
                if (connection != null && !connection.isClosed()) {
                    return false;
                }
                Class.forName(driver);
                getLogger().severe("jdbc:mysql://" + host + ":" + port + "/" + database);
                connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, username, password);
                return true;
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void verifyConfirmations() {
        new BukkitRunnable() {
            public void run() {
                if (getConfirmations().isEmpty()) {
                    return;
                }

                for (CommandSender e : getConfirmations().keySet()) {
                    confirmations.put(e, confirmations.get(e) - 1);
                    if (confirmations.get(e) <= 0 || confirmations.get(e) > 31) {
                        String group = confirmationGroups.get(e);
                        e.sendMessage(Messages.admin_remove_timeout.replace("<group>", group));
                        confirmations.remove(e);
                        confirmationGroups.remove(e);
                    }
                }
            }
        }.runTaskTimer(this, 0, 20);
    }

    public Map<CommandSender, Integer> getConfirmations() {
        return confirmations;
    }

    public void setConfirmations(Map<CommandSender, Integer> confirmations) {
        this.confirmations = confirmations;
    }

}