package com.mediadevkit.mdkplayer

import android.view.SurfaceView
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mediadevkit.sdk.*
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlayerScreen(
  modifier: Modifier = Modifier,
  url: String,
  config: KotlinPlayer.Config,
) {
  val player = remember { KotlinPlayer(config) }
  val state = rememberPlayerState(player)
  var userValue by remember { mutableFloatStateOf(0f) }
  val sliderSource = remember(::MutableInteractionSource)
  val isSliding by sliderSource.collectIsDraggedAsState()
  var type by remember { mutableStateOf(MediaType.Unknown) }
  val lifecycleOwner = LocalLifecycleOwner.current

  val sliderValue by remember {
    derivedStateOf {
      when {
        isSliding -> userValue
        else -> state.progress
      }
    }
  }

  LaunchedEffect(
    key1 = isSliding,
    block = {
      player.state = if (isSliding) 2 else 1
    }
  )
  LaunchedEffect(
    key1 = userValue,
    block = { player.position = (state.duration * userValue.toDouble()).inWholeMilliseconds  }
  )
  DisposableEffect(
    key1 = Unit,
    effect = {
      player.state = 0
      player.media = url
      player.state = 1
      val listener = LifecycleEventObserver { _, event ->
        if (event.targetState == Lifecycle.State.CREATED) player.state = 2
      }
      lifecycleOwner.lifecycle.addObserver(listener)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(listener)
        player.release()
      }
    }
  )

  TracksDialog(
    type = type,
    onClick = { index, stream ->
      val key = when (stream) {
        is MdkAudioStream -> "audio.tracks"
        is MdkSubtitle -> "subtitle.tracks"
        is MdkVideoStream -> "video.tracks"
      }
      player.setProperty(key, "-1") //fixme: if not set to -1 before, tracks are combined !!
      player.setProperty(key, "$index")
      when (stream) {
        is MdkAudioStream -> state.audioTrack = index
        is MdkSubtitle -> state.subtitleTrack = index
        is MdkVideoStream -> state.videoTrack = index
      }
      type = MediaType.Unknown
    },
    onDismiss = { type = MediaType.Unknown },
    state = state,
  )

  Column(
    modifier = modifier.fillMaxSize(),
    content = {
      AndroidView(
        modifier = Modifier.weight(1f),
        factory = { SurfaceView(it).apply(player::setSurface) },
        onRelease = { player.setSurface(null) },
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        content = {
          IconButton(
            onClick = {
              when (state.state) {
                1 -> player.state = 2
                else -> player.state = 1
              }
            },
            content = {
              Icon(
                imageVector = when (state.state) {
                  1 -> Icons.Default.Pause
                  2 -> Icons.Default.PlayArrow
                  else -> Icons.Default.PlayArrow
                },
                contentDescription = null
              )
            }
          )
          Spacer(
            modifier = Modifier.width(12.dp),
          )
          Slider(
            modifier = modifier.weight(1f),
            interactionSource = sliderSource,
            value = sliderValue,
            onValueChange = { userValue = it },
          )
        }
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.CenterHorizontally),
        content = {
          IconButton(
            onClick = {
              state.hdr = !state.hdr
              player.hdr = state.hdr
            },
            content = {
              Icon(
                imageVector = when {
                  state.hdr -> Icons.Default.HdrOn
                  else -> Icons.Default.HdrOff
                },
                contentDescription = null,
              )
            }
          )
          IconButton(
            onClick = { type = MediaType.Audio },
            content = { Icon(Icons.Default.Audiotrack, null) }
          )
          IconButton(
            onClick = { type = MediaType.Subtitles },
            content = { Icon(Icons.Default.ClosedCaption, null) }
          )
        }
      )
    },
  )

}

@Stable
class PlayerState {
  var hdr: Boolean by mutableStateOf(false)
  var position: Duration by mutableStateOf(Duration.ZERO)
  var state: Int by mutableIntStateOf(0)
  var mediaInfo: MdkMediaInfo? by mutableStateOf(null)
  val duration: Duration by derivedStateOf { mediaInfo?.duration?.milliseconds ?: Duration.ZERO }

  val progress: Float by derivedStateOf {
    when {
      duration <= Duration.ZERO -> 0f
      else -> (position / duration).toFloat()
    }
  }

  var audioTrack: Int by mutableIntStateOf(-1)
  var videoTrack: Int by mutableIntStateOf(-1)
  var subtitleTrack: Int by mutableIntStateOf(-1)

}

@Stable
@Composable
fun rememberPlayerState(player: KotlinPlayer): PlayerState {
  val state = remember(::PlayerState)
  LaunchedEffect(
    key1 = Unit,
    block = {
      withContext(Dispatchers.IO) {
        while (isActive) {
          try {
            withContext(Dispatchers.Main) {
              state.position = player.position.milliseconds
            }
          } finally {
            delay(100)
          }
        }
      }
    },
  )
  DisposableEffect(
    key1 = Unit,
    effect = {
      val listener = object : KotlinPlayer.Listener {

        override fun onMediaStatus(previous: MediaStatus, next: MediaStatus) {
          if (state.mediaInfo == null && next.isPrepared) {
            state.mediaInfo = player.mediaInfo
            state.audioTrack = if (state.mediaInfo?.audio.isNullOrEmpty()) -1 else 0
            state.subtitleTrack = if (state.mediaInfo?.subtitles.isNullOrEmpty()) -1 else 0
            state.videoTrack = if (state.mediaInfo?.video.isNullOrEmpty()) -1 else 0
          }
        }

        override fun onState(newValue: Int) {
          state.state = newValue
        }
      }
      player.listeners += listener
      onDispose { player.listeners -= listener }
    }
  )
  return state
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksDialog(
  state: PlayerState,
  type: MediaType,
  onDismiss: () -> Unit,
  onClick: (index: Int, stream: MdkStream) -> Unit,
) {
  val isVisible by rememberUpdatedState(type != MediaType.Unknown)
  val tracks by remember(type) {
    derivedStateOf {
      when (type) {
        MediaType.Unknown -> emptyList()
        MediaType.Audio -> state.mediaInfo?.audio.orEmpty()
        MediaType.Video -> state.mediaInfo?.video.orEmpty()
        MediaType.Subtitles -> state.mediaInfo?.subtitles.orEmpty()
      }
    }
  }
  if (isVisible) {
    BasicAlertDialog(
      onDismissRequest = onDismiss,
      content = {
        Surface(
          modifier = Modifier.fillMaxWidth()
            .fillMaxHeight(.5f),
          shape = MaterialTheme.shapes.medium,
          content = {
            Column(
              modifier = Modifier.fillMaxSize(),
              content = {
                for (index in tracks.indices) {
                  val track = tracks[index]
                  val isSelected by remember {
                    derivedStateOf {
                      when (track) {
                        is MdkAudioStream -> index == state.audioTrack
                        is MdkSubtitle -> index == state.subtitleTrack
                        is MdkVideoStream -> index == state.videoTrack
                      }
                    }
                  }
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(8.dp),
                    content = {
                      Text(
                        modifier = Modifier.weight(1f),
                        text = "Track ${track.index}"
                      )
                      RadioButton(
                        selected = isSelected,
                        onClick = { onClick.invoke(index, track) },
                      )
                    }
                  )
                }
              }
            )
          }
        )
      }
    )
  }
}