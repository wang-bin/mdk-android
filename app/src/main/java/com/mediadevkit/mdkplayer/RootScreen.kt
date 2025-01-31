package com.mediadevkit.mdkplayer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.mediadevkit.sdk.KotlinPlayer

var decodeToSurfaceView by mutableStateOf(false)

@Composable
fun RootScreen(
  modifier: Modifier = Modifier,
) {
  val (getUrl, setUrl) = remember { mutableStateOf(defaultUrl) }

  val intentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      Navigator.currentScreen = Screen.Player(
        url = uri.toString(),
        config = KotlinPlayer.Config(decodeToSurfaceView),
      )
    }
  )
  Column(
    modifier = modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(20.dp, alignment = Alignment.CenterVertically),
    content = {
      Row(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = {
          Text("Decode to SurfaceView")
          Switch(
            checked = decodeToSurfaceView,
            onCheckedChange = { decodeToSurfaceView = it },
          )
        }
      )
      Button(
        onClick = { intentLauncher.launch("video/*") },
        content = { Text("Open file") }
      )
      HorizontalDivider()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = {
          OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = getUrl,
            onValueChange = setUrl,
            label = { Text("Url") },
            singleLine = true,
          )
          Button(
            shape = MaterialTheme.shapes.small,
            onClick = {
              if ( getUrl.isBlank() ) return@Button
              Navigator.currentScreen = Screen.Player(
                url = getUrl,
                config = KotlinPlayer.Config(decodeToSurfaceView)
              )
            },
            content = { Text("Go") }
          )
        }
      )


    },
  )
}





//dv profile 5 fhd
//BL_RPU_dvhe-05_1920x1080@24fps_0_6313.mp4

//dv profile 5 uhd
//BL_RPU_dvhe-05_3840x2160@24fps_0_6313.mp4

//dv profile 8.4 fhd
//BL_RPU_dvhe-08-84_1920x1080@24fps_0_6313.mp4

//dv profile 8.4 uhd
//BL_RPU_dvhe-08-84_3840x2160@24fps_0_6313.mp4

//dv profile 8.1 fhd
//BL_RPU_dvhe-08-mapDynamic1000-81_1920x1080@24fps_0_6313.mp4

//dv profile 8.1 uhd
//BL_RPU_dvhe-08-mapDynamic1000-81_3840x2160@24fps_0_6313.mp4

val baseUrl = "https://media.githubusercontent.com/media/DolbyLaboratories/dolby-vision-contents/refs/heads/main/SolLevante_Netflix/{FILE}?download=true"
//val defaultUrl = baseUrl.replace("{FILE}", "BL_RPU_dvhe-08-mapDynamic1000-81_1920x1080@24fps_0_6313.mp4")
val defaultUrl = "https://github.com/ietf-wg-cellar/matroska-test-files/raw/master/test_files/test5.mkv"