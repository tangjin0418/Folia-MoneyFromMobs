package me.chocolf.moneyfrommobs;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import me.chocolf.moneyfrommobs.commands.*;
import me.chocolf.moneyfrommobs.integrations.WorldGuardFlags;
import me.chocolf.moneyfrommobs.listeners.*;
import me.chocolf.moneyfrommobs.managers.*;
import me.chocolf.moneyfrommobs.runnables.RepeatingMultiplierEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import me.chocolf.moneyfrommobs.integrations.MoneyFromMobsPlaceholderExpansion;
import me.chocolf.moneyfrommobs.integrations.MythicMobsFileManager;
import me.chocolf.moneyfrommobs.runnables.NearEntitiesRunnable;
import me.chocolf.moneyfrommobs.utils.ConfigUpdater;
import me.chocolf.moneyfrommobs.utils.Metrics;
import me.chocolf.moneyfrommobs.utils.UpdateChecker;
import me.chocolf.moneyfrommobs.utils.VersionUtils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.tjdev.util.tjpluginutil.spigot.scheduler.universalscheduler.scheduling.tasks.MyScheduledTask;

public class MoneyFromMobs extends JavaPlugin{
	private Economy econ = null;
	private Permission permissions = null;
	private MythicMobsFileManager mmConfig;
	private MultipliersFileManager multipliersConfig;
	private PickUpManager pickUpManager;
	private MessageManager messageManager;
	private DropsManager dropsManager;
	private MobManager mobManager;
	private MultipliersManager multipliersManager;
	private MyScheduledTask inventoryIsFullRunnable;
	private MyScheduledTask repeatingMultiplierEvent;
	private PlaceholderAPIListener placeholderListener;
	private static MoneyFromMobs instance;
	
	@Override
	public void onEnable() {
		instance = this;
		
		// bstats
		new Metrics(this, 8361); // 8361 is this plugins id
		
		// Disable plugin if fail to set up vault and econ plugin
		if(!setupEconomy()){
			getLogger().severe("COMPATIBLE ECONOMY PLUGIN NOT FOUND! DISABLING PLUGIN!!!");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		setupPermissions();

		// listeners
		new PickUpListeners(this);
		new DeathListeners(this);
		new MobSpawnListener(this);
		new OnJoinListener(this);
		new HopperListeners(this);
		if (VersionUtils.getVersionNumber() > 13)
			new PlaceMinecartHopperListener(this);
		if (isUsingPaper())
			new PaperListeners(this);
		if(Bukkit.getServer().getPluginManager().isPluginEnabled("WorldGuard") && VersionUtils.getVersionNumber() > 15)
			new WorldGuardListener(this);
		
		// config stuff
		loadConfigs();
		
		// Commands
		new ClearDropsCommand(this);
		new AdminDropMoneyCommand(this);
		new PlayerDropMoneyCommand(this);
		this.getCommand("AdminDropMoney").setTabCompleter(new DropMoneyTabCompleter());
		new MfmEventCommand(this);
		this.getCommand("mfmevent").setTabCompleter(new MfmEventTabCompleter());
		new MuteMessagesCommand(this);
		new ReloadCommand(this);
		new HelpCommand(this);

		// Managers
		pickUpManager = new PickUpManager(this);
		messageManager = new MessageManager(this);
		dropsManager = new DropsManager(this);
		mobManager = new MobManager(this);
		multipliersManager = new MultipliersManager(this);
		
		// PlaceholderAPI integration
		if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
			new MoneyFromMobsPlaceholderExpansion(this).register();
			placeholderListener = new PlaceholderAPIListener(this);
			getLogger().info("Found PlaceholderAPI and expansion successfully registered ");
		}
	
		// Bukkit runnable to allow players to pickup items when inventory is full
		loadInventoryIsFullRunnable();

		// Bukkit runnable for Repeating Multiplier Event
		loadRepeatingMultiplierEvent();
		
		// Checks if user is using the latest version of the plugin on spigot
		try {
			if (UpdateChecker.checkForUpdate())
				getLogger().info("Update Available for MoneyFromMobs: https://www.spigotmc.org/resources/money-from-mobs-1-12-1-20-5.79137/");
		}
		catch (Exception e) {
			getLogger().warning("Unable to retrieve latest update from SpigotMC.org");
		}
	}
	
	@Override
	public void onLoad() {
		// loads WorldGuard flag
		if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && VersionUtils.getVersionNumber() > 15) {
			WorldGuardFlags.registerFlags();
		}
	}
	
	@Override
	public void onDisable() {
		List<World> worldList = Bukkit.getServer().getWorlds();
		for (World world : worldList) {
			List<Entity> entList = world.getEntities();
			for (Entity entity : entList) {
				if ( entity instanceof ArmorStand ) {
					ArmorStand armorstand = (ArmorStand) entity;
					if (armorstand.hasMetadata("mfmas")) {
						armorstand.remove();
					}
				}
			}
		}
	}

	// sets up economy if server has Vault and an Economy plugin
	private boolean setupEconomy() {
    	RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
    	if (rsp != null)
    		econ = rsp.getProvider();

		return econ != null;
    }
	
	// sets up permission hook
	private void setupPermissions() {
		RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
		if (rsp != null)
			if (rsp.getProvider().hasGroupSupport())
	        	permissions = rsp.getProvider();
			else
				this.getLogger().warning(MessageManager.applyColour("&cCOMPATIBLE PERMISSIONS PLUGIN NOT FOUND! PERMISSION GROUP MULTIPLIERS WILL NOT WORK!!!"));
	}
	
	private void loadConfigs() {
		// loads config.yml and auto updates it
		saveDefaultConfig();
		try {
			  ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"), Arrays.asList());//The list is sections you want to ignore
		} 
		catch (IOException e) {
			  e.printStackTrace();
		}
		reloadConfig();
		
		// Makes MythicMobs and Multipliers Config file
		mmConfig = new MythicMobsFileManager(this);
		multipliersConfig = new MultipliersFileManager(this);

		// Updates Multipliers.yml
		try {
			  ConfigUpdater.update(this, "Multipliers.yml", new File(getDataFolder(), "Multipliers.yml"), Arrays.asList());//The list is sections you want to ignore
		} 
		catch (IOException e) {
			  e.printStackTrace();
		}
	}
	
	// loads runnable that allows players to pick up money when their inventory is full
	public void loadInventoryIsFullRunnable() {
		if (isUsingPaper()) return;
		
		if ( getConfig().getBoolean("PickupMoneyWhenInventoryIsFull.Enabled")) {
			int interval = this.getConfig().getInt("PickupMoneyWhenInventoryIsFull.Interval");
			inventoryIsFullRunnable = new NearEntitiesRunnable(this).runTaskTimer(this, interval, interval);
		}
	}

	public void loadRepeatingMultiplierEvent(){
		if(getMultipliersConfig().getConfig().getBoolean("RepeatingMultiplierEvent.Enabled"))
			repeatingMultiplierEvent = new RepeatingMultiplierEvent(this).runTaskTimer(this, multipliersManager.getRepeatingInitialDelay(), multipliersManager.getRepeatingDelay());
	}
	
	// checks if server is running Paper 1.13+
	public boolean isUsingPaper() {
		String version = getServer().getVersion();
		return version.contains("Paper") || version.contains("Purpur");
	}

	public MythicMobsFileManager getMMConfig() {
		return mmConfig;
	}
	public MultipliersFileManager getMultipliersConfig() {
		return multipliersConfig;
	}
	public Economy getEcon() {
		return econ;
	}
	public Permission getPerms() {
		return permissions;
	}
	public PickUpManager getPickUpManager() {
		return pickUpManager;
	}
	public MessageManager getMessageManager() {
		return messageManager;
	}
	public MultipliersManager getMultipliersManager() {
		return multipliersManager;
	}

	public DropsManager getDropsManager() {
		return dropsManager;
	}
	public MobManager getNumbersManager() {
		return mobManager;
	}
	public PlaceholderAPIListener getPlaceholdersListener() {
		return placeholderListener;
	}
	public MyScheduledTask getInventoryIsFullRunnable() {
		return inventoryIsFullRunnable;
	}
	public MyScheduledTask getRepeatingMultiplierEvent(){return repeatingMultiplierEvent;}
	public void setRepeatingMultiplierEvent(MyScheduledTask newRepeatingMultiplierEvent){ repeatingMultiplierEvent = newRepeatingMultiplierEvent;}
	public static MoneyFromMobs getInstance() {
		return instance;
	}
}
