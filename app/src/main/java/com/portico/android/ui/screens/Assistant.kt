@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.portico.android.data.PorticoStore
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * An investment analyst rather than a chatbot: it reads the portfolio, answers
 * with the figure and the working, and admits when a question is outside what
 * it can compute. Everything happens on-device, which the surface states
 * plainly rather than implying a model call that never happens.
 */
@Composable
fun AssistantScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val scope = rememberCoroutineScope()
    val currency = store.profile.currency
    val results = store.financials()
    val portfolio = store.portfolio()

    val conversation = store.conversations.firstOrNull { it.id == state.activeConversationId }
    val focus = store.propertyById(state.assistantContextPropertyId)
    val listState = rememberLazyListState()

    LaunchedEffect(conversation?.messages?.size) {
        val count = conversation?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun ask(question: String) {
        if (question.isBlank()) return
        val now = SimpleDate.today().format()
        val target = conversation ?: AiConversation(
            id = PorticoStore.newId("conv"),
            title = question.take(48),
            contextLabel = focus?.name ?: "Portfolio",
            propertyId = focus?.id,
            createdAt = now
        ).also {
            state.activeConversationId = it.id
            store.saveConversation(it)
        }

        val withQuestion = target.copy(
            messages = target.messages + AiMessage(
                PorticoStore.newId("msg"), target.id, true, question, now
            )
        )
        store.saveConversation(withQuestion)
        state.assistantInput = ""
        state.assistantLoading = true
        state.assistantError = null

        scope.launch {
            delay(700)
            if (state.offline) {
                state.assistantLoading = false
                state.assistantError = "You're offline. Analysis runs on this device, so try again once the app settles."
                return@launch
            }
            val reply = Analyst.answer(question, results, portfolio, store.taxProfile, currency, focus)
            store.saveConversation(
                withQuestion.copy(
                    messages = withQuestion.messages + AiMessage(
                        PorticoStore.newId("msg"), target.id, false, reply.text, now, reply.workings
                    )
                )
            )
            state.assistantLoading = false
        }
    }

    Column(modifier) {
        ContextBar(state, focus)

        if (conversation == null) {
            AssistantHome(state, results, portfolio, currency, onAsk = ::ask, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                items(conversation.messages, key = { it.id }) { message ->
                    MessageBubble(message, currency)
                }
                if (state.assistantLoading) {
                    item { ThinkingRow() }
                }
                state.assistantError?.let { error ->
                    item {
                        Box(Modifier.padding(horizontal = Space.lg)) {
                            InlineError(error, onRetry = { state.assistantError = null })
                        }
                    }
                }
                item {
                    val last = conversation.messages.lastOrNull { !it.fromUser }
                    if (last != null && !state.assistantLoading) {
                        val followUps = Analyst.answer(
                            conversation.messages.lastOrNull { it.fromUser }?.content ?: "",
                            results, portfolio, store.taxProfile, currency, focus
                        ).followUps
                        SuggestionRow(followUps, onAsk = ::ask)
                    }
                }
            }
        }

        Composer(state, onSend = ::ask)
    }
}

// ------------------------------------------------------------------ context

@Composable
private fun ContextBar(state: PorticoState, focus: Property?) {
    val store = state.store
    val semantic = PorticoTheme.semantic
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PorticoIcon(Glyph.ASSISTANT, size = 18.dp, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                SectionLabel("Analysing")
                Text(
                    focus?.name ?: "Whole portfolio",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (state.activeConversationId != null) {
                TextButton(onClick = {
                    state.activeConversationId = null
                    state.assistantError = null
                }) { Text("New") }
            }
            PorticoIcon(
                if (expanded) Glyph.UP else Glyph.DOWN,
                size = 16.dp,
                tint = semantic.tertiaryText,
                contentDescription = if (expanded) "Collapse context" else "Change context"
            )
        }
        if (expanded) {
            Panel(Modifier.padding(horizontal = Space.lg)) {
                PanelHeader("Choose what to analyse")
                DataRow(
                    "Whole portfolio",
                    if (focus == null) "Selected" else "",
                    valueColor = MaterialTheme.colorScheme.primary,
                    onClick = { state.assistantContextPropertyId = null; expanded = false }
                )
                store.properties.forEach { property ->
                    Hairline()
                    DataRow(
                        property.name,
                        if (focus?.id == property.id) "Selected" else "",
                        supporting = property.location,
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = { state.assistantContextPropertyId = property.id; expanded = false }
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
        }
        Hairline(inset = 0.dp)
    }
}

// --------------------------------------------------------------------- home

@Composable
private fun AssistantHome(
    state: PorticoState,
    results: List<PropertyFinancials>,
    portfolio: PortfolioFinancials,
    currency: String,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val store = state.store
    Column(
        modifier
            .fillMaxWidth()
            .verticalScrollCompat(),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        Spacer(Modifier.height(Space.sm))

        if (results.isEmpty()) {
            EmptyState(
                title = "Nothing to analyse yet",
                body = "Add a property and I can work through its return, tax and cashflow with you.",
                glyph = Glyph.ASSISTANT,
                actionLabel = "Add a property",
                onAction = { state.resetDraft(); state.navigate(Route.ADD_PROPERTY) }
            )
            return@Column
        }

        // Insight worth surfacing before being asked.
        val weakest = results.minByOrNull { it.netYield }
        if (weakest != null) {
            Panel(Modifier.padding(horizontal = Space.lg), accent = true) {
                PanelHeader("Worth a look")
                Text(
                    if (weakest.monthlyCashflow < 0)
                        "${weakest.property.name} is running at ${Money.format(weakest.monthlyCashflow, currency)} a month after tax. Its value has grown ${Money.format(weakest.appreciation, currency)}, so the question is whether the appreciation justifies the drag."
                    else
                        "${weakest.property.name} has your lowest net yield at ${Money.percent(weakest.netYield)}, against a portfolio average of ${Money.percent(portfolio.netYield)}.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = Space.lg)
                )
                Spacer(Modifier.height(Space.md))
                Box(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                    SecondaryButton("Ask about ${weakest.property.name}") {
                        state.assistantContextPropertyId = weakest.property.id
                        onAsk("Why does ${weakest.property.name} earn least after tax?")
                    }
                }
                Spacer(Modifier.height(Space.sm))
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Ask about", supporting = "Answers come with the working, so you can check them")
            Analyst.defaultQuestions(results).forEachIndexed { index, question ->
                if (index > 0) Hairline()
                DataRow(question, "", onClick = { onAsk(question) }, trailing = {
                    PorticoIcon(Glyph.FORWARD, size = 14.dp, tint = PorticoTheme.semantic.tertiaryText, contentDescription = null)
                })
            }
        }

        if (store.conversations.isNotEmpty()) {
            Panel(Modifier.padding(horizontal = Space.lg)) {
                PanelHeader("Earlier conversations")
                store.conversations.take(6).forEachIndexed { index, conv ->
                    if (index > 0) Hairline()
                    DataRow(
                        label = conv.title,
                        value = "",
                        supporting = "${conv.contextLabel} · ${conv.createdAt}",
                        onClick = {
                            state.activeConversationId = conv.id
                            state.assistantContextPropertyId = conv.propertyId
                        },
                        trailing = {
                            GlyphButton(Glyph.DELETE, "Delete conversation") {
                                store.removeConversation(conv.id)
                            }
                        }
                    )
                }
            }
        }

        SyntheticNote(
            "Analysis runs on this device from your own records. It is information, not financial advice."
        )
        Spacer(Modifier.height(Space.lg))
    }
}

// ----------------------------------------------------------------- messages

@Composable
private fun MessageBubble(message: AiMessage, currency: String) {
    val semantic = PorticoTheme.semantic
    if (message.fromUser) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = Space.md, vertical = Space.sm)
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 4.dp, bottomEnd = 14.dp))
                    .background(semantic.panel)
                    .padding(horizontal = Space.md, vertical = Space.md)
            ) {
                Text(message.content, style = MaterialTheme.typography.bodyLarge)
            }
            if (message.workings.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                Panel {
                    PanelHeader("Working")
                    Column(Modifier.padding(start = Space.lg, end = Space.lg, bottom = Space.md)) {
                        message.workings.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            "Working through your figures",
            style = MaterialTheme.typography.bodySmall,
            color = PorticoTheme.semantic.tertiaryText
        )
    }
}

@Composable
private fun SuggestionRow(suggestions: List<String>, onAsk: (String) -> Unit) {
    if (suggestions.isEmpty()) return
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        suggestions.forEach { suggestion ->
            Box(
                Modifier
                    .clip(ControlShape)
                    .background(PorticoTheme.semantic.panelSunk)
                    .clickable { onAsk(suggestion) }
                    .padding(horizontal = Space.md, vertical = Space.sm)
            ) {
                Text(suggestion, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Composer(state: PorticoState, onSend: (String) -> Unit) {
    Column {
        Hairline(inset = 0.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Space.md)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            OutlinedTextField(
                value = state.assistantInput,
                onValueChange = { state.assistantInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about your portfolio") },
                shape = ControlShape,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = PorticoTheme.semantic.rule,
                    unfocusedContainerColor = PorticoTheme.semantic.panelSunk.copy(alpha = .5f),
                    focusedContainerColor = PorticoTheme.semantic.panelSunk.copy(alpha = .5f)
                )
            )
            FilledIconButton(
                onClick = { onSend(state.assistantInput) },
                enabled = state.assistantInput.isNotBlank() && !state.assistantLoading,
                modifier = Modifier.size(52.dp),
                shape = ControlShape
            ) {
                PorticoIcon(
                    Glyph.FORWARD,
                    size = 20.dp,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    contentDescription = "Send question"
                )
            }
        }
    }
}

/** Scroll helper so the home column behaves inside the fixed-height shell. */
@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.verticalScroll(rememberScrollState())
