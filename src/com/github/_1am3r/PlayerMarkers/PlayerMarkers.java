package com.github._1am3r.PlayerMarkers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class PlayerMarkers extends JavaPlugin implements Runnable, Listener {
	private static final String MappingSectionName = "Mapping";

	private int updateTaskId = 0;
	private JSONDataWriter dataWriter = null;
	private PluginDescriptionFile pdfFile;
	private File locationsFile;
	private Map<String, String> nameMap = new HashMap<String, String>();
	private ConcurrentHashMap<String, SimpleLocation> offlinePlayers = new ConcurrentHashMap<String, SimpleLocation>();

	public void onEnable() {
		pdfFile = this.getDescription();

		// Clear out the mapping
		nameMap.clear();

		// Load the name mapping from the config
		ConfigurationSection mappingsection = getConfig().getConfigurationSection(MappingSectionName);
		if (mappingsection != null) {
			// Load and check the mapping found in the config
			Map<String, Object> configMap = mappingsection.getValues(false);
			for (Map.Entry<String, Object> entry : configMap.entrySet()) {
				nameMap.put(entry.getKey(), (String) entry.getValue());
			}
		} else {
			Logger.getLogger(pdfFile.getName()).log(Level.WARNING, "[" + pdfFile.getName() + "] found no configured mapping, creating a default one.");
		}
		// If there are new worlds in the server add them to the mapping
		List<World> serverWorlds = getServer().getWorlds();
		for (World w : serverWorlds) {
			if (!nameMap.containsKey(w.getName())) {
				nameMap.put(w.getName(), w.getName());
			}
		}
		// Set the new mapping
		getConfig().createSection(MappingSectionName, nameMap);

		// Save the config
		getConfig().options().copyDefaults(true);
		saveConfig();

		locationsFile = new File(getDataFolder(), "locations.bin");

		initializeOfflinePlayersMap();

		int updateInterval = getConfig().getInt("updateInterval");
		// Convert interval from 1000 ms to game ticks (20 per second)
		updateInterval /= 50;

		String targetFile = getConfig().getString("targetFile");
		dataWriter = new JSONDataWriter(targetFile);

		// Register update task
		updateTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, this, updateInterval, updateInterval);

		// Register our event handlers
		getServer().getPluginManager().registerEvents(this, this);

		// Done initializing, tell the world
		Logger.getLogger(pdfFile.getName()).log(Level.INFO, pdfFile.getName() + " version " + pdfFile.getVersion() + " enabled");
	}

	public void onDisable() {
		// Disable updates
		getServer().getScheduler().cancelTask(updateTaskId);

		// Save the offline plaers map
		saveOfflinePlayersMap();

		// Update data one last time
		this.run();

		Logger.getLogger(pdfFile.getName()).log(Level.INFO, pdfFile.getName() + " disabled");
	}

	@EventHandler
	public void playerJoin(PlayerJoinEvent event) {
		offlinePlayers.remove(event.getPlayer().getName());
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event) {
		offlinePlayers.put(event.getPlayer().getName(), new SimpleLocation(event.getPlayer().getLocation()));
	}

	@SuppressWarnings("unchecked")
	public void initializeOfflinePlayersMap() {
		if (locationsFile.exists() && locationsFile.isFile()) {
			// Data is stored, load it
			try {
				ObjectInputStream in = new ObjectInputStream(new FileInputStream(locationsFile));
				offlinePlayers = (ConcurrentHashMap<String, SimpleLocation>) in.readObject();
				in.close();
			} catch (IOException e) {
				Logger.getLogger(pdfFile.getName()).log(Level.WARNING, pdfFile.getName() + ": Couldn't open Locations file!");
			} catch (ClassNotFoundException e) {
				Logger.getLogger(pdfFile.getName()).log(Level.WARNING, pdfFile.getName() + ": Couldn't load Locations file!");
			}
		}
	}

	public void saveOfflinePlayersMap() {
		try {
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(locationsFile));
			out.writeObject(offlinePlayers);
			out.close();
		} catch (IOException e) {
			Logger.getLogger(pdfFile.getName()).log(Level.WARNING, pdfFile.getName() + ": Couldn't write Locations file! \n" + e.getMessage());
		}
	}

	public static class SimpleLocation implements Serializable {
		private static final long serialVersionUID = -1249619403579340650L;
		public String worldName;
		public int x, y, z;

		public SimpleLocation(String world, int xLocation, int yLocation, int zLocation) {
			worldName = world;
			x = xLocation;
			y = yLocation;
			z = zLocation;
		}

		public SimpleLocation(Location loc) {
			worldName = loc.getWorld().getName();
			x = loc.getBlockX();
			y = loc.getBlockY();
			z = loc.getBlockZ();
		}
	}

	@SuppressWarnings("unchecked")
	public void run() {
		JSONArray jsonList = new JSONArray();
		JSONObject out;

		// Write Online players
		Player[] players = getServer().getOnlinePlayers();
		for (Player p : players) {
			out = new JSONObject();
			out.put("msg", p.getName());
			out.put("id", 4);
			out.put("world", nameMap.get(p.getLocation().getWorld().getName()));
			out.put("x", p.getLocation().getBlockX());
			out.put("y", p.getLocation().getBlockY());
			out.put("z", p.getLocation().getBlockZ());

			jsonList.add(out);
		}

		// Write Offline players
		for (ConcurrentHashMap.Entry<String, SimpleLocation> p : offlinePlayers.entrySet()) {
			out = new JSONObject();
			out.put("msg", p.getKey());
			out.put("id", 5);
			out.put("world", nameMap.get(p.getValue().worldName));
			out.put("x", p.getValue().x);
			out.put("y", p.getValue().y);
			out.put("z", p.getValue().z);

			jsonList.add(out);
		}

		dataWriter.setData(jsonList);
		getServer().getScheduler().scheduleAsyncDelayedTask(this, dataWriter);
	}

	private class JSONDataWriter implements Runnable {
		private final String targetPath;
		private JSONArray jsonData;

		public JSONDataWriter(String path) {
			targetPath = path;
		}

		public void setData(JSONArray data) {
			if (jsonData != null) {
				return;
			} else {
				jsonData = (JSONArray) data.clone();
			}
		}

		public void run() {
			if (jsonData != null) {
				try {
					PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(targetPath)));
					writer.print(jsonData);
					writer.close();
				} catch (java.io.IOException e) {
					Logger.getLogger(getDescription().getName()).log(Level.SEVERE, "Unable to write to " + targetPath + ": " + e.getMessage());
				} finally {
					jsonData = null;
				}
			}
		}

	}
}
