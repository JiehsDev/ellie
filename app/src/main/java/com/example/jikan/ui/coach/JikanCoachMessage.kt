package com.example.jikan.ui.coach

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.jikan.R
import com.example.jikan.ui.theme.JikanTheme
import com.example.jikan.ui.theme.RadiusMd
import com.example.jikan.ui.theme.RadiusSm
import com.example.jikan.ui.theme.neoRaised

@Composable
fun JikanCoachMessage(
    message: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val containerModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    BoxWithConstraints(modifier = containerModifier) {
        if (maxWidth < 340.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CoachMascot()
                Spacer(Modifier.height(10.dp))
                CoachBubble(message = message, shape = RoundedCornerShape(RadiusMd))
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                CoachMascot()
                Spacer(Modifier.width(12.dp))
                CoachBubble(
                    message = message,
                    shape = RoundedCornerShape(
                        topStart = 10.dp,
                        topEnd = RadiusMd,
                        bottomEnd = RadiusMd,
                        bottomStart = RadiusMd,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CoachMascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.mascot_jikan_coach),
        contentDescription = "Ellie",
        modifier = modifier
            .size(76.dp)
            .clip(RoundedCornerShape(RadiusSm))
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(RadiusSm))
            .padding(7.dp),
    )
}

@Composable
private fun CoachBubble(
    message: String,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .neoRaised(shape, elevation = 5.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                text = "Ellie",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Local companion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JikanCoachMessagePreview() {
    JikanTheme {
        JikanCoachMessage(
            message = "Steady work today. Your accuracy was solid, and the missed cards are ready for a gentle review next time.",
            modifier = Modifier.padding(24.dp),
        )
    }
}
