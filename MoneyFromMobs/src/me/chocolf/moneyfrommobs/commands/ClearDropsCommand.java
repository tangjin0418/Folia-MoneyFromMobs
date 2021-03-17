package me.chocolf.moneyfrommobs.commands;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import me.chocolf.moneyfrommobs.MoneyFromMobs;
import me.chocolf.moneyfrommobs.utils.Utils;

public class ClearDropsCommand implements CommandExecutor{
	
	private MoneyFromMobs plugin;
	
	public ClearDropsCommand(MoneyFromMobs plugin) {
		this.plugin = plugin;
		plugin.getCommand("mfmclear").setExecutor(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		
		if (label.equalsIgnoreCase("mfmclear") && sender.hasPermission("MoneyFromMobs.clear")) {
			
			List<World> worldList = Bukkit.getServer().getWorlds();
			for (World world : worldList) {
				List<Entity> entList = world.getEntities();
				for (Entity entity : entList) {
					if (entity instanceof Item) {
						ItemStack item = ((Item) entity).getItemStack();
						if (plugin.getManager().checkIfMoney(item)) entity.remove();				
					}
				}
			}
			sender.sendMessage(Utils.applyColour("&9All money was succesfully removed from the ground!"));
			return true;
		}		
		return false;
	}

}
