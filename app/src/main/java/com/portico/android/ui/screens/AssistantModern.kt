package com.portico.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.model.ChatMessage
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val motionEnabled = LocalPorticoMotion.current
    val prompts = listOf("What is driving my net return?", "Compare Harbor House and Lakeside", "Test an $800 rent scenario", "Where is my portfolio concentrated?")
    LaunchedEffect(Unit) {
        if (state.assistantMessages.isEmpty()) {
            state.assistantMessages = listOf(ChatMessage("I can read return, cashflow, tax exposure, and acquisition scenarios from the current perimeter.", false, "Portico intelligence"))
        }
    }
    fun submit() {
        val input = state.assistantInput.trim()
        if (input.isBlank() || state.assistantLoading) return
        state.assistantMessages = state.assistantMessages + ChatMessage(input, true, state.assistantContext)
        state.assistantInput = ""
        state.assistantLoading = true
        state.assistantError = null
        scope.launch {
            delay(if (motionEnabled) 720 else 80)
            val answer = offlineAssistantAnswer(state.assistantContext, input)
            state.assistantOnline = false
            state.assistantError = "Offline demo mode · no portfolio data leaves this device."
            state.assistantMessages = state.assistantMessages + ChatMessage(answer, false, "Offline demo")
            state.assistantLoading = false
        }
    }

    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PorticoLogoMark(Modifier.size(48.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Portico intelligence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Context-aware investment assistance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    InstrumentBadge("RELAY", "OFFLINE", PorticoOrange)
                }
                Text("Ask about the whole orbit, a property, or a scenario. Answers are interpretations, not financial, tax, or legal advice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Portfolio", "Harbor House", "Lakeside Duplex", "Scenario").forEach { context ->
                FilterChip(selected = state.assistantContext == context, onClick = { state.assistantContext = context }, label = { Text(context) })
            }
        }
        if (state.assistantMessages.size <= 1) {
            SectionHeading("Start with a question", "Suggested prompts grounded in your register")
            prompts.forEach { prompt ->
                Surface(modifier = Modifier.fillMaxWidth().clickable { state.assistantInput = prompt }, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        PorticoGlyph(PorticoGlyphType.Assistant, Modifier.size(19.dp), PorticoTeal)
                        Text(prompt, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        PorticoGlyph(PorticoGlyphType.Forward, Modifier.size(18.dp), PorticoMuted)
                    }
                }
            }
        }
        state.assistantMessages.forEach { message -> ModernChatBubble(message) }
        if (state.assistantLoading) Text("Reading the current perimeter…", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
        if (state.assistantError != null) Text(state.assistantError!!, style = MaterialTheme.typography.labelSmall, color = PorticoOrange)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.assistantInput, onValueChange = { state.assistantInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask the cockpit…") }, minLines = 1, maxLines = 4)
            Button(onClick = ::submit, modifier = Modifier.size(56.dp), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send question", modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun offlineAssistantAnswer(context: String, input: String): String {
    val question = input.lowercase()
    return when {
        "compare" in question || "harbor" in question && "lakeside" in question ->
            "Harbor House leads on ROI at 23.3% with $2,130 monthly cashflow. Lakeside Duplex has the stronger net yield at 7.2% and $1,740 monthly cashflow. Treat this as illustrative demo data until verified records and a secure AI relay are connected."
        "rent" in question || "scenario" in question ->
            "For the Scenario view, test rent, vacancy, and operating costs together. The current perimeter averages $4,820 monthly net cashflow before a new assumption is applied. Treat this as an illustrative demo response until verified records and a secure AI relay are connected."
        "concentrated" in question || "location" in question || "country" in question ->
            "The portfolio is concentrated in Uruguay: Montevideo and Canelones represent 81% of the illustrated allocation, with Buenos Aires making up the remaining 19%. Treat this as illustrative demo data until verified records and a secure AI relay are connected."
        "return" in question || "roi" in question ->
            "Harbor House is the strongest illustrated return driver at 23.3% ROI, followed by Lakeside Duplex at 14.2%. Across the perimeter, the modeled total return is 11.1%. Treat this as illustrative demo data until verified records and a secure AI relay are connected."
        else ->
            "In the $context view, the clearest next read is net cashflow: the portfolio keeps about $4,820 each month after operating costs. Ask about return, rent, comparison, or concentration for a more specific demo read."
    }
}

@Composable
private fun ModernChatBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp, 20.dp, if (message.fromUser) 5.dp else 20.dp, if (message.fromUser) 20.dp else 5.dp), color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, border = if (message.fromUser) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(modifier = Modifier.width(320.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.context != null) Text(message.context, style = MaterialTheme.typography.labelSmall, color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = .72f) else PorticoTeal)
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
