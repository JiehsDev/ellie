package com.example.jikan.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.jikan.MainActivity
import com.example.jikan.data.AppDatabase
import com.example.jikan.data.Wallet

private val WidgetBg = Color(0xFFF4EFE7)
private val WidgetInk = Color(0xFF2B2723)
private val WidgetInkSoft = Color(0xFF8A8175)
private val WidgetAccent = Color(0xFF45537C)
private val WidgetVermillion = Color(0xFFB5543C)

class JikanWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val wallet = AppDatabase.getInstance(context).walletDao().get() ?: Wallet()

        provideContent {
            WidgetContent(
                walletMinutes = wallet.creditBalanceMinutes,
                streakDays = wallet.currentStreakDays,
            )
        }
    }
}

@Composable
private fun WidgetContent(walletMinutes: Int, streakDays: Int) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(20.dp)
            .padding(16.dp),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                StatColumn(label = "WALLET", value = "$walletMinutes min", valueColor = WidgetInk)
                Spacer(modifier = GlanceModifier.width(16.dp))
                StatColumn(label = "STREAK", value = if (streakDays == 1) "1 day" else "$streakDays days", valueColor = WidgetVermillion)
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(WidgetAccent)
                    .cornerRadius(999.dp)
                    .clickable(
                        actionStartActivity(
                            intent = Intent(context, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_START_STUDY, true)
                            },
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Learn now",
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 14.sp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatColumn(label: String, value: String, valueColor: Color) {
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
            text = label,
            style = TextStyle(color = ColorProvider(WidgetInkSoft), fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = value,
            style = TextStyle(color = ColorProvider(valueColor), fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
    }
}
