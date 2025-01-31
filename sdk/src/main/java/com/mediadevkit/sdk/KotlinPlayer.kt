package com.mediadevkit.sdk

import android.os.Handler
import android.os.Looper
import android.view.*

class KotlinPlayer(private val config: Config) {

  //events from native can occur on background thread, so post to listeners with this
  private val handler = Handler( Looper.getMainLooper() )

  interface Listener {
    fun onState(newValue: Int)
    fun onMediaStatus(previous: Int, next: Int) = onMediaStatus( MediaStatus(previous), MediaStatus(next) )
    fun onMediaStatus(previous: MediaStatus, next: MediaStatus)
  }

  data class Config(val decodeToSurfaceView: Boolean)

  private var nativeHandle = LibMdk.createPlayer()

  val listeners = mutableSetOf<Listener>()

  private val listener = object : Listener {
    override fun onState(newValue: Int) {
      handler.post { for (it in listeners) it.onState(newValue) }
    }
    override fun onMediaStatus(previous: MediaStatus, next: MediaStatus) {
      handler.post {
        for (it in listeners) it.onMediaStatus(previous, next)
      }
    }
  }

  /**
   * if set subtitle track with decodeToSurfaceView == true && AMediaCodec:async=1: freeze
   */
  init {
    LibMdk.setListener(nativeHandle, listener)
    val videoDecoders = listOf(
      "AMediaCodec:dv=1:acquire=latest:ndk_codec=1:java=0:copy=0:surface=1:image=1:async=1:low_latency=1",
      "FFmpeg",
    )
    LibMdk.setProperty(
      handle = nativeHandle,
      key = "video.decoders",
      value = videoDecoders.joinToString(separator = ",")
    )
  }

  var nativeWindow = 0L
    set(value) {
      if (config.decodeToSurfaceView) {
        LibMdk.setProperty(nativeHandle, "video.decoder", "surface=$value")
      }
      LibMdk.deleteGlobalRef(field)
      field = value
    }

  var state: Int
    get() = LibMdk.getState(nativeHandle)
    set(value) = LibMdk.setState(nativeHandle, value)

  var media: String? = null
    set(value) {
      field = value
      LibMdk.setMedia(nativeHandle, value)
    }

  var position: Long
    get() = LibMdk.getPosition(nativeHandle)
    set(value) = LibMdk.seek(nativeHandle, value)

  var hdr: Boolean = false
    set(value) {
      field = value
      LibMdk.setColorSpace(nativeHandle, nativeWindow, if (hdr) 1 else 0)
    }

  //var hardwareDecoding: Boolean = false

  val mediaInfo: MdkMediaInfo?
    get() = LibMdk.getMediaInfo(nativeHandle)

  fun setSurface(view: SurfaceView?) {
    this.surfaceView = view
  }

  fun release() {
    LibMdk.release(nativeHandle)
  }

  fun setProperty(key: String, value: String) = LibMdk.setProperty(nativeHandle, key, value)

  val callback = object : SurfaceHolder.Callback {

    override fun surfaceCreated(holder: SurfaceHolder) {
      nativeWindow = LibMdk.newGlobalRef(holder.surface)
      if (config.decodeToSurfaceView) return
      LibMdk.updateNativeSurface(handle = nativeHandle, 0L, 0, 0, 0)
      LibMdk.updateNativeSurface(handle = nativeHandle, nativeWindow, -1, -1, 0)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
      nativeWindow = 0L
      if (config.decodeToSurfaceView) return
      LibMdk.updateNativeSurface(handle = nativeHandle, 0L, 0, 0, 0)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
      if (config.decodeToSurfaceView) return
      LibMdk.updateNativeSurface(handle = nativeHandle, nativeWindow, width, height, 0)
    }
  }

  private var surfaceView: SurfaceView? = null
    set(newValue) {
      if (field === newValue) return
      field?.holder?.removeCallback(callback)
      newValue?.holder?.addCallback(callback)
      field = newValue
    }

}

