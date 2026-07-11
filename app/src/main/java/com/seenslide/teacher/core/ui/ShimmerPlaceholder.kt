package com.seenslide.teacher.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )
    return Brush.linearGradient(
        colors = listOf(
            Color.LightGray.copy(alpha = 0.3f),
            Color.LightGray.copy(alpha = 0.1f),
            Color.LightGray.copy(alpha = 0.3f),
        ),
        start = Offset(translateAnim.value - 200f, 0f),
        end = Offset(translateAnim.value, 0f),
    )
}

@Composable
fun ShimmerSessionCards(count: Int = 3) {
    val brush = shimmerBrush()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(count) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerTalkCards(count: Int = 3) {
    val brush = shimmerBrush()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerSlideGrid(count: Int = 4) {
    val brush = shimmerBrush()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count.coerceAtMost(2)) {
            Card(
                modifier = Modifier
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .background(brush),
                )
            }
        }
    }
}
