package com.wordiq.app.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID

enum class AudioAvailability { IDLE, INITIALIZING, READY, UNAVAILABLE }

interface PronunciationPlayer {
    val availability: AudioAvailability
    fun speakFinnish(text: String, onUnavailable: () -> Unit = {})
    fun stop()
    fun release()
}

/** Android TTS implementation kept behind [PronunciationPlayer] for later recorded audio. */
class AndroidFinnishTtsPlayer(context: Context) : PronunciationPlayer, TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val finnish = Locale.forLanguageTag("fi-FI")
    private var engine: TextToSpeech? = null
    private var pending: Pair<String, () -> Unit>? = null

    override var availability: AudioAvailability = AudioAvailability.IDLE
        private set

    override fun onInit(status: Int) {
        val tts = engine
        if (status != TextToSpeech.SUCCESS || tts == null || tts.isLanguageAvailable(finnish) < TextToSpeech.LANG_AVAILABLE) {
            availability = AudioAvailability.UNAVAILABLE
            pending?.second?.let(::post)
            pending = null
            return
        }
        tts.language = finnish
        tts.voices
            ?.asSequence()
            ?.filter { it.locale.language == finnish.language && !it.isNetworkConnectionRequired }
            ?.maxByOrNull { it.quality }
            ?.let { tts.voice = it }
        tts.setSpeechRate(0.92f)
        availability = AudioAvailability.READY
        pending?.also { (text, unavailable) -> speakFinnish(text, unavailable) }
        pending = null
    }

    override fun speakFinnish(text: String, onUnavailable: () -> Unit) {
        if (text.isBlank()) return
        when (availability) {
            AudioAvailability.IDLE -> {
                pending = text to onUnavailable
                availability = AudioAvailability.INITIALIZING
                engine = TextToSpeech(appContext, this)
            }
            AudioAvailability.INITIALIZING -> pending = text to onUnavailable
            AudioAvailability.UNAVAILABLE -> post(onUnavailable)
            AudioAvailability.READY -> {
                val result = engine?.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    Bundle(),
                    "wordiq-${UUID.randomUUID()}",
                )
                if (result == TextToSpeech.ERROR) post(onUnavailable)
            }
        }
    }

    override fun stop() {
        pending = null
        engine?.stop()
    }

    override fun release() {
        pending = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        availability = AudioAvailability.UNAVAILABLE
    }

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }
}
