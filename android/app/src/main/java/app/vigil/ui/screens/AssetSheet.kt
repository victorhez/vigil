package app.vigil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vigil.data.Asset
import app.vigil.data.formatAmount
import app.vigil.ui.theme.Mono
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import app.vigil.ui.components.Overline
import app.vigil.ui.components.VigilButton
import java.math.BigDecimal

/** Keeps enough SOL in the wallet for fees when depositing "max". */
private const val SOL_FEE_RESERVE = 15_000_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSheet(
    title: String,
    action: String,
    assets: List<Asset>,
    reserveSolForFees: Boolean,
    busy: Boolean,
    initial: Asset? = null,
    onDismiss: () -> Unit,
    onConfirm: (Asset, Long) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(initial ?: assets.firstOrNull()) }
    var text by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Vigil.Surface,
        dragHandle = { Box(Modifier.padding(top = 12.dp).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Vigil.LineStrong)) },
    ) {
        Column(Modifier.padding(horizontal = 22.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(title, style = Type.Title)
            Spacer(Modifier.height(18.dp))

            if (assets.isEmpty()) {
                Text("Nothing to move yet.", style = Type.BodyMuted)
                Spacer(Modifier.height(24.dp))
                return@Column
            }

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                assets.forEach { asset ->
                    val active = asset == selected
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) Vigil.Flame.copy(alpha = 0.14f) else Vigil.Raised)
                            .border(1.dp, if (active) Vigil.Flame else Vigil.Line, RoundedCornerShape(50))
                            .clickable { selected = asset; text = "" }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TokenGlyph(asset, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(asset.symbol, style = Type.Label, color = if (active) Vigil.Wick else Vigil.Bone)
                    }
                }
            }

            val asset = selected ?: return@Column
            val max = if (asset.isSol && reserveSolForFees) (asset.amount - SOL_FEE_RESERVE).coerceAtLeast(0) else asset.amount
            val amount = text.toBigDecimalOrNull()
            val raw = amount?.let { asset.toRaw(it) } ?: 0L
            val valid = raw in 1..max

            Spacer(Modifier.height(26.dp))
            Overline("Amount")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                BasicTextField(
                    value = text,
                    onValueChange = { v -> if (v.length <= 18 && v.count { it == '.' } <= 1 && v.all { it.isDigit() || it == '.' }) text = v },
                    textStyle = Type.Display.copy(fontFamily = Mono, fontSize = 40.sp, color = if (amount != null && raw > max) Vigil.Blood else Vigil.Bone),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(Vigil.Flame),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("0", style = Type.Display.copy(fontFamily = Mono, fontSize = 40.sp, color = Vigil.Faint))
                        inner()
                    },
                )
                Text(asset.symbol, style = Type.Headline, color = Vigil.Muted, modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("Available ${formatAmount(BigDecimal(max).movePointLeft(asset.decimals), 6)} ${asset.symbol}", style = Type.Label)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 100).forEach { pct ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Vigil.Raised)
                            .clickable {
                                val part = BigDecimal(max).multiply(BigDecimal(pct)).divide(BigDecimal(100)).toLong()
                                text = BigDecimal(part).movePointLeft(asset.decimals).stripTrailingZeros().toPlainString()
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Text(if (pct == 100) "Max" else "$pct%", style = Type.Label, color = Vigil.Bone)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            VigilButton(
                text = action,
                onClick = { onConfirm(asset, raw) },
                enabled = valid,
                loading = busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun TokenGlyph(asset: Asset, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val color = when {
        asset.isSol -> Color(0xFFB9A6FF)
        asset.featured -> Vigil.Flame
        asset.symbol.startsWith("USD") -> Color(0xFF8FB3FF)
        else -> Vigil.Moss
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            asset.symbol.take(if (size < 30.dp) 1 else 3),
            style = Type.Overline.copy(fontSize = if (size < 30.dp) 10.sp else 11.sp, letterSpacing = 0.sp),
            color = color,
        )
    }
}
