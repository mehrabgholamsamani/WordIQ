package com.wordiq.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import android.os.Build
import com.wordiq.app.ui.theme.BluePrimary
import com.wordiq.app.ui.theme.BlueSoft
import com.wordiq.app.ui.theme.SurfaceElevated
import com.wordiq.app.ui.theme.TextSecondary
import com.wordiq.app.ui.theme.BackgroundPrimary
import com.wordiq.app.ui.theme.MotionTokens
import com.wordiq.app.ui.theme.Radius
import com.wordiq.app.ui.theme.Spacing

val ScreenPadding = Spacing.XL
val CardRadius = Radius.Large
val ControlRadius = Radius.Medium

@Composable
fun motionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.animation.ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
    }
}

@Composable
fun PrimaryPracticeButton(
    minutes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Practice",
    icon: ImageVector = Icons.Default.PlayArrow,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val animate = motionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) 0.975f else 1f,
        animationSpec = spring(dampingRatio = MotionTokens.Damping, stiffness = MotionTokens.Stiffness),
        label = "practicePress",
    )
    Button(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().height(96.dp).scale(scale),
        shape = RoundedCornerShape(CardRadius),
        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column {
                if (minutes != null) Text("$minutes min", style = MaterialTheme.typography.bodyMedium)
                Text(label, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    val shape = RoundedCornerShape(CardRadius)
    val colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    if (onClick == null) {
        Card(modifier = cardModifier, shape = shape, colors = colors, elevation = elevation) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
        }
    } else {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val animate = motionEnabled()
        val scale by animateFloatAsState(
            targetValue = if (pressed && animate) MotionTokens.PressedScale else 1f,
            animationSpec = spring(dampingRatio = MotionTokens.Damping, stiffness = MotionTokens.Stiffness),
            label = "cardPress",
        )
        Card(
            onClick = onClick,
            modifier = cardModifier.scale(scale),
            shape = shape,
            colors = colors,
            elevation = elevation,
            interactionSource = interaction,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
        }
    }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).semantics { role = Role.Button }) {
        Icon(icon, contentDescription = description, tint = TextSecondary)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WordIqTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundPrimary),
    )
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Text(title, color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, shape = RoundedCornerShape(Radius.Medium)) { Text(actionLabel) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MasteryIndicator(progress: Float, modifier: Modifier = Modifier) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(5.dp),
        color = BluePrimary,
        trackColor = BlueSoft,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
