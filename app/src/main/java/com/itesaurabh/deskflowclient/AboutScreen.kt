package com.itesaurabh.deskflowclient

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The lockup's own 1089 x 380 viewport, so it never distorts. */
private const val LOGO_ASPECT = 1089f / 380f

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Chosen from the theme actually in effect, not the -night resource qualifier: the
            // in-app theme toggle can disagree with the system, and qualifiers only follow the
            // system. Getting that wrong hid the white wordmark on a light background.
            val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Image(
                painter = painterResource(
                    if (darkSurface) R.drawable.logo_full_dark else R.drawable.logo_full_light
                ),
                contentDescription = "Deskflow Droid",
                modifier = Modifier
                    .height(72.dp)
                    .aspectRatio(LOGO_ASPECT)
            )
            Text(
                text = "Version ${versionName(context)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Makes this device one of the screens in your desktop's Deskflow layout. " +
                "When the desktop cursor crosses the edge towards it, the mouse and keyboard " +
                "land here instead.",
            style = MaterialTheme.typography.bodyLarge
        )

        AboutSection("Connection") {
            Fact("Protocol", "Barrier / Synergy 1.8")
            Fact("Default port", Protocol.DEFAULT_PORT.toString())
            Fact("Encryption", "Optional TLS 1.2, pinned on first use")
            Body(
                "The client name must match a screen name in the server's layout exactly, or the " +
                    "server rejects the connection."
            )
        }

        AboutSection("What works") {
            Body(
                "Cursor movement, tap, drag, flick and wheel scrolling. Typing into text fields, " +
                    "including backspace, delete, arrow keys, Home, End and Enter. Select all, " +
                    "copy, cut and paste. Escape and right-click go Back; Alt+Tab opens Recents."
            )
        }

        AboutSection("What doesn't") {
            Body(
                "Arbitrary keyboard shortcuts, and typing anywhere that isn't a text field — " +
                    "games, canvases, drawing surfaces, and apps that don't expose their text to " +
                    "accessibility tools."
            )
            Body(
                "This is a platform limit, not a missing feature. Android has no public API for " +
                    "injecting key events, so an accessibility service can only act on what an app " +
                    "chooses to expose. Lifting it needs a different injection backend."
            )
        }

        AboutSection("Permissions") {
            Fact("Accessibility", "Draws the cursor and performs the taps, drags and edits")
            Fact("Notifications", "Shows the ongoing connection, which keeps Android from killing the app mid-session")
            Fact("Network", "Speaks to the Deskflow server on your local network")
        }

        Text(
            text = "An unofficial third-party client, not affiliated with or endorsed by the " +
                "Deskflow project. Report problems with this app here, not upstream.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Fact(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(0.9f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (value.all { it.isDigit() }) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(2f)
            )
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp
    )
}

private fun versionName(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"
