package org.havenapp.main.ui.viewholder

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.github.derlio.waveform.SimpleWaveformView
import nl.changer.audiowife.AudioWife
import org.havenapp.main.R
import org.havenapp.main.model.EventTrigger
import org.havenapp.main.resources.IResourceManager
import org.havenapp.main.audio.DecodedAudio
import java.io.File
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat

/**
 * Created by Arka Prava Basu<arkaprava94@gmail.com> on 21/02/19
 **/
class AudioVH(
    private val resourceManager: IResourceManager,
    viewGroup: ViewGroup,
    var spectrogramEnabled: Boolean = false
)
    : RecyclerView.ViewHolder(LayoutInflater.from(viewGroup.context)
        .inflate(R.layout.item_audio, viewGroup, false)) {

    private val indexNumber = itemView.findViewById<TextView>(R.id.index_number)
    private val audioTitle = itemView.findViewById<TextView>(R.id.title)
    private val audioDesc = itemView.findViewById<TextView>(R.id.item_audio_desc)
    private val waveFormView = itemView.findViewById<SimpleWaveformView>(R.id.item_sound)
    private val spectrogramView = itemView.findViewById<SpectrogramView>(R.id.item_spectrogram)
    private val playerContainer = itemView.findViewById<LinearLayout>(R.id.item_player_container)
    private var loadJob: Job? = null
    private var playerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    fun bind(eventTrigger: EventTrigger, context: Context, position: Int) {
        indexNumber.text = "#${position + 1}"
        audioTitle.text = eventTrigger.getStringType(resourceManager)
        audioDesc.text = eventTrigger.time?.toLocaleString() ?: ""

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val fileSound = File(eventTrigger.path)
        waveFormView.visibility = View.GONE
        spectrogramView.visibility = View.GONE

        loadJob?.cancel()
        loadJob = scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                decodeAudio(fileSound)
            }

            if (!isActive) return@launch

            if (decoded != null) {
                if (spectrogramEnabled) {
                    spectrogramView.visibility = View.VISIBLE
                    spectrogramView.setAudioData(
                        decoded.samples,
                        decoded.sampleRate,
                        decoded.durationSeconds
                    )
                } else {
                    waveFormView.visibility = View.VISIBLE
                }
            } else {
                waveFormView.visibility = View.VISIBLE
            }
        }

        playerJob = scope.launch {
            withContext(Dispatchers.Main) {
                playerContainer.removeAllViews()
            }
            val preparedPlayer = withContext(Dispatchers.IO) {
                try {
                    AudioWife().init(context, Uri.fromFile(fileSound))
                } catch (exception: Exception) {
                    null
                }
            }
            if (isActive && preparedPlayer != null) {
                preparedPlayer.useDefaultUi(playerContainer, inflater)
            }
        }
    }

    fun release() {
        cancelLoad()
        AudioWife.getInstance().release()
    }

    fun cancelLoad() {
        loadJob?.cancel()
        playerJob?.cancel()
    }

    private fun decodeAudio(file: File): DecodedAudio? {
        if (!file.exists()) return null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = index
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val durationMicros = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val codec = MediaCodec.createDecoderByType(mime!!)
            codec.configure(format, null, null, 0)
            codec.start()

            val output = ArrayList<Short>()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false
            var deadline = System.currentTimeMillis() + 5_000L

            while (!sawOutputEos && System.currentTimeMillis() < deadline && output.size < 44100 * 60) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outputIndex >= 0) {
                    val pcm = codec.getOutputBuffer(outputIndex)!!
                    pcm.position(info.offset)
                    pcm.limit(info.offset + info.size)
                    while (pcm.remaining() >= 2) output.add(pcm.short)
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                }
            }
            codec.stop()
            codec.release()
            if (output.isEmpty()) return null
            val samples = ShortArray(output.size)
            for ((index, value) in output.withIndex()) samples[index] = value
            val seconds = if (durationMicros > 0) durationMicros / 1_000_000f else samples.size / sampleRate.toFloat()
            return DecodedAudio(samples, sampleRate, seconds.coerceAtLeast(0.01f))
        } catch (exception: Exception) {
            return null
        } finally {
            try {
                extractor.release()
            } catch (ignored: Exception) {
            }
        }
    }
}
