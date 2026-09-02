package com.example.jikan.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Soft single-tone drop shadow approximating the reference design's neomorphic "raised" cards. */
fun Modifier.neoRaised(shape: Shape = RoundedCornerShape(28.dp), elevation: Dp = 10.dp): Modifier =
    this.shadow(elevation, shape, ambientColor = ShadowDark, spotColor = ShadowDark)

val RadiusLg = 28.dp
val RadiusMd = 20.dp
val RadiusSm = 14.dp

@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(RadiusLg),
    elevation: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .neoRaised(shape, elevation)
            .background(MaterialTheme.colorScheme.background, shape)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun IconChip(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(RadiusSm)
    var chipModifier = modifier
        .size(40.dp)
        .neoRaised(shape, elevation = 6.dp)
        .background(MaterialTheme.colorScheme.background, shape)
    if (onClick != null) {
        chipModifier = chipModifier.clip(shape).clickable(onClick = onClick)
    }
    Box(modifier = chipModifier, contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier.height(56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .height(56.dp)
            .neoRaised(shape, elevation = 6.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight()) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun JikanProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, shape),
        )
    }
}
