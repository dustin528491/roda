package ai.roadtalk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF0B1018)
    val panel = Color(0xFF141B26)
    val panel2 = Color(0xFF1B2433)
    val line = Color(0xFF263245)
    val text = Color(0xFFEFF3F8)
    val sub = Color(0xFF9AA8BC)
    val dim = Color(0xFF66758A)
    val accent = Color(0xFF39D0B0)     // 말하기·진행
    val blue = Color(0xFF5B9DFF)       // 듣기·정보
    val amber = Color(0xFFFFB547)      // 멈춤
    val red = Color(0xFFFF6B6B)
    val warm = Color(0xFFFFD27A)       // 어린이 보호구역
    val purple = Color(0xFFB18CFF)     // 복습
}

@Composable
fun RoadTalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent, onPrimary = Color(0xFF00241C), background = C.bg, surface = C.panel,
            onBackground = C.text, onSurface = C.text, secondary = C.blue,
        ),
        content = content,
    )
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false, modifier: Modifier = Modifier, size: Int = 15) {
    Box(
        modifier
            .background(if (filled) color else color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (filled) Color(0xFF07130F) else color, fontSize = size.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.padding(end = 8.dp).background(color, RoundedCornerShape(50)).padding(5.dp))
}

@Composable
fun LabelValue(label: String, value: String, color: Color = C.text) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp))
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
