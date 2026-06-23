package com.examhelper.app.util

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 验证选项文字匹配解析（Commit e66be3a 的核心功能）。
 *
 * 场景：题库中的选择题（单选/多选）在屏幕上的选项顺序与题库不同。
 * 传统方式直接按题库字母点击会点错，本测试验证通过选项文字相似度
 * 匹配来找到正确的屏幕字母。
 */
class OptionTextUtilsTest {

    // ── parseOptionMapInline ────────────────────────────────────────────

    @Test
    fun `parseOptionMapInline parses standard format`() {
        val result = parseOptionMapInline("A. 施工方案 B. 质量管理 C. 安全培训 D. 进度控制")
        assertEquals("施工方案", result["A"])
        assertEquals("质量管理", result["B"])
        assertEquals("安全培训", result["C"])
        assertEquals("进度控制", result["D"])
    }

    @Test
    fun `parseOptionMapInline parses Chinese separator`() {
        val result = parseOptionMapInline("A．施工方案 B．质量管理 C．安全培训")
        assertEquals("施工方案", result["A"])
        assertEquals("质量管理", result["B"])
        assertEquals("安全培训", result["C"])
    }

    @Test
    fun `parseOptionMapInline parses colon separator`() {
        val result = parseOptionMapInline("A:施工方案 B:质量管理 C:安全培训")
        assertEquals("施工方案", result["A"])
        assertEquals("质量管理", result["B"])
        assertEquals("安全培训", result["C"])
    }

    @Test
    fun `parseOptionMapInline parses hyphen separator`() {
        val result = parseOptionMapInline("A-每半年 B-每年 C-每两年 D-每三年")
        assertEquals("每半年", result["A"])
        assertEquals("每年", result["B"])
        assertEquals("每两年", result["C"])
        assertEquals("每三年", result["D"])
    }

    @Test
    fun `parseOptionMapInline returns empty for empty input`() {
        val result = parseOptionMapInline("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseOptionMapInline handles single option`() {
        val result = parseOptionMapInline("A. 施工方案")
        assertEquals("施工方案", result["A"])
        assertEquals(1, result.size)
    }

    // ── computeTextSimilarity ───────────────────────────────────────────

    @Test
    fun `computeTextSimilarity identical text returns 1 dot 0`() {
        val score = computeTextSimilarity("施工方案", "施工方案")
        assertEquals(1.0f, score)
    }

    @Test
    fun `computeTextSimilarity substring match returns 0 dot 85`() {
        val score = computeTextSimilarity("施工方案审批", "施工方案")
        assertEquals(0.85f, score)
    }

    @Test
    fun `computeTextSimilarity partially similar returns intermediate score`() {
        val score = computeTextSimilarity("施工方案", "施工组织")
        assertTrue(score > 0.0f, "Should have some similarity: $score")
        assertTrue(score < 1.0f, "Should not be perfect: $score")
    }

    @Test
    fun `computeTextSimilarity completely different returns 0`() {
        val score = computeTextSimilarity("施工方案", "ABCDEFG")
        assertEquals(0.0f, score)
    }

    // ── resolveOnScreenLetters: 核心乱序场景 ─────────────────────────────

    /**
     * 场景1: 单选，题库A对应"施工方案"，屏幕上"施工方案"在C位置
     * KB: A.施工方案 B.质量管理 C.安全培训 D.进度控制
     * 屏幕: A.安全培训 B.进度控制 C.施工方案 D.质量管理  ← 乱序
     * KB答案: A → 应解析为 C
     */
    @Test
    fun `resolve single choice with shuffled options`() {
        val kbOptionsText = "A. 施工方案 B. 质量管理 C. 安全培训 D. 进度控制"
        val answerLetters = listOf("A")
        val onScreenOptions = listOf(
            "A" to "安全培训",
            "B" to "进度控制",
            "C" to "施工方案",
            "D" to "质量管理"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("C"), resolved, "KB answer A (施工方案) should resolve to C on screen")
    }

    /**
     * 场景2: 多选，题库A+B对应"施工方案"+"质量管理"，屏幕上位置互换
     * KB: A.施工方案 B.质量管理 C.安全培训 D.进度控制
     * 屏幕: A.质量管理 B.施工方案 C.安全培训 D.进度控制  ← A和B互换
     * KB答案: A B → 应解析为 B A
     */
    @Test
    fun `resolve multi choice with swapped options`() {
        val kbOptionsText = "A. 施工方案 B. 质量管理 C. 安全培训 D. 进度控制"
        val answerLetters = listOf("A", "B")
        val onScreenOptions = listOf(
            "A" to "质量管理",
            "B" to "施工方案",
            "C" to "安全培训",
            "D" to "进度控制"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("B", "A"), resolved,
            "KB answer A(施工方案)→B, B(质量管理)→A on screen")
    }

    /**
     * 场景3: 多选完全乱序
     * KB: A.工作票 B.操作票 C.记录簿 D.登记册
     * 屏幕: A.登记册 B.工作票 C.操作票 D.记录簿  ← 完全乱序
     * KB答案: A C → 应解析为 B D
     */
    @Test
    fun `resolve multi choice with fully shuffled options`() {
        val kbOptionsText = "A. 工作票 B. 操作票 C. 记录簿 D. 登记册"
        val answerLetters = listOf("A", "C")  // KB says 工作票+记录簿
        val onScreenOptions = listOf(
            "A" to "登记册",
            "B" to "工作票",
            "C" to "操作票",
            "D" to "记录簿"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("B", "D"), resolved,
            "KB answer A(工作票)→B, C(记录簿)→D on screen")
    }

    /**
     * 场景4: 选项文字完全相同（没乱序）→ 保持原字母
     */
    @Test
    fun `resolve with same order keeps original letters`() {
        val kbOptionsText = "A. 施工方案 B. 质量管理 C. 安全培训"
        val answerLetters = listOf("A")
        val onScreenOptions = listOf(
            "A" to "施工方案",
            "B" to "质量管理",
            "C" to "安全培训"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("A"), resolved, "Same order should keep original letter")
    }

    /**
     * 场景5: KB没有选项文字 → fallback到原字母
     */
    @Test
    fun `resolve with empty KB options falls back to original letters`() {
        val answerLetters = listOf("A")
        val onScreenOptions = listOf(
            "A" to "施工方案",
            "B" to "质量管理"
        )

        val resolved = resolveOnScreenLetters("", answerLetters, onScreenOptions)
        assertEquals(listOf("A"), resolved, "Empty KB options should keep original letter")
    }

    /**
     * 场景6: 判断题（正确/错误）→ 不经过选项文字匹配
     */
    @Test
    fun `resolve true false question skips option matching`() {
        val kbOptionsText = ""  // 判断题通常没有options
        val answerLetters = listOf("正确")
        val onScreenOptions = listOf(
            "A" to "正确",
            "B" to "错误"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("正确"), resolved, "True/False should keep original value")
    }

    /**
     * 场景7: 长文字选项，部分匹配
     * KB: A.工作负责人向工作票签发人提出申请 B.工作许可人向运维负责人提出申请
     * 屏幕: A.工作许可人向运维负责人提出申请 B.工作负责人向工作票签发人提出申请
     * KB答案: A → 应解析为 B
     */
    @Test
    fun `resolve with long text options shuffled`() {
        val kbOptionsText = "A. 工作负责人向工作票签发人提出申请 B. 工作许可人向运维负责人提出申请"
        val answerLetters = listOf("A")
        val onScreenOptions = listOf(
            "A" to "工作许可人向运维负责人提出申请",
            "B" to "工作负责人向工作票签发人提出申请"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("B"), resolved,
            "KB answer A(工作负责人...) should resolve to B on screen")
    }

    /**
     * 场景8: 模拟真实考试数据 — 电力安全规程
     * KB: A.工作负责人 B.工作许可人 C.专责监护人 D.工作票签发人
     * 屏幕: A.工作票签发人 B.专责监护人 C.工作许可人 D.工作负责人  ← 逆序
     * KB答案: B D → 应解析为 C A
     */
    @Test
    fun `resolve with reverse order options`() {
        val kbOptionsText = "A. 工作负责人 B. 工作许可人 C. 专责监护人 D. 工作票签发人"
        val answerLetters = listOf("B", "D")  // KB says 工作许可人+工作票签发人
        val onScreenOptions = listOf(
            "A" to "工作票签发人",
            "B" to "专责监护人",
            "C" to "工作许可人",
            "D" to "工作负责人"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("C", "A"), resolved,
            "KB answer B(工作许可人)→C, D(工作票签发人)→A on screen")
    }

    /**
     * 场景9: KB选项文字在屏幕上有前缀/后缀差异
     * KB: A.施工方案 B.质量管理
     * 屏幕: A.选项A施工方案 B.选项B质量管理  ← 多了"选项X"前缀
     * 由于substring匹配（施工方案 in 选项A施工方案），相似度0.85，应匹配
     */
    @Test
    fun `resolve with prefix on screen options`() {
        val kbOptionsText = "A. 施工方案 B. 质量管理"
        val answerLetters = listOf("A")
        val onScreenOptions = listOf(
            "A" to "选项A施工方案",
            "B" to "选项B质量管理"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        // computeTextSimilarity("施工方案", "选项A施工方案"): "施工方案" in "选项A施工方案" → 0.85
        // computeTextSimilarity("施工方案", "选项B质量管理"): no match → ~0.0
        // So "A" should resolve to "A" (施工方案→选项A施工方案)
        assertEquals(listOf("A"), resolved,
            "KB answer A(施工方案) should match screen A(选项A施工方案) by substring")
    }

    // ── 完整端到端模拟 ──

    /**
     * 场景11: 真实日志中的乱序 — 题库用 - 分隔符，屏幕乱序
     * KB: A-每半年 B-每年 C-每两年 D-每三年
     * 屏幕: A.每年 B.每两年 C.每半年 D.每三年
     * KB答案: A（每半年）→ 应解析为 C
     * KB答案: B（每年）→ 应解析为 A
     */
    @Test
    fun `resolve real scenario Q8 hyphen separator shuffled`() {
        val kbOptionsText = "A-每半年 B-每年 C-每两年 D-每三年"
        val answerLetters = listOf("A", "B")
        val onScreenOptions = listOf(
            "A" to "每年",
            "B" to "每两年",
            "C" to "每半年",
            "D" to "每三年"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("C", "A"), resolved,
            "KB answer A(每半年)→C, B(每年)→A on screen")
    }

    /**
     * 场景12: 真实日志中的 Q10 — 题库用 - 分隔符，屏幕完全乱序
     * KB: A-先扣分再降级 B-先降级再扣分 C-先考核再降级 D-先降级再考核
     * 屏幕: A.先考核再降级 B.先降级再扣分 C.先降级再考核 D.先扣分再降级
     */
    @Test
    fun `resolve real scenario Q10 hyphen separator shuffled`() {
        val kbOptionsText = "A-先扣分再降级 B-先降级再扣分 C-先考核再降级 D-先降级再考核"
        val answerLetters = listOf("C")
        val onScreenOptions = listOf(
            "A" to "先考核再降级",
            "B" to "先降级再扣分",
            "C" to "先降级再考核",
            "D" to "先扣分再降级"
        )

        val resolved = resolveOnScreenLetters(kbOptionsText, answerLetters, onScreenOptions)
        assertEquals(listOf("A"), resolved,
            "KB answer C(先考核再降级)→A on screen")
    }
    /**
     * 场景10: 模拟完整的performAutoClick中的选项文字解析流程。
     *
     * 模拟 ExamAccessibilityService.performAutoClick 中的代码段:
     *
     * val onScreenMap = qOptionNodes.mapNotNull { (_, nodeText) ->
     *     val letter = nodeText.firstOrNull()?.uppercaseChar()?.toString() ?: return@mapNotNull null
     *     val text = nodeText.drop(1).trimStart('.', '、', '．', '：', ':', '，', ' ', '）', ')').trim()
     *     if (letter in "A".."F") letter to text else null
     * }
     * val resolved = resolveOnScreenLetters(kbOpts, selections, onScreenMap)
     */
    @Test
    fun `end to end simulate performAutoClick logic`() {
        // 模拟屏幕上抓到的节点文字
        val rawScreenTexts = listOf("A. 安全培训", "B. 进度控制", "C. 施工方案", "D. 质量管理")

        // 模拟 performAutoClick 中的构建过程
        val onScreenMap = rawScreenTexts.mapNotNull { nodeText ->
            val letter = nodeText.first().uppercaseChar().toString()
            val text = nodeText.drop(1).trimStart('.', '、', '．', '：', ':', '，', ' ', '）', ')').trim()
            if (letter in "A".."F") letter to text else null
        }

        // KB数据
        val kbOptionsText = "A. 施工方案 B. 质量管理 C. 安全培训 D. 进度控制"
        val selections = listOf("A")  // KB says answer is A

        val resolved = resolveOnScreenLetters(kbOptionsText, selections, onScreenMap)
        assertEquals(listOf("C"), resolved,
            "Full pipeline: KB answer A(施工方案) -> screen C")
    }
}
