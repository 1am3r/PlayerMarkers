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
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class PlayerMarkers extends JavaPlugin implements Runnable, Listener {
	private static final String MappingSectionName = "Mapping";

	private int mUpdateTaskId = 0;
	private JSONDataWriter mDataWriter = null;
	private PluginDescriptionFile mPdfFile;
	private File mOfflineLocationsFile = null;
	private Map<String, String> mMapNameMapping = new HashMap<String, String>();
	private ConcurrentHashMap<String, SimpleLocation> mOfflineLocations = new ConcurrentHashMap<String, SimpleLocation>();
	private boolean mSaveOfflinePlayers = true;
	private boolean mHideVanishedPlayers = true;
	private boolean mHideSneakingPlayers = true;
	private boolean mHideInvisiblePlayers = true;
	private boolean mSendJSONOnVanishedPlayers = false;
	private boolean mSendJSONOnSneakingPlayers = false;
	private boolean mSendJSONOnInvisiblePlayers = false;

	public void onEnable() {
		mPdfFile = this.getDescription();
		mSaveOfflinePlayers = getConfig().getBoolean("saveOfflinePlayers");
		mHideVanishedPlayers = getConfig().getBoolean("hideVanishedPlayers");
		mHideSneakingPlayers = getConfig().getBoolean("hideSneakingPlayers");
		mHideInvisiblePlayers = getConfig().getBoolean("hideInvisiblePlayers");
		mSendJSONOnVanishedPlayers = getConfig().getBoolean("sendJSONOnVanishedPlayers");
		mSendJSONOnSneakingPlayers = getConfig().getBoolean("sendJSONOnSneakingPlayers");
		mSendJSONOnInvisiblePlayers = getConfig().getBoolean("sendJSONOnInvisiblePlayers");

		// Initialize the mapping bukkit to overviewer map names
		initMapNameMapping();

		// Save the config
		getConfig().options().copyDefaults(true);
		saveConfig();

		if (mSaveOfflinePlayers) {
			initializeOfflinePlayersMap();
		}

		int updateInterval = getConfig().getInt("updateInterval");
		// Convert interval from 1000 ms to game ticks (20 per second)
		updateInterval /= 50;

		String targetFile = getConfig().getString("targetFile");
		mDataWriter = new JSONDataWriter(targetFile);

		// Register update task
		mUpdateTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, this, updateInterval, updateInterval);

		if (mSaveOfflinePlayers) {
			// Register our event handlers
			getServer().getPluginManager().registerEvents(this, this);
		}

		// Done initializing, tell the world
		Logger.getLogger(mPdfFile.getName()).log(Level.INFO, mPdfFile.getName() + " version " + mPdfFile.getVersion() + " enabled");
	}

	public void onDisable() {
		// Disable updates
		getServer().getScheduler().cancelTask(mUpdateTaskId);

		if (mSaveOfflinePlayers) {
			// Save the offline players map
			saveOfflinePlayersMap();
		}

		// Update data one last time
		this.run();

		Logger.getLogger(mPdfFile.getName()).log(Level.INFO, mPdfFile.getName() + " disabled");
	}

	@EventHandler
	public void playerJoin(PlayerJoinEvent event) {
		mOfflineLocations.remove(event.getPlayer().getName());
	}

	@EventHandler
	public void playerQuit(PlayerQuitEvent event) {
		mOfflineLocations.put(event.getPlayer().getName(), new SimpleLocation(event.getPlayer().getLocation()));
	}

	private void initMapNameMapping() {
		// Clear out the mapping
		mMapNameMapping.clear();

		// Load the name mapping from the config
		ConfigurationSection mappingsection = getConfig().getConfigurationSection(MappingSectionName);
		if (mappingsection != null) {
			// Load and check the mapping found in the config
			Map<String, Object> configMap = mappingsection.getValues(false);
			for (Map.Entry<String, Object> entry : configMap.entrySet()) {
				mMapNameMapping.put(entry.getKey(), (String) entry.getValue());
			}
		} else {
			Logger.getLogger(mPdfFile.getName()).log(Level.WARNING, "[" + mPdfFile.getName() + "] found no configured mapping, creating a default one.");
		}

		// If there are new worlds in the server add them to the mapping
		List<World> serverWorlds = getServer().getWorlds();
		for (World w : serverWorlds) {
			if (!mMapNameMapping.containsKey(w.getName())) {
				mMapNameMapping.put(w.getName(), w.getName());
			}
		}

		// Set the new mapping in the config
		getConfig().createSection(MappingSectionName, mMapNameMapping);
	}

	@SuppressWarnings("unchecked")
	private void initializeOfflinePlayersMap() {
		File configOfflineLocationPath = new File(getConfig().getString("offlineFile"));
		if (configOfflineLocationPath.isAbsolute()) {
			mOfflineLocationsFile = configOfflineLocationPath;
		} else {
			mOfflineLocationsFile = new File(getDataFolder(), configOfflineLocationPath.getPath());
		}

		if (mOfflineLocationsFile.exists() && mOfflineLocationsFile.isFile()) {
			// Data is stored, load it
			try {
				ObjectInputStream in = new ObjectInputStream(new FileInputStream(mOfflineLocationsFile));
				mOfflineLocations = (ConcurrentHashMap<String, SimpleLocation>) in.readObject();
				in.close();
			} catch (IOException e) {
				Logger.getLogger(mPdfFile.getName()).log(Level.WARNING,
						mPdfFile.getName() + ": Couldn't open Locations file from " + mOfflineLocationsFile.toString() + "!");
			} catch (ClassNotFoundException e) {
				Logger.getLogger(mPdfFile.getName()).log(Level.WARNING,
						mPdfFile.getName() + ": Couldn't load Locations file from " + mOfflineLocationsFile.toString() + "!");
			}
		}
	}

	private void saveOfflinePlayersMap() {
		if (mOfflineLocationsFile != null && mSaveOfflinePlayers) {
			try {
				ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(mOfflineLocationsFile));
				out.writeObject(mOfflineLocations);
				out.close();
			} catch (IOException e) {
				Logger.getLogger(mPdfFile.getName()).log(Level.WARNING,
						mPdfFile.getName() + ": Couldn't write Locations file from " + mOfflineLocationsFile.toString() + "! \n" + e.getMessage());
			}
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
			boolean sendDataVanished = true;
			boolean sendDataSneaking = true;
			boolean sendDataInvisible = true;

			out = new JSONObject();
			out.put("msg", p.getName());
			out.put("id", 4);
			out.put("world", mMapNameMapping.get(p.getLocation().getWorld().getName()));
			out.put("x", p.getLocation().getBlockX());
			out.put("y", p.getLocation().getBlockY());
			out.put("z", p.getLocation().getBlockZ());

			// Handles sneaking player
			if (mHideSneakingPlayers) {
				boolean isSneaking = p.isSneaking();

				if (isSneaking) {
					if (mSendJSONOnSneakingPlayers) {
						out.put("id", 6);
					}

					sendDataSneaking = false;
				}
			}

			// Handles invisible potion effect on player
			if (mHideInvisiblePlayers) {
				boolean isInvisible = p.hasPotionEffect(PotionEffectType.INVISIBILITY);

				if (isInvisible) {
					if (mSendJSONOnInvisiblePlayers) {
						out.put("id", 7); // will replace sneaking player ID
					}

					sendDataInvisible = false;
				}
			}

			// Handles vanished player
			if (mHideVanishedPlayers) {
				List<MetadataValue> list = p.getMetadata("vanished");
				for (MetadataValue value : list) {
					if (value.asBoolean()) {
						if (mSendJSONOnVanishedPlayers) {
							out.put("id", 5); // will replace invisible player ID
						}

						sendDataVanished = false;

						break;
					}
				}
			}

			if (sendDataSneaking || sendDataInvisible || sendDataVanished) {
				jsonList.add(out);
			}
		}

		if (mSaveOfflinePlayers) {
			// Write Offline players
			for (ConcurrentHashMap.Entry<String, SimpleLocation> p : mOfflineLocations.entrySet()) {
				out = new JSONObject();
				out.put("msg", p.getKey());
				out.put("id", 5);
				out.put("world", mMapNameMapping.get(p.getValue().worldName));
				out.put("x", p.getValue().x);
				out.put("y", p.getValue().y);
				out.put("z", p.getValue().z);

				jsonList.add(out);
			}
		}

		mDataWriter.setData(jsonList);
		getServer().getScheduler().scheduleAsyncDelayedTask(this, mDataWriter);
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
