package com.example.util

sealed interface ReadBlock {
    data class Heading(val level: Int, val text: String) : ReadBlock
    data class Paragraph(val text: String) : ReadBlock
    data class BulletItem(val text: String) : ReadBlock
    data class CheckboxItem(val checked: Boolean, val text: String) : ReadBlock
    data class Blockquote(val text: String) : ReadBlock
    data class CodeBlock(val code: String) : ReadBlock
    object Divider : ReadBlock
}

object MarkdownParser {
    fun parse(input: String): List<ReadBlock> {
        val lines = input.lines()
        val blocks = mutableListOf<ReadBlock>()
        var inCodeFence = false
        val codeBuilder = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeFence) {
                    blocks.add(ReadBlock.CodeBlock(code = codeBuilder.toString().trimEnd()))
                    codeBuilder.clear()
                    inCodeFence = false
                } else {
                    inCodeFence = true
                }
                continue
            }

            if (inCodeFence) {
                codeBuilder.append(line).append("\n")
                continue
            }

            val t = trimmed
            if (t.isEmpty()) continue

            if (t.matches(Regex("^---+[\\s-]*$"))) {
                blocks.add(ReadBlock.Divider)
                continue
            }

            val headingMatch = Regex("^(#{1,4})\\s+(.*)").find(t)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2]
                blocks.add(ReadBlock.Heading(level = level, text = text))
                continue
            }

            val checkboxMatch = Regex("^\\[([ xX])\\]\\s+(.*)").find(t)
            if (checkboxMatch != null) {
                val mark = checkboxMatch.groupValues[1]
                val isChecked = mark == "x" || mark == "X"
                val text = checkboxMatch.groupValues[2]
                blocks.add(ReadBlock.CheckboxItem(checked = isChecked, text = text))
                continue
            }

            val bulletMatch = Regex("^[-*•]\\s+(.*)").find(t)
            if (bulletMatch != null) {
                val text = bulletMatch.groupValues[1]
                blocks.add(ReadBlock.BulletItem(text = text))
                continue
            }

            val quoteMatch = Regex("^>\\s?(.*)").find(t)
            if (quoteMatch != null) {
                val text = quoteMatch.groupValues[1]
                blocks.add(ReadBlock.Blockquote(text = text))
                continue
            }

            blocks.add(ReadBlock.Paragraph(text = t))
        }

        if (inCodeFence && codeBuilder.isNotEmpty()) {
            blocks.add(ReadBlock.CodeBlock(code = codeBuilder.toString().trimEnd()))
        }

        return blocks
    }

    fun renderToHtml(input: String): String {
        val blocks = parse(input)
        val sb = StringBuilder()
        for (block in blocks) {
            when (block) {
                is ReadBlock.Heading -> {
                    val tag = "h${block.level.coerceIn(1, 6)}"
                    sb.append("<$tag>${escapeHtml(block.text)}</$tag>\n")
                }
                is ReadBlock.Paragraph -> {
                    sb.append("<p>${escapeHtml(block.text)}</p>\n")
                }
                is ReadBlock.BulletItem -> {
                    sb.append("<ul><li>${escapeHtml(block.text)}</li></ul>\n")
                }
                is ReadBlock.CheckboxItem -> {
                    val checkMark = if (block.checked) "☑ " else "☐ "
                    sb.append("<p style=\"margin: 4px 0;\"><span>$checkMark</span> ${escapeHtml(block.text)}</p>\n")
                }
                is ReadBlock.Blockquote -> {
                    sb.append("<blockquote>${escapeHtml(block.text)}</blockquote>\n")
                }
                is ReadBlock.CodeBlock -> {
                    sb.append("<pre style=\"background: #f4f4f5; padding: 12px; border-radius: 8px; overflow-x: auto;\"><code>${escapeHtml(block.code)}</code></pre>\n")
                }
                is ReadBlock.Divider -> {
                    sb.append("<hr style=\"border: 0; border-top: 1px solid #e5e5ea; margin: 16px 0;\" />\n")
                }
            }
        }
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
