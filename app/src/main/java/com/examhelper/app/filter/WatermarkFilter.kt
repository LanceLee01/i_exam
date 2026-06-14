package com.examhelper.app.filter

import android.view.accessibility.AccessibilityNodeInfo

object WatermarkFilter {

    fun matchesWatermark(text: String?, keywords: Set<String>): Boolean {
        if (text.isNullOrBlank()) return false
        return keywords.any { keyword ->
            text.contains(keyword, ignoreCase = true) ||
                text.trim().equals(keyword, ignoreCase = true)
        }
    }

    fun shouldSkipNode(node: AccessibilityNodeInfo, keywords: Set<String>): Boolean {
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        return matchesWatermark(text, keywords) ||
            matchesWatermark(contentDesc, keywords)
    }

    fun filterLines(lines: List<String>, keywords: Set<String>): List<String> {
        return lines.filter { line ->
            !matchesWatermark(line, keywords)
        }
    }
}
