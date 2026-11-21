package com.yunke.ainote.llm

/**
 * 端侧大模型推理桥。
 *
 * M2 骨架：native 侧为占位实现，仅验证 JNI/NDK 链路；
 * 后续替换为 llama.cpp 真实加载与采样。
 */
class LlmEngine {

    private var handle: Long = 0L

    private external fun nativeLoad(modelPath: String): Long
    private external fun nativeChat(handle: Long, prompt: String): String
    private external fun nativeRelease(handle: Long)

    val isLoaded: Boolean
        get() = handle != 0L

    fun load(modelPath: String) {
        check(handle == 0L) { "engine already loaded" }
        handle = nativeLoad(modelPath)
        if (handle == 0L) {
            throw IllegalStateException("native 模型加载失败")
        }
    }

    fun chat(prompt: String): String {
        check(handle != 0L) { "engine not loaded" }
        return nativeChat(handle, prompt)
    }

    fun release() {
        if (handle != 0L) {
            nativeRelease(handle)
            handle = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("ainote-llm")
        }
    }
}
