var playerMarkers = [];
var infoWindowsArray = [];
var foundPlayerMarkers = null;
var validDimensions = ["", " - overworld", " - nether", " - end"];
var imageURL = "playermarkers/player.php";

setInterval(updatePlayerMarkers, 1000 * 3);
setTimeout(updatePlayerMarkers, 1000);

function prepareInfoWindow(infoWindow, item) {
	var online = (item.id == 4 ? "Online" : (item.id == 5 ? "Offline" : ""))
	var c = 
    "<div class=\"infoWindow\" style='width: 300px'><img src='playermarkers/player.php?online?big?"+item.msg+"'/>\
    <h1>&nbsp;"+item.msg+"</h1>\
    <p style='text-align: left;'>&nbsp;"+online+"<br />\
    &nbsp;X: "+item.x+"<br />\
    &nbsp;Y: "+item.y+"<br />\
    &nbsp;Z: "+item.z+"</p></div>";
	if (c != infoWindow.getContent())
		infoWindow.setContent(c);
}

function updatePlayerMarkers() {
	$.getJSON('markers.json?'+Math.round(new Date().getTime()), function(data) {
		var foundPlayerMarkers = [];
		for (i in playerMarkers)
			foundPlayerMarkers.push(false);

		var curWorld = overviewer.mapView.options.currentTileSet.attributes.world.id;
		for (i in data) {
			var item = data[i];
			if(item.id != 4 && item.id != 5) continue; // only online and offline players
			
			var onCurWorld = false;
			for (var i=0; i<validDimensions.length; i++) {
				if (item.world + validDimensions[i] == curWorld) {
					onCurWorld = true;
					break;
				}
			}
			if (!onCurWorld) continue;

			var converted = overviewer.util.fromWorldToLatLng(item.x, item.y, item.z, overviewer.mapView.options.currentTileSet);
			var playerOnline = (item.id == 4 ? "online" : (item.id == 5 ? "offline" : ""))

			//if found, change position
			var found = false;
			for (player in playerMarkers) {
				if(playerMarkers[player].getTitle() == item.msg) {
					foundPlayerMarkers[player] = found = true;
					playerMarkers[player].setPosition(converted);
					playerMarkers[player].setIcon(imageURL+'?'+playerOnline+"?small?"+item.msg);
					if(playerMarkers[player].getMap() == null)
						playerMarkers[player].setMap(overviewer.map);
					prepareInfoWindow(infoWindowsArray[player], item);
					break;
				}
			}
			//else new marker
			if(!found) {
				var marker =  new google.maps.Marker({
					position: converted,
					map: overviewer.map,
					title: item.msg,
					icon: imageURL+'?'+playerOnline+"?small?"+item.msg,
					visible: true,
					zIndex: 999
				});
				playerMarkers.push(marker);
				
				var infowindow = new google.maps.InfoWindow({content: item.msg});
				prepareInfoWindow(infowindow, item);
				google.maps.event.addListener(marker, 'click', function(event) {
					var i = 0;
					for (playerindex in playerMarkers) {
						if (playerMarkers[playerindex].title == this.title) {
							i = playerindex;
							break;
						}
					}
					if(infoWindowsArray[i].getMap()){
						infoWindowsArray[i].close()
					} else {
						infoWindowsArray[i].open(overviewer.map, playerMarkers[i]);
					}
				});
				infoWindowsArray.push(infowindow);
				foundPlayerMarkers.push(true);
			}
		}

		//remove unused markers
		for (i in playerMarkers) {
			if (!foundPlayerMarkers[i]) {
				playerMarkers[i].setMap(null);
			}
		}
	});
}
