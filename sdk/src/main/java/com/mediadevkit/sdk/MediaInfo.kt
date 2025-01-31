package com.mediadevkit.sdk

data class MdkMediaInfo(
  val startTime: Long,
  val duration: Long,
  val bitRate: Long,
  val size: Long,
  val format: String,
  val streams: Int,
  //todo: val chapters: List<Chapter>,
  //todo: val metaData: MetaData,
  val audio: List<MdkAudioStream>,
  val video: List<MdkVideoStream>,
  val subtitles: List<MdkSubtitle>,
  //todo: val programInfo: List<ProgramInfo>,
)

data class MdkAudioStream(
  override val index: Int,
  val startTime: Long,
  val duration: Long,
  val frames: Long,
  val metaData: Map<String?, String?>,
  val codec: MdkAudioCodec,
) : MdkStream

data class MdkAudioCodec(
  val codec: String,
  val codecTag: Int,
  //todo: val extraData: ByteArray,
  val bitRate: Long,
  val profile: Int,
  val level: Int,
  val frameRate: Float,
  val isFloat: Boolean,
  val isUnsigned: Boolean,
  val isPlanar: Boolean,
  val rawSampleSize: Int,
  val channels: Int,
  val sampleRate: Int,
  val blockAlign: Int,
  val frameSize: Int,
)


data class MdkVideoStream(
  override val index: Int,
  val startTime: Long,
  val duration: Long,
  val frames: Long,
  val rotation: Int,
  val metaData: Map<String?, String?>,
  val codec: MdkVideoCodec,
  //todo: val imageData: ByteArray,
  //todo: val imageSize: Int,
) : MdkStream

data class MdkVideoCodec(
  val codec: String,
  val codecTag: Int,
  //todo: val extraData: ByteArray,
  //todo: val extraDataSize: Int,
  val bitRate: Long,
  val profile: Int,
  val level: Int,
  val frameRate: Float,
  val format: Int,
  val formatName: String,
  val width: Int,
  val height: Int,
  val bFrames: Int,
  val par: Float,
  val colorSpace: Int,
  val dolbyVisionProfile: Int,
)

data class MdkSubtitle(
  override val index: Int,
  val startTime: Long,
  val duration: Long,
  val metaData: Map<String?, String?>,
  val codec: MdkSubtitleCodec,
) : MdkStream

sealed interface MdkStream { val index: Int }

data class MdkSubtitleCodec(
  val codec: String,
  val codecTag: Int,
  //todo: val extraData: ByteArray,
  //todo: val extraDataSize: Int,
  //todo: val width: Int,
  //todo: val height: Int,
)

