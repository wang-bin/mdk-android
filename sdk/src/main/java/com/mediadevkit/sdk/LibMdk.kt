package com.mediadevkit.sdk

object LibMdk {

  init { System.loadLibrary("mdk-jni") }

  init {
    setGlobalOptionString("subtitle.fonts.file", "assets://fonts/font.ttf")
  }

  @JvmStatic external fun setGlobalOptionString(key: String, value: String)
  @JvmStatic external fun setGlobalOptionInt(key: String, value: Int)

  @JvmStatic external fun newGlobalRef(obj: Any): Long
  @JvmStatic external fun deleteGlobalRef(ref: Long)

  @JvmStatic external fun createPlayer(): Long

  @JvmStatic external fun setState(handle: Long, state: Int)
  @JvmStatic external fun getState(handle: Long): Int

  @JvmStatic external fun setColorSpace(handle: Long, surface: Long, colorSpace: Int)

  @JvmStatic external fun setMedia(handle: Long, url: String?)
  @JvmStatic external fun release(handle: Long)

  @JvmStatic external fun getPosition(handle: Long): Long
  @JvmStatic external fun seek(handle: Long, position: Long)

  @JvmStatic external fun setProperty(handle: Long, key: String, value: String)

  @JvmStatic external fun updateNativeSurface(handle: Long, surface: Long, width: Int, height: Int, type: Int)

  @JvmStatic external fun setListener(handle: Long, listener: KotlinPlayer.Listener)

  @JvmStatic external fun getMediaInfo(handle: Long): MdkMediaInfo?



}