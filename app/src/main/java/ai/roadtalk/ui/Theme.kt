package ai.roadtalk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 디자인 시스템(라이트) 토큰.
 * 색: Foundations > Colors(Grayscale, System, Informative), 글자: Foundations > Typography,
 * 모양: 에뮬레이터 기본 앱(전화·차량 제어)처럼 각진 흰 칸, 1px 구분선, 검은 선택 표시.
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
    val basic700 = Color(0xFF313236)
    val basic800 = Color(0xFF1D1E21)
    val basic900 = Color(0xFF131417)

    val background = basic100          // 콘텐츠 뒤 회색 바탕
    val surface = basic00              // 흰 칸
    val surfaceHigh = basic100         // 눌림·강조 칸
    val divider = basic200
    val selected = basic900            // 선택된 항목 바탕(검정)
    val textPrimary = basic900
    val textSecondary = basic500
    val textTertiary = basic400

    val active = Color(0xFF02C265)     // informative_active
    val positive = Color(0xFF0064FF)   // informative_positive
    val negative = Color(0xFFFE3D16)   // informative_negative
    val autonomous = Color(0xFF5A46FA) // informative_autonomous_driving
}

/** Pleos 타이포 스케일(Headline 60/56/48, Title 40/36/32, Body 30/28/26, Label 24/20). 글꼴은 시스템 테마 글꼴을 그대로 쓴다. */
object T {
    private fun s(size: Int, line: Int, w: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, color = P.textPrimary)
    val headlineL = s(60, 80, FontWeight.Bold)
    val headlineM = s(56, 72, FontWeight.Bold)
    val headlineS = s(48, 64, FontWeight.Bold)
    val titleL = s(40, 52)
    val titleLBold = s(40, 52, FontWeight.Bold)
    val titleM = s(36, 44)
    val titleMBold = s(36, 44, FontWeight.Bold)
    val titleS = s(32, 40)
    val titleSBold = s(32, 40, FontWeight.Bold)
    val bodyL = s(30, 38)
    val bodyM = s(28, 36)
    val bodyS = s(26, 34)
    val labelM = s(24, 28)
    val labelS = s(20, 24)
}

@Composable
fun RoadTalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = P.basic900, onPrimary = P.basic00, background = P.background, surface = P.surface,
            onBackground = P.textPrimary, onSurface = P.textPrimary, secondary = P.positive,
        ),
        content = content,
    )
}

/** 누르면 95%로 줄어드는 Pleos 버튼 반응. 운전 중 시선을 끄는 다른 움직임은 쓰지 않는다. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return this.scale(if (pressed) 0.95f else 1f).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}


@Composable
fun HDivider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(P.divider))

@Composable
fun VDivider(modifier: Modifier = Modifier) = Box(modifier.fillMaxHeight().width(1.dp).background(P.divider))

/** 앱 상단(Page Navigation): 짙은 정사각형 앱 아이콘 + 제목, 오른쪽 메뉴 영역. */
@Composable
fun PageNav(title: String, icon: ImageVector, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().background(P.surface)) {
        Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(30.dp))
            Box(Modifier.size(62.dp).background(P.basic700), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = P.basic00, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.width(20.dp))
            Text(title, style = T.titleM, maxLines = 1)
            if (subtitle != null) {
                Spacer(Modifier.width(20.dp))
                Box(Modifier.height(32.dp).width(1.dp).background(P.divider))
                Spacer(Modifier.width(20.dp))
                Text(subtitle, style = T.titleS.copy(color = P.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        HDivider()
    }
}

/** 상단 오른쪽 메뉴 칸(세로 구분선 + 글자/아이콘). */
@Composable
fun NavAction(text: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        VDivider()
        Box(Modifier.fillMaxHeight().pressable(onClick = onClick).padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, text, tint = P.textPrimary, modifier = Modifier.size(40.dp))
            else Text(text ?: "", style = T.bodyL)
        }
    }
}

/** 검은 사각 버튼(기본 앱의 '블루투스 연결'과 같은 모양). */
@Composable
fun BlackButton(text: String, modifier: Modifier = Modifier, height: Dp = 96.dp, onClick: () -> Unit) {
    Box(modifier.height(height).background(P.selected).pressable(onClick = onClick).padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.titleS.copy(color = P.basic00), maxLines = 1)
    }
}

/** 흰 테두리 버튼. */
@Composable
fun LineButton(text: String, modifier: Modifier = Modifier, height: Dp = 96.dp, onClick: () -> Unit) {
    Box(modifier.height(height).background(P.surface).border(1.dp, P.basic300).pressable(onClick = onClick).padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.titleS, maxLines = 1)
    }
}

/** 테두리 안에 칸이 나뉘고 선택된 칸만 검은 바탕인 선택 막대(차량 제어 '전조등'과 같은 모양). */
@Composable
fun <K> Segmented(items: List<Pair<K, String>>, selected: K, modifier: Modifier = Modifier, height: Dp = 88.dp, onSelect: (K) -> Unit) {
    Row(modifier.height(height).border(1.dp, P.basic300).height(IntrinsicSize.Min)) {
        items.forEachIndexed { i, (k, label) ->
            if (i > 0) VDivider()
            val sel = k == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().background(if (sel) P.selected else P.surface).pressable { onSelect(k) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = T.bodyL.copy(color = if (sel) P.basic00 else P.textPrimary), maxLines = 1) }
        }
    }
}

/** 기본 앱 왼쪽 목록 한 줄. 선택되면 검은 바탕. */
@Composable
fun NavRow(icon: ImageVector, title: String, value: String? = null, selected: Boolean = false, valueColor: Color = P.textSecondary, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(100.dp).background(if (selected) P.selected else P.surface)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) P.basic00 else P.textPrimary, modifier = Modifier.size(42.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = (if (value == null) T.titleS else T.labelM).copy(color = if (selected) P.basic00 else if (value == null) P.textPrimary else P.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value != null) Text(value, style = T.bodyL.copy(color = if (selected) P.basic00 else valueColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 상태 점 + 글자. */
@Composable
fun StatusDot(color: Color, text: String, style: TextStyle = T.labelM) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = style.copy(color = color), maxLines = 1)
    }
}
