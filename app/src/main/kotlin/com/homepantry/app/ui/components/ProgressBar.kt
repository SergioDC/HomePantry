package com.listacasa.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.listacasa.app.data.Item
import com.listacasa.app.ui.progressText

@Composable
fun ProgressBar(items: List<Item>, modifier: Modifier = Modifier) {
    val total = items.size
    val done = items.count { it.done }
    val fraction = if (total == 0) 0f else done.toFloat() / total

    Column(modifier = modifier.fillMaxWidth()) {
        Text(progressText(items), style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
