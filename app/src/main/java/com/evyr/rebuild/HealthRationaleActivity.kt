package com.evyr.rebuild

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.*

/**
 * Health Connect won't show its permission screen unless the app declares
 * one of these explaining what it reads. Opened by Health Connect itself.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RebuildTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(EvaBackground).padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("REBUILD + HEALTH CONNECT", fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "REBUILD reads your exercise sessions so a workout of 10 minutes or more can tick the directive you picked in Settings. " +
                            "It reads nothing else, writes nothing, and nothing leaves your phone.",
                        fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { finish() },
                        colors = ButtonDefaults.buttonColors(containerColor = EvaYellow, contentColor = Color.Black)
                    ) { Text("CLOSE", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
