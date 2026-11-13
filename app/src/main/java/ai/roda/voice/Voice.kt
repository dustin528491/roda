package ai.roda.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.listener.EventListener as TtsEvents
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import ai.roda.core.Link
import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * 음성 출력과 음성 인식.
 * 차량에서는 Pleos TextToSpeech·SpeechToText SDK를 쓰고(ON_DEVICE 기본, 마이크 소리는 차 밖으로 보내지 않음),
 * Pleos 음성 서비스가 없는 기기(시연용 태블릿 등)에서는 안드로이드 기본 TTS로 말하고 인식은 시연용 답변으로 대신한다.
 */
class Voice(private val context: Context, private val onStatus: (stt: Link?, tts: Link?) -> Unit) {
    private val tag = "Roda/Voice"

    private var pleosTts: PleosTts? = null
    private var pleosStt: SpeechToText? = null
    private var androidTts: AndroidTts? = null

    @Volatile var ttsReady = false; private set
    @Volatile var sttReady = false; private set
    @Volatile var usingPleosTts = false; private set

    private var speakDone: CompletableDeferred<Boolean>? = null
    private var listenDone: CompletableDeferred<String>? = null
    private var partial = ""

    /** 인식 중 실시간 글자. 화면에 보여 준다. */
    var onPartial: ((String) -> Unit)? = null
    var onLevel: ((Float) -> Unit)? = null

    fun start() {
        startPleosTts()
        startPleosStt()
    }

    private fun startPleosTts() {
        try {
            val t = PleosTts(context.applicationContext, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
            pleosTts = t
            t.initialize(object : TtsInit {
                override fun onSuccess() {
                    runCatching { t.addEventListener(ttsEvents) }
                    ttsReady = true; usingPleosTts = true
                    onStatus(null, Link.CONNECTED)
                }
                override fun onFailure(e: ai.pleos.playground.tts.constant.ErrorType) {
                    Log.w(tag, "pleos tts init failed: $e"); fallbackTts()
                }
            })
        } catch (e: Throwable) {
            Log.w(tag, "pleos tts unavailable", e); fallbackTts()
        }
    }

    private val ttsEvents = object : TtsEvents {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() { speakDone?.complete(true) }
        override fun onError(e: ai.pleos.playground.tts.constant.ErrorType) { Log.w(tag, "tts error $e"); speakDone?.complete(false) }
        override fun onStop() { speakDone?.complete(false) }
        override fun onUpdatedRms(rms: Double) {}
    }

    private fun fallbackTts() {
        if (androidTts != null) return
        usingPleosTts = false
        androidTts = AndroidTts(context.applicationContext) { code ->
            ttsReady = code == AndroidTts.SUCCESS
            onStatus(null, if (ttsReady) Link.DEMO else Link.UNAVAILABLE)
        }.also {
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { speakDone?.complete(true) }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { speakDone?.complete(false) }
                override fun onStop(id: String?, interrupted: Boolean) { speakDone?.complete(false) }
            })
        }
    }

    private fun startPleosStt() {
        try {
            val s = SpeechToText(context.applicationContext, ai.pleos.playground.stt.constant.Mode.ON_DEVICE)
            pleosStt = s
            s.initialize(object : SttInit {
                override fun onSuccess() {
                    runCatching { s.addListener(sttEvents) }
                    sttReady = true
                    onStatus(Link.CONNECTED, null)
                }
                override fun onFailure(e: ai.pleos.playground.stt.constant.ErrorType) {
                    Log.w(tag, "pleos stt init failed: $e"); onStatus(Link.DEMO, null)
                }
            })
        } catch (e: Throwable) {
            Log.w(tag, "pleos stt unavailable", e); onStatus(Link.DEMO, null)
        }
    }

    private val sttEvents = object : ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() { partial = "" }
        override fun onUpdated(text: String, completed: Boolean) {
            partial = text
            onPartial?.invoke(text)
            if (completed) listenDone?.complete(text)
        }
        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onEndedRecognition() { listenDone?.complete(partial) }
        override fun onError(e: ai.pleos.playground.stt.constant.ErrorType) { Log.w(tag, "stt error $e"); listenDone?.complete(partial) }
        override fun onUpdatedRms(rms: Double) { onLevel?.invoke(rms.toFloat()) }
    }

    /** 한 문장을 말하고 끝날 때까지 기다린다. */
    suspend fun speak(text: String, locale: Locale, timeoutMs: Long = 15_000): Boolean {
        if (text.isBlank()) return true
        if (!ttsReady) { kotlinx.coroutines.delay(estimateMs(text)); return false }
        val done = CompletableDeferred<Boolean>()
        speakDone = done
        try {
            if (usingPleosTts) {
                val t = pleosTts!!
                t.setLocale(locale)
                t.speak(text, true)
            } else {
                val t = androidTts!!
                t.language = locale
                t.speak(text, AndroidTts.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            }
        } catch (e: Throwable) {
            Log.w(tag, "speak failed", e)
            kotlinx.coroutines.delay(estimateMs(text))
            return false
        }
        return withTimeoutOrNull(timeoutMs.coerceAtLeast(estimateMs(text) * 3)) { done.await() } ?: false
    }

    /** 차량 마이크로 한 번 듣고 인식된 글자를 돌려준다. 인식기가 없으면 null. */
    suspend fun listen(locale: Locale, timeoutMs: Long = 9_000): String? {
        val s = pleosStt ?: return null
        if (!sttReady) return null
        val done = CompletableDeferred<String>()
        listenDone = done
        partial = ""
        return try {
            s.setLocale(locale)
            s.request()
            val r = withTimeoutOrNull(timeoutMs) { done.await() }
            if (r == null) runCatching { s.stop() }
            (r ?: partial).trim()
        } catch (e: Throwable) {
            Log.w(tag, "listen failed", e); null
        }
    }

    fun stopAll() {
        runCatching { pleosTts?.stop() }
        runCatching { androidTts?.stop() }
        runCatching { pleosStt?.stop() }
        speakDone?.complete(false)
        listenDone?.complete(partial)
    }

    fun release() {
        runCatching { pleosTts?.removeEventListener(ttsEvents); pleosTts?.release() }
        runCatching { pleosStt?.removeListener(sttEvents); pleosStt?.release() }
        runCatching { androidTts?.shutdown() }
    }

    private fun estimateMs(text: String) = (600L + text.length * 70L).coerceAtMost(9_000)
}
