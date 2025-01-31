package com.mediadevkit.sdk

@JvmInline
value class MediaStatus(private val flags: Int) {

  private companion object {
    private const val FLAG_NO_MEDIA = 0
    private const val FLAG_UNLOADED = 1
    private const val FLAG_LOADING = 1 shl 1
    private const val FLAG_LOADED = 1 shl 2
    private const val FLAG_PREPARED = 1 shl 8
    private const val FLAG_STALLED = 1 shl 3
    private const val FLAG_BUFFERING = 1 shl 4
    private const val FLAG_BUFFERED = 1 shl 5
    private const val FLAG_END = 1 shl 6
    private const val FLAG_SEEKING = 1 shl 7
    private const val FLAG_INVALID = 1 shl 31
  }

  val isNoMedia: Boolean get() = flags == FLAG_NO_MEDIA
  val isUnloaded: Boolean get() = flags and FLAG_UNLOADED != 0
  val isLoading: Boolean get() = flags and FLAG_LOADING != 0
  val isLoaded: Boolean get() = flags and  FLAG_LOADED != 0
  val isPrepared: Boolean get() = flags and  FLAG_PREPARED != 0
  val isStalled: Boolean get() = flags and  FLAG_STALLED != 0
  val isBuffering: Boolean get() = flags and  FLAG_BUFFERING != 0
  val isBuffered: Boolean get() = flags and  FLAG_BUFFERED != 0
  val isEnd: Boolean get() = flags and  FLAG_END != 0
  val isSeeking: Boolean get() = flags and  FLAG_SEEKING != 0
  val isInvalid: Boolean get() = flags and  FLAG_INVALID != 0

  override fun toString(): String {
    return "MediaStatus(flags=$flags, isNoMedia=$isNoMedia, isUnloaded=$isUnloaded, isLoading=$isLoading, isLoaded=$isLoaded, isPrepared=$isPrepared, isStalled=$isStalled, isBuffering=$isBuffering, isBuffered=$isBuffered, isEnd=$isEnd, isSeeking=$isSeeking, isInvalid=$isInvalid)"
  }


}