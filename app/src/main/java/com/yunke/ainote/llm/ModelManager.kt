package com.yunke.ainote.llm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

enum class ModelState { IDLE, LOADING, LOADED, ERROR }

/** 内置模型从 assets 拷贝到私有目录的状态。 */
enum class BuiltinStatus { NOT_COPIED, COPYING, READY, ERROR }

/**
 * 模型与推理引擎管理：内置模型拷贝、模型目录扫描、加载状态、加载/释放/对话。
 * native 侧通过 LlmEngine 桥接 llama.cpp 加载 GGUF。
 */
class ModelManager(context: Context) {

    private val appContext = context.applicationContext
    private val engine = LlmEngine()

    private val _state = MutableStateFlow(ModelState.IDLE)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _currentModel = MutableStateFlow<String?>(null)
    val currentModel: StateFlow<String?> = _currentModel.asStateFlow()

    private val _builtinStatus = MutableStateFlow(BuiltinStatus.NOT_COPIED)
    val builtinStatus: StateFlow<BuiltinStatus> = _builtinStatus.asStateFlow()

    private val _builtinProgress = MutableStateFlow(0f)
    val builtinProgress: StateFlow<Float> = _builtinProgress.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** 最近一次本地模型加载/对话失败原因，供界面透出。 */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val modelDir: File = File(appContext.filesDir, "models").apply { mkdirs() }

    companion object {
        /** 内置 GGUF 在 assets 下的相对路径与落地文件名。 */
        const val BUILTIN_MODEL_NAME = "Qwen3-0.6B-Q4_0.gguf"
        private const val ASSET_PATH = "models/$BUILTIN_MODEL_NAME"
    }

    /**
     * 确保内置模型已从 assets 拷贝到私有模型目录。
     * 已就位则直接返回；否则流式拷贝并更新进度。须在 IO 线程调用。
     */
    fun ensureBuiltin() {
        val target = File(modelDir, BUILTIN_MODEL_NAME)
        if (target.exists() && target.length() > 0) {
            _builtinStatus.value = BuiltinStatus.READY
            _builtinProgress.value = 1f
            return
        }

        _builtinStatus.value = BuiltinStatus.COPYING
        _builtinProgress.value = 0f

        runCatching {
            val total = runCatching { appContext.assets.openFd(ASSET_PATH).use { it.length } }.getOrDefault(0L)
            appContext.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(8 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        copied += n
                        _builtinProgress.value = if (total > 0) copied.toFloat() / total else 0f
                    }
                }
            }
            if (target.length() <= 0) throw IllegalStateException("内置模型拷贝为空")
            _builtinStatus.value = BuiltinStatus.READY
            _builtinProgress.value = 1f
        }.onFailure {
            target.delete()
            _builtinStatus.value = BuiltinStatus.ERROR
            _builtinProgress.value = 0f
        }
    }

    fun listModels(): List<File> =
        modelDir.listFiles { f -> f.isFile && f.extension == "gguf" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun load(modelPath: String) {
        _state.value = ModelState.LOADING
        try {
            engine.load(modelPath)
            _currentModel.value = File(modelPath).name
            _lastError.value = null
            _state.value = ModelState.LOADED
        } catch (t: Throwable) {
            _currentModel.value = null
            _lastError.value = t.message ?: t.javaClass.simpleName
            _state.value = ModelState.ERROR
        }
    }

    /** 内置模型文件是否已就位。 */
    fun builtinFile(): File = File(modelDir, BUILTIN_MODEL_NAME)

    /** 内置模型就位且尚未加载时，自动加载内置 GGUF；须在 IO 线程调用。 */
    fun autoLoadBuiltin() {
        if (_state.value == ModelState.LOADED) return
        val f = builtinFile()
        if (f.exists() && f.length() > 0) {
            load(f.absolutePath)
        }
    }

    fun chat(prompt: String): String = engine.chat(prompt)

    fun release() {
        engine.release()
        _currentModel.value = null
        _lastError.value = null
        _state.value = ModelState.IDLE
    }
}
