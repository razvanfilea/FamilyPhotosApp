package net.theluckycoder.familyphotos.ui.composables.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.FileTypes
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.amr.AmrExtractor
import androidx.media3.extractor.avi.AviExtractor
import androidx.media3.extractor.avif.AvifExtractor
import androidx.media3.extractor.bmp.BmpExtractor
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.flv.FlvExtractor
import androidx.media3.extractor.heif.HeifExtractor
import androidx.media3.extractor.jpeg.JpegExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.png.PngExtractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.Ac3Extractor
import androidx.media3.extractor.ts.Ac4Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.PsExtractor
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.wav.WavExtractor
import androidx.media3.extractor.webp.WebpExtractor

/**
 * Same extractors, order and defaults as `DefaultExtractorsFactory`, but without subtitle parsing.
 *
 * `DefaultExtractorsFactory` always creates a `DefaultSubtitleParserFactory` in its constructor,
 * which keeps every subtitle parser (WebVTT, SSA, SubRip, TTML, PGS, DVB...) in the APK.
 * Subtitle tracks are ignored instead. Only the MIDI and native FLAC extensions are left out:
 * they are loaded by reflection and their modules aren't dependencies of the app.
 */
@UnstableApi
internal object NoSubtitlesExtractorsFactory : ExtractorsFactory {

    private val defaultOrder = intArrayOf(
        FileTypes.FLV,
        FileTypes.FLAC,
        FileTypes.WAV,
        FileTypes.MP4,
        FileTypes.AMR,
        FileTypes.PS,
        FileTypes.OGG,
        FileTypes.TS,
        FileTypes.MATROSKA,
        FileTypes.ADTS,
        FileTypes.AC3,
        FileTypes.AC4,
        FileTypes.MP3,
        FileTypes.AVI,
        FileTypes.JPEG,
        FileTypes.PNG,
        FileTypes.WEBP,
        FileTypes.BMP,
        FileTypes.HEIF,
        FileTypes.AVIF,
    )

    private val noSubtitles = SubtitleParser.Factory.UNSUPPORTED

    override fun createExtractors(): Array<Extractor> =
        createExtractors(Uri.EMPTY, emptyMap())

    override fun createExtractors(
        uri: Uri,
        responseHeaders: Map<String, List<String>>
    ): Array<Extractor> {
        val extractors = ArrayList<Extractor>(defaultOrder.size + 1)

        // Like DefaultExtractorsFactory: try the inferred file types first
        val headersType = FileTypes.inferFileTypeFromResponseHeaders(responseHeaders)
        if (headersType != FileTypes.UNKNOWN) addExtractors(headersType, extractors)

        val uriType = FileTypes.inferFileTypeFromUri(uri)
        if (uriType != FileTypes.UNKNOWN && uriType != headersType) addExtractors(uriType, extractors)

        for (fileType in defaultOrder) {
            if (fileType != headersType && fileType != uriType) addExtractors(fileType, extractors)
        }
        return extractors.toTypedArray()
    }

    private fun addExtractors(fileType: Int, out: MutableList<Extractor>) {
        when (fileType) {
            FileTypes.AC3 -> out += Ac3Extractor()
            FileTypes.AC4 -> out += Ac4Extractor()
            FileTypes.ADTS -> out += AdtsExtractor()
            FileTypes.AMR -> out += AmrExtractor()
            FileTypes.FLAC -> out += FlacExtractor()
            FileTypes.FLV -> out += FlvExtractor()
            FileTypes.MATROSKA -> out += MatroskaExtractor(noSubtitles, 0)
            FileTypes.MP3 -> out += Mp3Extractor()
            FileTypes.MP4 -> {
                // Since Media3 1.12, within-GOP sample dependencies are always parsed for H.264/H.265
                out += FragmentedMp4Extractor(noSubtitles, FragmentedMp4Extractor.FLAG_READ_MFRA_FOR_SEEK_MAP)
                out += Mp4Extractor(noSubtitles)
            }

            FileTypes.OGG -> out += OggExtractor()
            FileTypes.PS -> out += PsExtractor()
            FileTypes.TS -> out += TsExtractor(
                TsExtractor.MODE_SINGLE_PMT,
                0,
                noSubtitles,
                TimestampAdjuster(0),
                DefaultTsPayloadReaderFactory(0, emptyList()),
                TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
            )

            FileTypes.WAV -> out += WavExtractor()
            FileTypes.JPEG -> out += JpegExtractor()
            FileTypes.AVI -> out += AviExtractor(0, noSubtitles)
            FileTypes.PNG -> out += PngExtractor()
            FileTypes.WEBP -> out += WebpExtractor()
            FileTypes.BMP -> out += BmpExtractor()
            FileTypes.HEIF -> out += HeifExtractor()
            FileTypes.AVIF -> out += AvifExtractor()
        }
    }
}

/** [DefaultRenderersFactory] without the text (subtitle) renderer. */
@UnstableApi
internal class NoSubtitlesRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) = Unit
}
