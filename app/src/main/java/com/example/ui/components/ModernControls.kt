package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ShutterGold

/**
 * Translucent glass stadium container for grouping modern toolbar actions.
 */
@Composable
fun ModernFloatingCapsule(
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0x99121822),
    borderColor: Color = Color.White.copy(alpha = 0.16f),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        containerColor.copy(alpha = (containerColor.alpha + 0.10f).coerceAtMost(1f)),
                        containerColor
                    )
                )
            )
            .border(0.9.dp, borderColor, CircleShape)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

/**
 * Circular frosted-glass icon button with spring press bounce, active accent glow, and optional micro-badge.
 */
@Composable
fun ModernGlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = ShutterGold,
    inactiveIconTint: Color = Color.White,
    buttonSize: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    standaloneGlass: Boolean = true,
    badgeDot: Boolean = false,
    badgeDotColor: Color = ShutterGold,
    badgeText: String? = null,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "glassCircleScale"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            active -> activeColor.copy(alpha = 0.22f)
            standaloneGlass -> Color(0x99121822)
            else -> Color.Transparent
        },
        label = "glassCircleBg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            active -> activeColor.copy(alpha = 0.78f)
            standaloneGlass -> Color.White.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        label = "glassCircleBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) activeColor else inactiveIconTint,
        label = "glassCircleIcon"
    )

    val rootMod = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        modifier = rootMod
            .size(48.dp)
            .scale(pressScale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(bgColor)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(0.95.dp, borderColor, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }

        if (badgeDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeDotColor)
                    .border(1.2.dp, Color(0xFF0E131A), CircleShape)
            )
        }

        if (!badgeText.isNullOrBlank()) {
            Surface(
                color = activeColor,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF141619),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                )
            }
        }
    }
}

/**
 * Circular glass pod action button for the bottom camera deck with spring press feedback & active halo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernDeckPodButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = ShutterGold,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
        label = "deckPodScale"
    )
    val podBrush = if (active) {
        Brush.verticalGradient(
            listOf(activeColor.copy(alpha = 0.30f), activeColor.copy(alpha = 0.14f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.06f))
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .width(64.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(podBrush)
                .border(
                    width = if (active) 1.4.dp else 0.9.dp,
                    color = if (active) activeColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.22f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeColor else Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            color = if (active) activeColor else Color.White.copy(alpha = 0.90f),
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Compact stadium toggle pill for viewfinder quick controls (Auto Capture, Torch, Burst, Interval).
 */
@Composable
fun ModernMiniTogglePill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = ShutterGold,
    testTag: String? = null
) {
    val bg by animateColorAsState(
        targetValue = if (active) activeColor else Color(0xA6121822),
        label = "miniPillBg"
    )
    val fg by animateColorAsState(
        targetValue = if (active) Color(0xFF141619) else Color.White,
        label = "miniPillFg"
    )
    val border = if (active) activeColor else Color.White.copy(alpha = 0.18f)
    val mod = if (testTag != null) modifier.testTag(testTag) else modifier

    Surface(
        color = bg,
        shape = CircleShape,
        modifier = mod
            .border(0.9.dp, border, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1
            )
        }
    }
}
