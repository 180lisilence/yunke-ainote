package com.yunke.ainote.data

/**
 * 笔记内容形态，对齐 4 号 NoteDraft 的 type 枚举。
 * 首版仅 NOTE 可完整编辑；其余形态在后续批次 B2/B4 逐步落地。
 */
enum class NoteType(val key: String, val label: String) {
    NOTE("NOTE", "笔记"),
    LIST("LIST", "清单"),
    BOARD("BOARD", "看板"),
    CALC("CALC", "计算"),
    COUNTER("COUNTER", "计数器"),
    HABIT("HABIT", "习惯"),
    LINKLIST("LINKLIST", "书签"),
    MAP("MAP", "地点"),
    NAMES("NAMES", "通讯录"),
    NOTEBOOK("NOTEBOOK", "笔记本"),
    PASSWORD("PASSWORD", "密码库"),
    SHOPPING("SHOPPING", "购物"),
    SNIPPETS("SNIPPETS", "代码片段"),
    TIMELINE("TIMELINE", "时间线"),
    WORKFLOW("WORKFLOW", "流程");

    companion object {
        fun fromKey(key: String?): NoteType =
            entries.firstOrNull { it.key == key } ?: NOTE
    }
}
