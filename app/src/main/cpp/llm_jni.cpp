#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <thread>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "ainote-llm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct EngineCtx {
    llama_model*   model  = nullptr;
    llama_context* ctx   = nullptr;
    const llama_vocab* vocab = nullptr;
};

const char* kSystemPrompt =
    "你是离线笔记本助手。依据用户给出的记录回答，简洁、准确；"
    "记录里没有的内容不要编造，不知道就说明不知道。";

int pick_threads() {
    unsigned int hc = std::thread::hardware_concurrency();
    if (hc == 0) hc = 4;
    return static_cast<int>(hc > 4 ? 4 : hc);
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_yunke_ainote_llm_LlmEngine_nativeLoad(JNIEnv* env, jobject /*thiz*/,
                                               jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        return 0;
    }
    LOGI("nativeLoad: %s", path);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;  // 纯 CPU 端侧推理

    llama_model* model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (model == nullptr) {
        LOGE("llama_model_load_from_file failed");
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = 2048;
    cparams.n_batch         = 512;
    cparams.n_threads      = pick_threads();
    cparams.n_threads_batch = pick_threads();

    llama_context* ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("llama_init_from_model failed");
        llama_model_free(model);
        return 0;
    }

    auto* e = new EngineCtx();
    e->model  = model;
    e->ctx    = ctx;
    e->vocab  = llama_model_get_vocab(model);
    LOGI("model loaded, n_ctx=%u threads=%d", llama_n_ctx(ctx), cparams.n_threads);
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT jstring JNICALL
Java_com_yunke_ainote_llm_LlmEngine_nativeChat(JNIEnv* env, jobject /*thiz*/,
                                                jlong handle, jstring prompt) {
    auto* e = reinterpret_cast<EngineCtx*>(handle);
    if (e == nullptr || e->ctx == nullptr) {
        return env->NewStringUTF("[推理失败] 引擎未初始化");
    }

    const char* userText = env->GetStringUTFChars(prompt, nullptr);
    if (userText == nullptr) {
        return env->NewStringUTF("[推理失败] 空输入");
    }
    std::string user = userText;
    env->ReleaseStringUTFChars(prompt, userText);

    // 每次对话前清空 KV cache，保证会话独立
    llama_memory_clear(llama_get_memory(e->ctx), true);

    // 组装对话消息
    llama_chat_message msgs[2];
    msgs[0].role    = "system";
    msgs[0].content = kSystemPrompt;
    msgs[1].role    = "user";
    msgs[1].content = user.c_str();

    const char* tmpl = llama_model_chat_template(e->model, nullptr);

    std::string promptText;
    if (tmpl != nullptr) {
        int32_t need = llama_chat_apply_template(tmpl, msgs, 2, true, nullptr, 0);
        if (need > 0) {
            std::vector<char> buf(2 * need + 1, '\0');
            llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(),
                                      static_cast<int32_t>(buf.size()));
            promptText.assign(buf.data());
        }
    }
    if (promptText.empty()) {
        // 兜底：Qwen3 风格手写模板
        promptText = std::string("<|im_start|>system\n") + kSystemPrompt +
                     "<|im_end|>\n<|im_start|>user\n" + user +
                     "<|im_end|>\n<|im_start|>assistant\n";
    }

    // 上下文上限：为生成预留空间，避免 prompt 超长在 mul_mat 阶段越界 SIGSEGV
    const uint32_t nCtx   = llama_n_ctx(e->ctx);
    const int32_t  maxGen = 256;
    int32_t promptMax = static_cast<int32_t>(nCtx) - maxGen - 64;
    if (promptMax < 64) {
        promptMax = 64;
    }

    // 分词：BPE token 数不超过 UTF-8 字节数，先按字节数 +16 给足缓冲；
    // 该版本 llama_tokenize 在容量不足时返回 -所需数，按绝对值扩容重试一次。
    int32_t textLen = static_cast<int32_t>(promptText.size());
    int32_t cap = textLen + 16;
    std::vector<llama_token> tokens(cap);
    int32_t ntok = llama_tokenize(e->vocab, promptText.c_str(), textLen,
                                  tokens.data(), cap, true, true);
    if (ntok < 0) {
        int32_t need = -ntok;
        tokens.resize(need);
        ntok = llama_tokenize(e->vocab, promptText.c_str(), textLen,
                              tokens.data(), need, true, true);
    }
    if (ntok <= 0) {
        return env->NewStringUTF("[推理失败] 分词失败");
    }
    tokens.resize(ntok);

    // 超上下文则保留首 token（特殊标记）+ 尾部最近内容，截断中间部分
    if (ntok > promptMax) {
        LOGI("prompt tokens %d exceed cap %d, truncating", ntok, promptMax);
        llama_token head = tokens.front();
        std::vector<llama_token> kept(tokens.end() - (promptMax - 1), tokens.end());
        tokens.clear();
        tokens.push_back(head);
        tokens.insert(tokens.end(), kept.begin(), kept.end());
        ntok = promptMax;
    }

    // 显式位置 0..ntok-1，避免依赖自动位置追踪在清缓存后出现偏移
    std::vector<llama_pos> pos(ntok);
    for (int32_t i = 0; i < ntok; ++i) {
        pos[i] = i;
    }

    // 编码 prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), ntok);
    batch.pos = pos.data();
    if (llama_decode(e->ctx, batch) != 0) {
        return env->NewStringUTF("[推理失败] prompt 解码失败");
    }

    // 采样链：top_k -> top_p -> temp -> 随机采样
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.5f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    std::string output;
    char piece[256];
    int32_t genPos = ntok;

    for (int32_t i = 0; i < maxGen; ++i) {
        if (genPos >= static_cast<int32_t>(nCtx)) {
            LOGI("reach n_ctx %u, stop generation", nCtx);
            break;
        }
        llama_token id = llama_sampler_sample(smpl, e->ctx, -1);
        if (llama_vocab_is_eog(e->vocab, id)) {
            break;
        }
        int32_t n = llama_token_to_piece(e->vocab, id, piece, sizeof(piece), 0, true);
        if (n > 0) {
            output.append(piece, static_cast<size_t>(n));
        }
        llama_sampler_accept(smpl, id);

        llama_pos nextPos = genPos;
        llama_batch next = llama_batch_get_one(&id, 1);
        next.pos = &nextPos;
        if (llama_decode(e->ctx, next) != 0) {
            break;
        }
        ++genPos;
    }

    llama_sampler_free(smpl);
    LOGI("chat done, prompt=%d tokens, output=%zu chars", ntok, output.size());

    if (output.empty()) {
        return env->NewStringUTF("[推理失败] 模型未生成内容");
    }
    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_yunke_ainote_llm_LlmEngine_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/,
                                                  jlong handle) {
    auto* e = reinterpret_cast<EngineCtx*>(handle);
    if (e == nullptr) {
        return;
    }
    if (e->ctx != nullptr) {
        llama_free(e->ctx);
    }
    if (e->model != nullptr) {
        llama_model_free(e->model);
    }
    delete e;
    LOGI("engine released");
}

}  // extern "C"
