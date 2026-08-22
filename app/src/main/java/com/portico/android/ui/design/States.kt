package com.portico.android.ui.design
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.portico.android.ui.theme.PorticoTheme

/*
 * The seven states every surface owes the user (blueprint section 48), built
 * once here so no screen improvises its own.
 *
 * Two rules run through all of them. Empty states teach the next action rather
 * than announcing absence, and error states name both the problem and the way
 * out. A message with no recovery is a dead end, which is what most of the
 * old build's placeholder snackbars were.
 */

// ------------------------------------------------------------------ loading

/** Shimmering block. Used to hold layout, never a spinner floating in content. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    widthFraction: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = stringResource(R.string.skeleton))
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = stringResource(R.string.skeleton_alpha)
    )
    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(PorticoTheme.semantic.panelSunk.copy(alpha = alpha))
    )
}

/** Panel-shaped placeholder matching the real content's rhythm. */
@Composable
fun LoadingPanel(modifier: Modifier = Modifier, rows: Int = 3) {
    // Hoisted: a semantics block is not a composable scope.
    val loading = stringResource(R.string.loading)
    Panel(modifier.semantics { contentDescription = loading }) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            SkeletonBlock(height = 12.dp, widthFraction = 0.35f)
            SkeletonBlock(height = 30.dp, widthFraction = 0.65f)
            repeat(rows) {
                SkeletonBlock(height = 14.dp, widthFraction = if (it % 2 == 0) 0.9f else 0.75f)
            }
        }
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier, label: String = "Loading your portfolio") {
    Column(
        modifier
            .fillMaxWidth()
            .padding(Space.lg)
            .semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        LoadingPanel(rows = 2)
        LoadingPanel(rows = 4)
    }
}

// ------------------------------------------------------------- message base

/**
 * Shared frame for every non-loading state: glyph, headline, explanation, and
 * up to two actions. Consistent shape means users learn it once.
 */
@Composable
fun StateMessage(
    glyph: Glyph,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    primaryAction: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryAction: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val semantic = PorticoTheme.semantic
    val accent = tone ?: MaterialTheme.colorScheme.primary
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.xxl)
            .semantics {
                contentDescription = "$title. $body"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            PorticoIcon(glyph, size = 26.dp, tint = accent, contentDescription = null)
        }
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = semantic.tertiaryText,
            textAlign = TextAlign.Center
        )
        if (primaryAction != null && onPrimary != null) {
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(primaryAction, onClick = onPrimary)
        }
        if (secondaryAction != null && onSecondary != null) {
            Spacer(Modifier.height(Space.sm))
            SecondaryButton(secondaryAction, onClick = onSecondary)
        }
    }
}

// -------------------------------------------------------------------- empty

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    glyph: Glyph = Glyph.EMPTY_BOX,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) = StateMessage(
    glyph = glyph,
    title = title,
    body = body,
    modifier = modifier,
    tone = PorticoTheme.semantic.neutral,
    primaryAction = actionLabel,
    onPrimary = onAction
)

// -------------------------------------------------------------------- error

@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) = StateMessage(
    glyph = Glyph.WARNING,
    title = title,
    body = body,
    modifier = modifier,
    tone = MaterialTheme.colorScheme.error,
    primaryAction = if (onRetry != null) retryLabel else null,
    onPrimary = onRetry,
    secondaryAction = secondaryLabel,
    onSecondary = onSecondary
)

/** Compact inline error for a form or a single panel. */
@Composable
fun InlineError(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(Space.md)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        PorticoIcon(Glyph.WARNING, size = 16.dp, tint = MaterialTheme.colorScheme.onErrorContainer, contentDescription = null)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            Text(
                stringResource(R.string.retry),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.12f))
                    .padding(horizontal = Space.sm, vertical = Space.xs)
            )
        }
    }
}

// ------------------------------------------------------------------ success

@Composable
fun SuccessState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) = StateMessage(
    glyph = Glyph.CHECK,
    title = title,
    body = body,
    modifier = modifier,
    tone = PorticoTheme.semantic.gain,
    primaryAction = actionLabel,
    onPrimary = onAction,
    secondaryAction = secondaryLabel,
    onSecondary = onSecondary
)

// ------------------------------------------------------------------ offline

/** Persistent strip, not a dialog, so the user can keep working while offline. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val semantic = PorticoTheme.semantic
    Row(
        modifier
            .fillMaxWidth()
            .background(semantic.panelSunk)
            .padding(horizontal = Space.lg, vertical = Space.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        PorticoIcon(Glyph.OFFLINE, size = 15.dp, tint = semantic.tertiaryText, contentDescription = null)
        Text(
            stringResource(R.string.offline_cached_records_remain_available_cloud),
            style = MaterialTheme.typography.bodySmall,
            color = semantic.tertiaryText,
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            Text(
                stringResource(R.string.retry),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.xs)
            )
        }
    }
}

// -------------------------------------------------------- permission denied

@Composable
fun PermissionDeniedState(
    what: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) = StateMessage(
    glyph = Glyph.LOCK,
    title = "You don't have access to $what",
    body = stringResource(R.string.this_record_belongs_to_another_member_of_the_w),
    modifier = modifier,
    tone = PorticoTheme.semantic.neutral,
    primaryAction = if (onBack != null) "Go back" else null,
    onPrimary = onBack
)

// ---------------------------------------------------------- session expired

@Composable
fun SessionExpiredState(
    modifier: Modifier = Modifier,
    onSignIn: () -> Unit,
    onUseDemo: (() -> Unit)? = null
) = StateMessage(
    glyph = Glyph.KEY,
    title = stringResource(R.string.your_session_ended),
    body = stringResource(R.string.you_were_signed_out_to_keep_your_financial_rec),
    modifier = modifier,
    tone = MaterialTheme.colorScheme.primary,
    primaryAction = "Sign in",
    onPrimary = onSignIn,
    secondaryAction = if (onUseDemo != null) stringResource(R.string.continue_with_demo_data) else null,
    onSecondary = onUseDemo
)

// --------------------------------------------------------------- restricted

/** Plan gate. Explains the limit and the way past it without hard-blocking. */
@Composable
fun PlanLimitState(
    limit: Int,
    modifier: Modifier = Modifier,
    onUpgrade: () -> Unit,
    onDismiss: (() -> Unit)? = null
) = StateMessage(
    glyph = Glyph.SUBSCRIPTION,
    title = stringResource(R.string.free_covers_n, limit),
    body = stringResource(R.string.you_ve_reached_the_free_plan_s_register_limit),
    modifier = modifier,
    tone = MaterialTheme.colorScheme.primary,
    primaryAction = "See Pro",
    onPrimary = onUpgrade,
    secondaryAction = if (onDismiss != null) "Not now" else null,
    onSecondary = onDismiss
)
