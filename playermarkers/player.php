<?php
  $queryStr = preg_replace('/[^a-zA-Z0-9_?]/', '', $_SERVER['QUERY_STRING']);
  list($online, $size, $username) = explode("?", $querystr, 3) + Array("online", "small", "player");
  $username = preg_replace('/[^a-zA-Z0-9_]/', '', $username);

  $pictureOnline = "";
  if ($online != "online") {
    $pictureOnline .= ".grey";
  }

  $pictureSize = "";
  if ($size == "big") {
    $pictureSize .= ".big";
  }
  $filename = $username . $pictureOnline . $pictureSize;

  // Send the headers.
  header("Content-Type: image/png");
  header("Content-Disposition: inline; filename={$filename}.png");

  // Try to make the cache dir if it doesn't exist.
  if(!file_exists("./cache/")) mkdir("./cache/");

  // If the image is already cached and not too old, use the cached one.
  if(file_exists("./cache/$filename.png") && filemtime("./cache/$filename.png") > time() - 86400) {
    readfile("./cache/$filename.png");
    die();
  }

  // Get the requested skin
  $src = @imagecreatefrompng("http://s3.amazonaws.com/MinecraftSkins/{$username}.png");
  if(!$src) {
    // Did not get a skin, display the old one, even if it's older than our cache time
    if(file_exists("./cache/$filename.png")) {
      readfile("./cache/$filename.png");
      die();
    }

    // Got no skin and had non cached, send the default skin
    readfile("player$pictureOnline$pictureSize.png");
    die();
  }

  if ($online != "online") {
    // Greyscale image if the player is offline
    imagefilter($src, IMG_FILTER_GRAYSCALE);
  }

  // Small image
  $img = imagecreatetruecolor(16, 32);
  imagealphablending($img, false);
  imagesavealpha($img, true);
  // Make the empty image transparent
  imagefill($img, 0, 0, imagecolorallocatealpha($img, 255, 0, 255, 127));

  // Copy the parts of the skin
  imagecopy         ($img, $src,  4,  0,  8,  8, 8,   8);         //Head
  imagecopy         ($img, $src,  4,  8, 20, 20, 8, 12);          //Body
  imagecopy         ($img, $src,  0,  8, 44, 20, 4, 12);          //Arm-L
  imagecopyresampled($img, $src, 12,  8, 47, 20, 4, 12, -4, 12);  //Arm-R (negative width to mirror)
  imagecopy         ($img, $src,  4, 20,  4, 20, 4, 12);          //Leg-L
  imagecopyresampled($img, $src,  8, 20,  7, 20, 4, 12, -4, 12);  //Leg-R (negative width to mirror)
  imagealphablending($img, true); //Enable alpha blending so hat blends with face.
  imagecopy         ($img, $src,  4,  0, 40,  8, 8,   8);         //Hat

  // Save the small image to the cache
  imagepng($img,"./cache/$username$pictureOnline.png");

  // Big image
  $imgBig = imagecreatetruecolor(64, 128);
  imagealphablending($imgBig, false);
  imagesavealpha($imgBig, true);
  // Make the empty image transparent
  imagefill($imgBig, 0, 0, imagecolorallocatealpha($imgBig, 255, 0, 255, 127));
  imagecopyresampled($imgBig, $img, 0, 0, 0, 0, 64, 128, 16, 32);

  // Save the big image to the cache
  imagepng($imgBig,"./cache/$username$pictureOnline" . ".big.png");

  // Deliver the image
  if ($size != "big") {
    imagepng($img);
  } else {
    imagepng($imgBig);
  }
?>
