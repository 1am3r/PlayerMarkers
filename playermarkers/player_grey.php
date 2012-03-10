<?php
	$username = preg_replace('/[^a-zA-Z0-9_]/', '', $_SERVER['QUERY_STRING']);

	//Headers.
	header("Content-Type: image/png");
	header("Content-Disposition: inline; filename={$username}.png");

	//Try to make the cache dir if it doesn't exist.
	if(!file_exists("./cache/")) mkdir("./cache/");

	//If it's cached, then yay.
	if(file_exists("./cache/$username_grey.png") && filemtime("./cache/$username_grey.png") > time()-86400) {
		readfile("./cache/$username_grey.png");
		die();
	}

	$src = @imagecreatefrompng("http://s3.amazonaws.com/MinecraftSkins/{$username}.png");
	if(!$src) {
		//Display the old one, even if it's outdated (better than nothing).
		if(file_exists("./cache/$username_grey.png")) {
			readfile("./cache/$username_grey.png");
			die();
		}

		readfile("player_grey.png");
		die();
	}

	imagefilter($src, IMG_FILTER_GRAYSCALE);

	$img = imagecreatetruecolor(16,32);
	imagealphablending($img,false);
	imagesavealpha($img,true);

	imagefill($img,0,0,imagecolorallocatealpha($img,255,0,255,127));

	imagecopy($img,$src,			4,	0,	8,	8,	8,	8);				//Head
	imagecopy($img,$src,			4,	8,	20,	20,	8,	12);			//Body
	imagecopy($img,$src,			0,	8,	44,	20,	4,	12);			//Arm-L
	imagecopyresampled($img,$src,	12,	8,	47,	20,	4,	12,	-4,	12);	//Arm-R
	imagecopy($img,$src,			4,	20,	4,	20,	4,	12);			//Leg-L
	imagecopyresampled($img,$src,	8,	20,	7,	20,	4,	12,	-4,	12);	//Leg-R

	//Enable alpha blending so hat blends with face.
	imagealphablending($img,true);
	imagecopy($img,$src,			4,	0,	40,	8,	8,	8);				//Hat

	if(!file_exists("./cache/")) mkdir("./cache/");
	imagepng($img,"./cache/$username_grey.png");
	imagepng($img);
?>
