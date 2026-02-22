package com.focux.pulse.ui.screens.Configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.focux.pulse.R
import com.focux.pulse.data.local.entities.AppCategory
import com.focux.pulse.data.local.entities.AppInfo
import com.focux.pulse.ui.theme.*
import com.focux.pulse.utilities.AppInfoHelper
import com.focux.pulse.utilities.PULSE_IGNORED_APPS

// ─────────────────────────────────────────────────────────────────
// Main Entry Point
// ─────────────────────────────────────────────────────────────────
@Composable
fun AppCategorySection(
    state: AppCategoryUiState,
    onToggleEditMode: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryChanged: (String, String) -> Unit // (packageName, newCategory)
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Transparent,
                RoundedCornerShape(PulseAppCornerRadiusMedium)
            )
            .border(
                width = PulseAppBorderWidthThick,
                color = PulseAppColorPrimary,
                shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
            )
            .padding(
                horizontal = PulseAppPaddingMedium,
                vertical = PulseAppPaddingSmall
            )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Header Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Category",
                    style = PulseAppFontLabel.copy(color = PulseAppColorPrimary)
                )

                Surface(
                    color = if (state.isEditMode) PulseAppColorPrimary else Color.Transparent,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onToggleEditMode() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.edit_icon),
                            contentDescription = if (state.isEditMode) "Done" else "Edit",
                            tint = if (state.isEditMode) Color.White else PulseAppColorPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.isEditMode) "Done" else "Edit",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isEditMode) Color.White else PulseAppColorPrimary
                            )
                        )
                    }
                }
            }

            if (state.isEditMode) {
                EditModeContent(
                    state = state,
                    onSearchQueryChanged = onSearchQueryChanged,
                    onCategoryChanged = onCategoryChanged
                )
            } else {
                ViewModeContent(state = state)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// VIEW MODE — Icon grids grouped by category
// ─────────────────────────────────────────────────────────────────

@Composable
private fun ViewModeContent(state: AppCategoryUiState) {
    val context = LocalContext.current

    // Shared state to track which app's tooltip is currently visible
    var tappedAppPackage by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss after 3 seconds
    LaunchedEffect(tappedAppPackage) {
        if (tappedAppPackage != null) {
            kotlinx.coroutines.delay(3000)
            tappedAppPackage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { tappedAppPackage = null } // Dismiss on tap outside
    ) {
        // ── Ignored Apps (system + user) ──
        val launchers = remember(context) { AppInfoHelper.getLauncherPackages(context) }
        val systemIgnored = remember(launchers) { (PULSE_IGNORED_APPS + launchers).distinct() }
        val userIgnored = state.ignoredApps

        if (systemIgnored.isNotEmpty() || userIgnored.isNotEmpty()) {
            Text(
                text = "Ignored",
                style = PulseAppFontLabel.copy(color = Color.Gray)
            )
            // System ignored (text-based, as original)
            systemIgnored.forEach { pkg ->
                val isLauncher = pkg in launchers
                Text(
                    text = "• $pkg${if (isLauncher) " (Launcher)" else " (System)"}",
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Gray)
                )
            }
            // User ignored (with icons)
            if (userIgnored.isNotEmpty()) {
                userIgnored.forEach { app ->
                    Text(
                        text = "• ${app.appName}",
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.Gray)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Category Grids ──
        CategoryIconGrid(
            title = "Productive",
            color = PulseAppColorProductive,
            apps = state.productiveApps,
            tappedAppPackage = tappedAppPackage,
            onAppTapped = { pkg -> tappedAppPackage = pkg }
        )

        CategoryIconGrid(
            title = "Distracting",
            color = PulseAppColorDistracting,
            apps = state.distractingApps,
            tappedAppPackage = tappedAppPackage,
            onAppTapped = { pkg -> tappedAppPackage = pkg }
        )

        CategoryIconGrid(
            title = "Neutral",
            color = PulseAppColorNeutral,
            apps = state.neutralApps,
            tappedAppPackage = tappedAppPackage,
            onAppTapped = { pkg -> tappedAppPackage = pkg }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// Category Icon Grid — subtitle + rows of tappable app icons (8 per row)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun CategoryIconGrid(
    title: String,
    color: Color,
    apps: List<AppInfo>,
    tappedAppPackage: String?,
    onAppTapped: (String?) -> Unit
) {
    if (apps.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = android.content.res.Resources.getSystem().displayMetrics

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$title (${apps.size})",
            style = PulseAppFontLabel.copy(color = color)
        )
    }

    Spacer(modifier = Modifier.height(4.dp))

    // 8 icons per row, equally sized
    val iconsPerRow = 8
    val rows = apps.chunked(iconsPerRow)
    rows.forEach { rowApps ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            rowApps.forEach { app ->
                val displayName = app.appName.ifEmpty { app.packageName }
                var iconCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .onGloballyPositioned { coords -> iconCoords = coords }
                        .clickable { onAppTapped(app.packageName) }
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx -> android.widget.ImageView(ctx) },
                        update = { view ->
                            view.setImageDrawable(AppInfoHelper.getAppIcon(view.context, app.packageName))
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // ── Dynamic Tooltip Popup anchored natively to this icon ──
                    if (tappedAppPackage == app.packageName && iconCoords != null) {
                        val coords = iconCoords!!
                        val windowPos = coords.localToWindow(Offset.Zero)
                        
                        val screenWidthPx = configuration.widthPixels.toFloat()
                        val screenHeightPx = configuration.heightPixels.toFloat()
                        
                        val isLeftHalf = windowPos.x < screenWidthPx / 2f
                        val isTopHalf = windowPos.y < screenHeightPx / 2f
                        
                        val boxWidth = coords.size.width
                        val boxHeight = coords.size.height
                        val padPx = with(density) { 4.dp.roundToPx() }
                        
                        // Let the popup expand diagonally towards the screen center from the optimal corner
                        val alignment: Alignment
                        val offsetX: Int
                        val offsetY: Int
                        
                        if (isLeftHalf && isTopHalf) {
                            alignment = Alignment.TopStart
                            offsetX = boxWidth + padPx
                            offsetY = boxHeight + padPx
                        } else if (!isLeftHalf && isTopHalf) {
                            alignment = Alignment.TopEnd
                            offsetX = -boxWidth - padPx
                            offsetY = boxHeight + padPx
                        } else if (isLeftHalf && !isTopHalf) {
                            alignment = Alignment.BottomStart
                            offsetX = boxWidth + padPx
                            offsetY = -boxHeight - padPx
                        } else {
                            alignment = Alignment.BottomEnd
                            offsetX = -boxWidth - padPx
                            offsetY = -boxHeight - padPx
                        }

                        Popup(
                            alignment = alignment,
                            offset = IntOffset(offsetX, offsetY),
                            onDismissRequest = { onAppTapped(null) }
                        ) {
                            Surface(
                                color = PulseAppColorBackground.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 8.dp,
                                modifier = Modifier.clickable { onAppTapped(null) }
                            ) {
                                Text(
                                    text = displayName,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PulseAppColorPrimary
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            // Fill remaining slots with invisible spacers to keep equal sizing
            val remaining = iconsPerRow - rowApps.size
            repeat(remaining) {
                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}

// ─────────────────────────────────────────────────────────────────
// EDIT MODE — Search + App list with category dropdowns
// ─────────────────────────────────────────────────────────────────
@Composable
private fun EditModeContent(
    state: AppCategoryUiState,
    onSearchQueryChanged: (String) -> Unit,
    onCategoryChanged: (String, String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // ── Search Bar ──
    Surface(
        color = PulseAppColorBackground,
        shape = RoundedCornerShape(PulseAppCornerRadiusMedium),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
            .clickable { focusRequester.requestFocus() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search_icon),
                contentDescription = "Search",
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(PulseAppColorPrimary),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            "Search apps...",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    innerTextField()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── App List ──
    // Use a fixed-height container so the Configuration screen remains scrollable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(state.allApps, key = { it.packageName }) { app ->
                AppCategoryRow(
                    app = app,
                    onCategoryChanged = { newCat -> onCategoryChanged(app.packageName, newCat) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Single app row with dropdown
// ─────────────────────────────────────────────────────────────────
@Composable
private fun AppCategoryRow(
    app: AppInfo,
    onCategoryChanged: (String) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val categoryColor = when (app.category) {
        AppCategory.PRODUCTIVE -> PulseAppColorProductive
        AppCategory.DISTRACTING -> PulseAppColorDistracting
        AppCategory.NEUTRAL -> PulseAppColorNeutral
        AppCategory.IGNORED -> Color.Gray
        else -> PulseAppColorNeutral
    }

    val categoryLabel = when (app.category) {
        AppCategory.PRODUCTIVE -> "Productive"
        AppCategory.DISTRACTING -> "Distracting"
        AppCategory.NEUTRAL -> "Neutral"
        AppCategory.IGNORED -> "Ignored"
        else -> "Neutral"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PulseAppColorBackground.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> android.widget.ImageView(ctx) },
            update = { view ->
                view.setImageDrawable(AppInfoHelper.getAppIcon(view.context, app.packageName))
            },
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // App name
        Text(
            text = app.appName.ifEmpty { app.packageName },
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color.White
            ),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Category dropdown chip
        Box {
            Surface(
                color = categoryColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { expanded = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(categoryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = categoryLabel,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "▼",
                        style = TextStyle(fontSize = 8.sp, color = categoryColor)
                    )
                }
            }

            // Dropdown menu
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(PulseAppColorBackground)
            ) {
                val options = listOf(
                    AppCategory.PRODUCTIVE to "Productive" to PulseAppColorProductive,
                    AppCategory.NEUTRAL to "Neutral" to PulseAppColorNeutral,
                    AppCategory.DISTRACTING to "Distracting" to PulseAppColorDistracting,
                    AppCategory.IGNORED to "Ignored" to Color.Gray
                )
                options.forEach { (catPair, color) ->
                    val (catValue, catLabel) = catPair
                    val isSelected = app.category == catValue
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = catLabel,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) color else Color.White
                                    )
                                )
                            }
                        },
                        onClick = {
                            onCategoryChanged(catValue)
                            expanded = false
                        },
                        modifier = Modifier.background(
                            if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}
