package com.github._1am3r.MapMarkers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MapMarkersPlayerListener implements Listener {
	private final MapMarkers plugin;
	
	public MapMarkersPlayerListener(MapMarkers instance) {
		plugin = instance;
	}
	
	@EventHandler
	public void playerJoin(PlayerJoinEvent event) {
		plugin.updatePlayer(event.getPlayer());
	}
	
	@EventHandler
	public void playerQuit(PlayerQuitEvent event) {
		plugin.removePlayer(event.getPlayer());
	}
}
