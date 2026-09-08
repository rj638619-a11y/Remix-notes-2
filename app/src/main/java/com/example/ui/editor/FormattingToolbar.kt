package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassTheme

@Composable
fun FormattingToolbar(
    onHeading: (Int) -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onCode: () -> Unit,
    onBulletList: () -> Unit,
    onCheckbox: () -> Unit,
    onQuote: () -> Unit,
    onDivider: () -> Unit,
    onInsertDate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToolbarTextButton("H1", 15.sp) { onHeading(1) }
        ToolbarTextButton("H2", 13.sp) { onHeading(2) }
        ToolbarTextButton("H3", 11.sp) { onHeading(3) }

        ToolbarSeparator()

        ToolbarTextButton("B", 14.sp, fontWeight = FontWeight.ExtraBold) { onBold() }
        ToolbarTextButton("I", 14.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold) { onItalic() }
        ToolbarIconButton(Icons.Default.Code) { onCode() }

        ToolbarSeparator()

        ToolbarIconButton(Icons.AutoMirrored.Filled.FormatListBulleted) { onBulletList() }
        ToolbarIconButton(Icons.Default.CheckBox) { onCheckbox() }
        ToolbarIconButton(Icons.Default.FormatQuote) { onQuote() }

        ToolbarSeparator()

        ToolbarTextButton("—", 14.sp, fontWeight = FontWeight.Bold) { onDivider() }
        ToolbarIconButton(Icons.Default.Schedule) { onInsertDate() }
    }
}

@Composable
private fun ToolbarTextButton(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Bold,
    fontStyle: FontStyle = FontStyle.Normal,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
fun HtmlFormattingToolbar(
    onTag: (String, String, String) -> Unit,
    onSnippet: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = GlassTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card)
            .border(1.dp, colors.hairline, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToolbarTagPill("<h1/>") { onTag("<h1>", "</h1>", "Heading") }
        ToolbarTagPill("<h2/>") { onTag("<h2>", "</h2>", "Subheading") }
        ToolbarTagPill("<p/>") { onTag("<p>", "</p>", "Text") }

        ToolbarSeparator()

        ToolbarTagPill("<b/>") { onTag("<b>", "</b>", "bold") }
        ToolbarTagPill("<i/>") { onTag("<i>", "</i>", "italic") }
        ToolbarTagPill("<code/>") { onTag("<code>", "</code>", "code") }

        ToolbarSeparator()

        ToolbarTagPill("<div/>") { onTag("<div class=\"card\">\n  ", "\n</div>", "Content") }
        ToolbarTagPill("<span/>") { onTag("<span>", "</span>", "text") }
        ToolbarTagPill("<ul/li>") { onTag("<ul>\n  <li>", "</li>\n</ul>", "Item") }
        ToolbarTagPill("<li/>") { onTag("<li>", "</li>", "Item") }

        ToolbarSeparator()

        ToolbarTagPill("<a/>") { onTag("<a href=\"#\">", "</a>", "Link") }
        ToolbarTagPill("<style/>") { onTag("<style>\n  ", "\n</style>", "body { }") }
        ToolbarTagPill("<btn/>") { onTag("<button>", "</button>", "Click") }
        ToolbarTagPill("<!-- -->") { onTag("<!-- ", " -->", "comment") }
    }
}

@Composable
private fun ToolbarSeparator() {
    val colors = GlassTheme.colors
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(18.dp)
            .background(colors.hairline)
    )
}

@Composable
private fun ToolbarTagPill(
    tag: String,
    onClick: () -> Unit
) {
    val colors = GlassTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.field)
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accent
        )
    }
}
