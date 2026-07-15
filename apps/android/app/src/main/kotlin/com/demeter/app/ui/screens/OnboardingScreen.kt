package com.demeter.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Zero permission or consent prompts here — the first system prompt a user ever sees
 * is POST_NOTIFICATIONS, after they enable a reminder. Local is the dominant path;
 * sign-in is introduced later, only where email reminders need it.
 */
@Composable
fun OnboardingScreen(onContinueLocally: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🌾", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(20.dp))
        Text(
            "Know what remains.\nUse it before it resets.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(20.dp))
        Benefit("One glanceable place for your ChatGPT and Claude usage allowances.")
        Benefit("Truthful reminders before resets — with the age of the data always visible.")
        Benefit("Local-first. Demeter never asks for your provider passwords and does not connect to consumer accounts automatically.")
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinueLocally, modifier = Modifier.fillMaxWidth()) {
            Text("Continue locally")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Sign in with Google is only needed for email reminders — you can add it later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Benefit(text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Text("·", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
