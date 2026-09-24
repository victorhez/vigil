package app.vigil.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.vigil.solana.VaultPhase
import app.vigil.ui.Busy
import app.vigil.ui.LocalSender
import app.vigil.ui.VigilState
import app.vigil.ui.VigilViewModel
import app.vigil.ui.components.ButtonKind
import app.vigil.ui.components.Overline
import app.vigil.ui.components.PulseDial
import app.vigil.ui.components.VigilButton
import app.vigil.ui.theme.Type
import app.vigil.ui.theme.Vigil
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class Page(val kicker: String, val lead: String, val emphasis: String, val body: String, val remaining: Float, val phase: VaultPhase)

private val pages = listOf(
    Page(
        "The problem",
        "Crypto has no ",
        "next of kin.",
        "When someone dies or loses their keys, their coins go with them. Nobody can open that wallet again.",
        0.08f,
        VaultPhase.Overdue,
    ),
    Page(
        "The habit",
        "Clock in. ",
        "Once a day.",
        "Hold, then touch the sensor. A key sealed in your phone's secure hardware signs a tiny proof of life on Solana.",
        0.82f,
        VaultPhase.Alive,
    ),
    Page(
        "The promise",
        "If you go quiet, ",
        "it goes home.",
        "Miss your window and grace period, and your vault goes to the people you chose. No custodian, no lawyer, no seed phrase handed over.",
        1f,
        VaultPhase.Alive,
    ),
)

@Composable
fun OnboardingScreen(state: VigilState, viewModel: VigilViewModel) {
    val sender = LocalSender.current
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    LaunchedEffect(state.wallet) {
        if (state.wallet != null) viewModel.finishOnboarding()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("vigil", style = Type.Title.copy(fontStyle = FontStyle.Italic), color = Vigil.Bone)
            Spacer(Modifier.weight(1f))
            Overline("Solana · Seeker")
        }

        val page = pages[pager.currentPage]
        val remaining by animateFloatAsState(page.remaining, tween(900), label = "remaining")
        Box(Modifier.fillMaxWidth().weight(0.9f), contentAlignment = Alignment.Center) {
            PulseDial(
                remaining = remaining,
                phase = page.phase,
                hold = 0f,
                burstKey = if (last) "lit" else null,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }

        HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f)) { index ->
            val p = pages[index]
            val offset = (pager.currentPage - index + pager.currentPageOffsetFraction).absoluteValue
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp)
                    .graphicsLayer { alpha = 1f - offset.coerceIn(0f, 1f) * 0.8f },
            ) {
                Overline(p.kicker, color = Vigil.Flame)
                Spacer(Modifier.height(14.dp))
                Text(
                    buildAnnotatedString {
                        append(p.lead)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Vigil.Wick)) { append(p.emphasis) }
                    },
                    style = Type.Hero,
                )
                Spacer(Modifier.height(18.dp))
                Text(p.body, style = Type.BodyMuted)
            }
        }

        Row(Modifier.padding(horizontal = 28.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pages.indices.forEach { i ->
                val w by animateFloatAsState(if (i == pager.currentPage) 28f else 8f, label = "dot")
                Box(
                    Modifier
                        .height(4.dp)
                        .width(w.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i == pager.currentPage) Vigil.Flame else Vigil.LineStrong),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!last) {
                VigilButton("Skip", onClick = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }, kind = ButtonKind.Ghost)
                VigilButton(
                    "Continue",
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    kind = ButtonKind.Solid,
                    modifier = Modifier.weight(1f),
                )
            } else {
                VigilButton(
                    "Connect wallet",
                    onClick = { viewModel.connect(sender) },
                    loading = state.busy == Busy.Connecting,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "Works with Seed Vault and any Mobile Wallet Adapter wallet.",
            style = Type.Label,
            color = Vigil.Faint,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp),
        )
    }
}
