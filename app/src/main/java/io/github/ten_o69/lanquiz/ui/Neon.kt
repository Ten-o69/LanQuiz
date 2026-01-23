package io.github.ten_o69.lanquiz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val isDark = bg.luminance() < 0.5f
    val baseGradient = if (isDark) {
        Brush.radialGradient(
            colors = listOf(
                bg,
                Color(0xFF0B1122)
            ),
            radius = 1200f
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                bg,
                Color(0xFFE3ECFF)
            )
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseGradient)
            .padding(contentPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.18f), Color.Transparent),
                        radius = 700f,
                        center = androidx.compose.ui.geometry.Offset(200f, 200f)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = 0.12f), Color.Transparent),
                        radius = 900f,
                        center = androidx.compose.ui.geometry.Offset(900f, 400f)
                    )
                )
        )
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp),
            content = content
        )
    }
}
