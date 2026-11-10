package com.example.mikayala.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Fusion de segments audio M4A (AAC) enregistrés successivement (Mission 03).
 *
 * Pourquoi : sur Android 6 (API < 24), MediaRecorder n'a PAS pause()/resume().
 * La seule façon fiable de proposer « pause puis reprise » est d'enregistrer
 * CHAQUE partie dans un fichier temporaire distinct, puis d'ASSEMBLER proprement
 * ces segments en un seul M4A à la fin. Réutiliser setOutputFile() sur un MP4
 * existant n'ajoute RIEN : cela réécrit le fichier depuis le début (corruption).
 *
 * Fusion : MediaExtractor (lecture des échantillons AAC) + MediaMuxer (écriture
 * dans un conteneur MP4 unique), avec décalage des horodatages par segment.
 * Disponible depuis API 18 — compatible minSdk 23.
 */
object AudioSegmentsMerger {

    /**
     * Partie pure (testable en JVM sans Android) : garde uniquement les segments
     * réellement exploitables (existent et non vides), dans l'ordre donné.
     */
    fun planValidSegments(files: List<File>): List<File> {
        return files.filter { it.exists() && it.length() > 0L }
    }

    /**
     * Partie pure (testable en JVM) : décalages cumulés des horodatages.
     * Chaque segment démarre après la fin du précédent (+ 20 ms de garde
     * pour éviter les chevauchements d'échantillons).
     */
    fun computeTimeOffsetsUs(durationsUs: List<Long>): List<Long> {
        val offsets = mutableListOf<Long>()
        var acc = 0L
        for (d in durationsUs) {
            offsets.add(acc)
            acc += d + 20_000L
        }
        return offsets
    }

    /**
     * Fusionne [segments] (fichiers M4A/AAC, mêmes réglages d'enregistrement)
     * dans [outputFile]. Retourne true si [outputFile] contient un M4A complet.
     *
     * Cas particuliers :
     * - aucun segment valide -> false (l'appelant gère l'erreur) ;
     * - un seul segment valide -> simple copie (aucun remux inutile) ;
     * - plus d'un segment -> remux complet avec horodatages recalculés.
     */
    fun mergeM4aSegments(segments: List<File>, outputFile: File): Boolean {
        val valid = planValidSegments(segments)
        if (valid.isEmpty()) return false

        if (valid.size == 1) {
            return try {
                valid[0].copyTo(outputFile, overwrite = true)
                true
            } catch (e: Exception) {
                false
            }
        }

        var muxer: MediaMuxer? = null
        val extractors = mutableListOf<MediaExtractor>()
        try {
            // 1. Préparer les extracteurs et lire les durées AVANT toute lecture
            val audioTrackIndices = mutableListOf<Int>()
            val durationsUs = mutableListOf<Long>()
            for (file in valid) {
                val ex = MediaExtractor()
                extractors.add(ex)
                ex.setDataSource(file.absolutePath)
                var track = -1
                for (i in 0 until ex.trackCount) {
                    val fmt = ex.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                        track = i
                        break
                    }
                }
                if (track == -1) return false
                ex.selectTrack(track)
                audioTrackIndices.add(track)
                val fmt = ex.getTrackFormat(track)
                durationsUs.add(if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L)
            }

            val offsetsUs = computeTimeOffsetsUs(durationsUs)

            // 2. Muxer de sortie : une seule piste audio (format du premier segment)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(extractors[0].getTrackFormat(audioTrackIndices[0]))
            muxer.start()

            // 3. Copier les échantillons de chaque segment avec son décalage
            val buffer = ByteBuffer.allocate(1 shl 16)
            for (index in valid.indices) {
                val ex = extractors[index]
                ex.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                while (true) {
                    buffer.clear()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    val info = MediaCodec.BufferInfo()
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = ex.sampleTime + offsetsUs[index]
                    info.flags = ex.sampleFlags and MediaCodec.BUFFER_FLAG_KEY_FRAME.inv()
                    if (index == 0 && ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        info.flags = info.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                    }
                    muxer.writeSampleData(outTrack, buffer, info)
                    ex.advance()
                }
            }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            for (ex in extractors) {
                try { ex.release() } catch (ignored: Exception) {}
            }
            try { muxer?.stop() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }
}
