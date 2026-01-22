package io.github.ten_o69.lanquiz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
fun NeonBackground(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val isDark = bg.luminance() < 0.5f
    val gradient = if (isDark) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF1B2B4B),
                Color(0xFF0B1020)
            ),
            radius = 1200f
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFEAF3FF),
                Color(0xFFDCE7FF)
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}
