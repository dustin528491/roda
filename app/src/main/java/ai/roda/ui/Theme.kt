package ai.roda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 디자인 시스템(라이트) 토큰.
 * 회색 단계·정보 색은 Foundations > Colors, 글자 크기는 Foundations > Typography,
 * 화면 형식은 대화형 기본 앱인 Gleo AI(가운데 정렬, 넓은 여백, 둥근 연보라 패널·파란 칩, 상태별 색 알약)를 따른다.
 */
object P {
    val basic00 = Color(0xFFFFFFFF)
    val basic50 = Color(0xFFF7F8FA)
    val basic100 = Color(0xFFEDEEF2)
    val basic200 = Color(0xFFE0E1E6)
    val basic300 = Color(0xFFC7C9CE)
    val basic400 = Color(0xFFA4A8AE)
    val basic500 = Color(0xFF686A72)
    val basic600 = Color(0xFF44464E)
    val basic900 = Color(0xFF131417)

    val surface = basic00
    val divider = basic200
    val chip = Color(0xFFF1F2F5)
    val textPrimary = basic900
    val textSecondary = basic500
    val textTertiary = basic400

    // Gleo 패널·칩
    val accent = Color(0xFF596BFF)
    val accentText = Color(0xFF3F4CE0)
    val accentSoft = Color(0xFFDFE6FF)
    val panel = Color(0xFFF5F6FF)

    // 대화 상태(Listening / Speaking / 대기)
    val listenBg = Color(0xFFDADFFF); val listenFg = Color(0xFF2A35B8)
    val speakBg = Color(0xFFF2E1FF); val speakFg = Color(0xFF6A2FA8)
    val holdBg = basic100; val holdFg = basic600

    val positive = Color(0xFF0064FF)
    val negative = Color(0xFFFE3D16)
}

object T {
    private fun s(size: Int, line: Int, w: FontWeight = FontWeight.Normal, c: Color = P.textPrimary) =
        TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, color = c)
    val display = s(84, 100, FontWeight.ExtraBold)
    val sentence = s(68, 88, FontWeight.Bold)
    val sentenceS = s(48, 64, FontWeight.Bold)        // 좁은 창
    val titleM = s(36, 44)
    val titleMBold = s(36, 44, FontWeight.Bold)
    val titleS = s(32, 44)
    val bodyL = s(30, 40)
    val bodyLStrong = s(30, 40, FontWeight.SemiBold)
    val bodyM = s(28, 36)
    val bodyS = s(26, 34)
    val labelM = s(24, 30)
    val labelS = s(22, 28)
}

@Composable
fun RodaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = P.accent, onPrimary = P.basic00, background = P.surface, surface = P.surface, onBackground = P.textPrimary, onSurface = P.textPrimary),
        content = content,
    )
}

/** 누르면 95%로 줄어드는 Pleos 버튼 반응. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return this.scale(if (pressed) 0.95f else 1f).clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
fun HDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(P.divider))

/** 앱 상단: 둥근 검은 앱 아이콘 + 이름 | 부제, 오른쪽 동작. */
@Composable
fun Header(icon: ImageVector, subtitle: @Composable RowScope.() -> Unit, trailing: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(P.surface)) {
        Row(Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)).background(P.basic900), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = P.basic00, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.width(20.dp))
            Text("로다", style = T.titleM, maxLines = 1)
            Spacer(Modifier.width(22.dp))
            Box(Modifier.height(32.dp).width(1.dp).background(P.divider))
            Spacer(Modifier.width(22.dp))
            subtitle()
            Spacer(Modifier.weight(1f))
            trailing()
        }
        HDivider()
    }
}

/** 회색 칩 버튼(상단 '끝내기' 등). */
@Composable
fun ChipButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(P.chip).pressable(onClick).padding(horizontal = 28.dp, vertical = 16.dp)) {
        Text(text, style = T.bodyM, maxLines = 1)
    }
}

/** 알약 선택지. 선택되면 검은 바탕. */
@Composable
fun <K> PillChoices(items: List<Pair<K, String>>, selected: K, onSelect: (K) -> Unit) {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
        items.forEach { (k, label) ->
            val sel = k == selected
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) P.basic900 else P.chip).pressable { onSelect(k) }.padding(horizontal = 40.dp, vertical = 18.dp)) {
                Text(label, style = T.bodyL.copy(color = if (sel) P.basic00 else P.basic600), maxLines = 1)
            }
        }
    }
}

/** 파란 주 버튼(Gleo 칩 primary). */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(P.accent).pressable(onClick).padding(horizontal = 120.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.titleMBold.copy(color = P.basic00), maxLines = 1)
    }
}

/** 대화 상태 알약. */
@Composable
fun StatePill(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 40.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(14.dp)) }
        Text(text, style = T.bodyLStrong.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 작은 꼬리표. */
@Composable
fun Tag(text: String, accent: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (accent) P.accentSoft else P.basic100).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(text, style = T.labelS.copy(color = if (accent) P.accentText else P.basic600), maxLines = 1)
    }
}
