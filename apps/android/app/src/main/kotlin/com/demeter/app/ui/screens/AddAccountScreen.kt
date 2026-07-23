@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.demeter.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.demeter.app.R
import com.demeter.app.ui.DemeterViewModel
import com.demeter.domain.model.Provider

/**
 * Compressed add flow: provider + nickname here, then straight into recording the
 * first usage evidence. Reminders can be configured afterwards — never forced.
 */
@Composable
fun AddAccountScreen(
    viewModel: DemeterViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    var provider by remember { mutableStateOf(Provider.OPENAI) }
    var nickname by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp),
        ) {
            Text("Choose a provider", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Provider.entries.forEach { p ->
                    ProviderCard(
                        provider = p,
                        selected = provider == p,
                        onClick = { provider = p },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Identified by name only — Demeter never signs in to your ${provider.accountNoun} or asks for a password. Independent app, not affiliated with OpenAI, Anthropic, or Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it; error = null },
                label = { Text("Nickname (e.g. Personal, Work)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val name = nickname.trim().ifEmpty { "${provider.displayLabel} account" }
                    viewModel.addAccount(provider, name) { id ->
                        if (id == null) {
                            error = "You already monitor 3 ${provider.displayLabel} accounts — that's the v1 limit."
                        } else {
                            onCreated(id)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Next: record current usage")
            }
        }
    }
}

/**
 * Large provider tile. Uses an original, generic mark (not the provider's trademarked logo)
 * plus its name — Demeter is an independent app and deliberately does not reproduce OpenAI's
 * or Anthropic's brand assets.
 */
@Composable
private fun ProviderCard(
    provider: Provider,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = providerAccent(provider)
    Card(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = if (selected) BorderStroke(2.dp, accent) else null,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(providerLogoRes(provider)),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                provider.displayLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                provider.accountNoun,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun providerAccent(p: Provider): Color = when (p) {
    Provider.OPENAI -> Color(0xFF10877A)
    Provider.ANTHROPIC -> Color(0xFFCC7A52)
    Provider.GOOGLE -> Color(0xFF4285F4)
}
