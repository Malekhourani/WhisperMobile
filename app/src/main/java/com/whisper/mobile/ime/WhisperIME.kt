package com.whisper.mobile.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.whisper.mobile.WhisperLib
import com.whisper.mobile.audio.AudioRecorder
import com.whisper.mobile.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class WhisperIME : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var scope: CoroutineScope? = null
    @Volatile
    private var whisperContext: Long = 0L
    private val contextLock = ReentrantLock()
    private lateinit var modelManager: ModelManager
    private lateinit var audioRecorder: AudioRecorder

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        modelManager = ModelManager(this)
        audioRecorder = AudioRecorder(this)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        loadModel()
    }

    private fun loadModel() {
        val activeModel = modelManager.getActiveModel() ?: return
        val path = modelManager.getModelPath(activeModel) ?: return
        scope?.launch(Dispatchers.IO) {
            contextLock.withLock {
                if (whisperContext != 0L) {
                    WhisperLib.freeContext(whisperContext)
                }
                whisperContext = WhisperLib.initContext(path)
            }
        }
    }

    override fun onCreateInputView(): View {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@WhisperIME)
            setViewTreeSavedStateRegistryOwner(this@WhisperIME)
            setContent {
                ImeUi(
                    audioRecorder = audioRecorder,
                    onTranscribe = { audioData -> transcribe(audioData) },
                    language = modelManager.getLanguagePreference(),
                    onLanguageChange = { lang -> modelManager.setLanguagePreference(lang) },
                    isModelLoaded = whisperContext != 0L,
                    onOpenSettings = {
                        val intent = packageManager.getLaunchIntentForPackage(packageName)
                        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (intent != null) startActivity(intent)
                    }
                )
            }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return view
    }

    private fun transcribe(audioData: FloatArray): String {
        return contextLock.withLock {
            val ctx = whisperContext
            if (ctx == 0L) return@withLock ""
            val lang = modelManager.getLanguagePreference()
            WhisperLib.transcribe(ctx, audioData, lang)
        }
    }

    fun commitTranscription(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        audioRecorder.release()
        scope?.cancel()
        contextLock.withLock {
            if (whisperContext != 0L) {
                WhisperLib.freeContext(whisperContext)
                whisperContext = 0L
            }
        }
        super.onDestroy()
    }
}
