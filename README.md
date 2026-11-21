# 云笺 · yunke-ainote

离线 AI 笔记本 Android 工程：本地记录 + 双通道对话（端侧 llama.cpp 推理 / OpenAI 兼容云端），对话可抽取为笔记，问答依据本地笔记回答。

- **在线展示页**：<https://180lisilence.github.io/yunke-ainote/>
- **包名**：`com.yunke.ainote`
- **版本**：versionCode 2 / versionName 0.2.0
- **平台**：Android，ABI arm64-v8a / x86_64，minSdk 24 / compileSdk 34 / targetSdk 34

## 功能亮点

- **本地记录**：笔记列表、正文编辑、文件夹分类、修订历史、回收站。
- **双通道对话**：端侧内置 GGUF 模型整段推理；云端走 OpenAI 兼容接口，支持 SSE 流式与思维链（`reasoning_content`）；通道选择持久化。
- **对话抽取入库**：模型按 JSON 契约抽取标题 / 正文 / 标签 / 人物 / 地点 / 情绪 / 待办，气泡末尾标注「📝 已存入笔记」。
- **读库问答**：先检索本地笔记标题与正文作为证据，只依据记录回答、不编造，气泡末尾标注「📎 依据」。
- **会话落库回看**：多轮历史携带到模型请求，消息持久化可回看。

## 技术栈

- Kotlin 1.9.22 + Jetpack Compose（BOM 2024.03.00，compiler 1.5.8）
- Room 2.6.1（笔记 / 修订 / 会话消息 / 文件夹）
- AGP 8.5.2，Gradle 8.14.3
- NDK 26.1.10909125，CMake 3.22.1，C++17；native 桥 `ainote-llm`（llama.cpp C API）
- 云端：纯 `HttpURLConnection` + `org.json`，OpenAI 兼容 `/chat/completions`，支持 SSE 流式与 `reasoning_content` 思维链

## 模块结构

```
app/src/main/
├── java/com/yunke/ainote/
│   ├── ai/          NoteExtractor（对话抽取为笔记，JSON 契约）
│   ├── data/       Room 实体 / DAO / Repository / 数据类型
│   ├── llm/        LlmEngine（JNI 桥）、ModelManager（模型状态/内置拷贝/加载）
│   ├── remote/     RemoteChatClient（云端非流式 + SSE 流式）
│   ├── settings/    ChatParams / Presets / SettingsStore / UsageStore / 通道枚举
│   └── ui/         Compose 屏幕、消息模型、主题、导航
├── cpp/            llm_jni.cpp（JNI 加载/采样/释放）、CMakeLists.txt
├── res/            图标与资源
└── assets/models/  内置 GGUF 模型（不入库，见下）
```

## 未入库的外部依赖（clone 后需补齐）

本仓库按 `.gitignore` 排除了体积过大或第三方的内容，补齐后才可编译 native：

1. **llama.cpp 源码**：放到 `third_party/llama.cpp/`（与 `:app` 平级），版本 v1.0.0。
   CMake 通过 `add_subdirectory` 引入，关闭 tests/tools/examples/server/common、OpenSSL、GGML_NATIVE、OpenMP、ccache，静态编入。
2. **内置模型权重**：放到 `app/src/main/assets/models/Qwen3-0.6B-Q4_0.gguf`（Qwen3-0.6B-Q4_0，Apache-2.0，约 364MB）。
   首启由 `ModelManager.ensureBuiltin()` 从 assets 拷贝到私有目录 `models/` 后加载。

## 构建环境

- JDK 21；Android SDK 34；NDK 26.1.10909125；CMake 3.22.1；build-tools 34.0.0
- 编译：`./gradlew assembleDebug`（Windows：`gradlew.bat assembleDebug`）
- 注意：工程路径不要含中文 / 空格 / 括号，否则 CMake/Ninja 配置 native 会失败。

## 对话流程

1. **记录**：对话里交代事项，模型按 JSON 契约抽取标题/正文/标签/人物/地点/情绪/待办，存入笔记列表，气泡末尾标注「📝 已存入笔记」。
2. **提问**：先检索本地笔记标题与正文作为证据，只依据记录回答、不编造；气泡末尾标注「📎 依据」。
3. **双通道**：本地走内置 GGUF 整段返回；云端走 OpenAI 兼容接口，支持流式与思维链；通道选择持久化到本机。

## 进度台账

- `M1-进度.md`：工程地基（Room 笔记/修订、列表与编辑屏）。
- `M2-进度.md`：本地 LLM 通路、云端双通道、抽取入库、读库检索、会话落库、内置模型。
- `M3-进度.md`：两包结合移植（导航骨架 / 内容形态 / 聊天壳 / 视觉主题）与本地推理各轮修复记录。

## 状态说明

debug 构建；本地 0.6B 模型的中文问答质量、推理速度与内存占用需真机/模拟器实测；云端请求需有效 API Key 与网络。
