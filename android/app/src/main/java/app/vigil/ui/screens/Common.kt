package app.vigil.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.vigil.solana.PublicKey
import app.vigil.ui.components.Icons
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil

/** Standard scrolling page used by every tab: a header, then content, with room for the floating tab bar. */
@Composable
fun TabPage(
    title: String,
    kicker: String,
    onSettings: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                app.vigil.ui.components.Overline(kicker)
                Spacer(Modifier.height(6.dp))
                Text(title, style = Type.Display)
            }
            IconButton(Icons.Settings, "Settings", onSettings)
        }
        content()
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
fun IconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Vigil.Surface)
            .border(1.dp, Vigil.Line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Vigil.Bone, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun FullPage(
    title: String,
    onClose: () -> Unit,
    closeIcon: ImageVector = Icons.Back,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(closeIcon, "Close", onClose)
            Spacer(Modifier.width(14.dp))
            Text(title, style = Type.Headline)
        }
        Column(Modifier.weight(1f).padding(contentPadding), content = content)
    }
}

@Composable
fun AddressText(key: PublicKey, modifier: Modifier = Modifier, head: Int = 4, tail: Int = 4) {
    val context = LocalContext.current
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { copy(context, key.toBase58()) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(key.short(head, tail), style = Type.Numeric.copy(fontSize = Type.Label.fontSize), color = Vigil.Muted)
        Icon(Icons.Copy, contentDescription = "Copy address", tint = Vigil.Faint, modifier = Modifier.size(13.dp))
    }
}

fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Address", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
