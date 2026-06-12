package dev.bikram.remember.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun AlertBarText(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    contentScale: Float = 1f,
) {
    val titleStyle = MaterialTheme.typography.titleSmall
    val bodyStyle = MaterialTheme.typography.bodyMedium
    Column(modifier = modifier) {
        Text(
            text = title,
            style = titleStyle.copy(fontSize = titleStyle.fontSize * contentScale),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (body != null) {
            Text(
                text = body,
                style = bodyStyle.copy(fontSize = bodyStyle.fontSize * contentScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
