package com.focux.pulse.ui.screens.Timeline.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.R
import com.focux.pulse.ui.theme.*
import com.focux.pulse.utilities.ActivityType
import kotlin.math.roundToInt

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.focus.focusRequester

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineFilterSheet(
    onDismiss: () -> Unit,
    onApply: (ClosedFloatingPointRange<Float>, Set<ActivityType>, Set<String>, String, String?) -> Unit, 
    // Updated signature to accept preset^
    onClear: () -> Unit,
    initialState: com.focux.pulse.ui.screens.Timeline.TimelineViewModel.FilterState,
    availableApps: List<String>,
    availableRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    // Local State initialized from VM state, clamped to available range
    var timeRange by remember(initialState, availableRange) { 
        mutableStateOf(
            (maxOf(initialState.timeRange.start, availableRange.start)..minOf(initialState.timeRange.endInclusive, availableRange.endInclusive)).let {
                if (it.start > it.endInclusive) availableRange else it
            }
        )
    }
    // Track active preset locally
    var activePreset by remember(initialState) { mutableStateOf(initialState.activePreset) }
    
    var selectedCategories by remember { mutableStateOf(initialState.selectedCategories) }
    var searchQuery by remember { mutableStateOf(initialState.searchQuery) }
    var selectedApps by remember { mutableStateOf(initialState.selectedApps) }
    
    // ... (NestedScrollConnection logic omitted for brevity, unchanged) ...
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset = available
        }
    }
    
    // Filter available apps by search query
    val filteredApps = remember(searchQuery, availableApps) {
        if (searchQuery.isEmpty()) availableApps else availableApps.filter { 
            it.contains(searchQuery, ignoreCase = true)
        }
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .nestedScroll(connection)
            .background(PulseAppColorBackground)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
            .padding(PulseAppPaddingMedium)
            .heightIn(max = 650.dp) 
    ) {
        // ... Header ...
        // (Header content here is unchanged: Row with Icon and Filter title)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.filter_icon),
                    contentDescription = null,
                    tint = PulseAppColorPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Filter",
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 20.sp,
                        color = PulseAppColorPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Time Slot ---
        Text(
            text = "Time Slot",
            style = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 16.sp,
                color = PulseAppColorPrimary
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Time Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf("Whole Day", "Till 12pm", "12pm to 6pm", "From 6pm")
            
            presets.forEach { label ->
                val targetRange = when (label) {
                    "Whole Day" -> availableRange
                    "Till 12pm" -> availableRange.start..12f
                    "12pm to 6pm" -> 12f..18f
                    "From 6pm" -> 18f..availableRange.endInclusive
                    else -> availableRange
                }
                
                // Active if explicitly set OR (if no preset set yet) ranges match closely
                val isSelected = activePreset == label || 
                               (activePreset == null && 
                                kotlin.math.abs(timeRange.start - targetRange.start) < 0.1f &&
                                kotlin.math.abs(timeRange.endInclusive - targetRange.endInclusive) < 0.1f)

                Surface(
                    color = if (isSelected) PulseAppColorPrimary else PulseAppColorSurface, 
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            val clampedStart = maxOf(availableRange.start, targetRange.start)
                            val clampedEnd = minOf(availableRange.endInclusive, targetRange.endInclusive)
                            
                            if (clampedStart <= clampedEnd) {
                                timeRange = clampedStart..clampedEnd
                                activePreset = label // Set active preset!
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp, 
                                color = if (isSelected) Color.White else Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        RangeSlider(
            modifier = Modifier.padding(horizontal = 8.dp),
            value = timeRange,
            onValueChange = { newRange ->
                // .. Snapping logic ..
                val tolerance = 0.25f
                val step = 0.5f
                
                // Snap Start
                val distToStart = kotlin.math.abs(newRange.start - availableRange.start)
                val finalStart = if (distToStart < tolerance) availableRange.start else {
                    val snapped = (newRange.start / step).roundToInt() * step
                    maxOf(availableRange.start, snapped)
                }

                // Snap End
                val distToEnd = kotlin.math.abs(newRange.endInclusive - availableRange.endInclusive)
                val finalEnd = if (distToEnd < tolerance) availableRange.endInclusive else {
                    val snapped = (newRange.endInclusive / step).roundToInt() * step
                    minOf(availableRange.endInclusive, snapped)
                }
                
                if (finalStart <= finalEnd) {
                    timeRange = finalStart..finalEnd
                    activePreset = null // Clear preset on manual interaction!
                }
            },
            valueRange = availableRange,
            colors = SliderDefaults.colors(
                thumbColor = PulseAppColorPrimary,
                activeTrackColor = PulseAppColorPrimary,
                inactiveTrackColor = PulseAppColorSurface
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            fun formatHour(float: Float): String {
                val normalized = float % 24
                val h = normalized.toInt()
                val m = ((normalized - h) * 60).roundToInt()
                return String.format("%02d:%02d", h, m)
            }
            Text(
                text = formatHour(timeRange.start),
                style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = PulseAppColorSecondary
                )
            )
            Text(
                text = formatHour(timeRange.endInclusive),
                style = TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = PulseAppColorSecondary
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- App Category ---
        Text(
            text = "App Category",
            style = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 16.sp,
                color = PulseAppColorPrimary
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories: List<Pair<ActivityType, String>> = listOf(
                ActivityType.Productive to "Productive",
                ActivityType.Distracting to "Distracting",
                ActivityType.Neutral to "Neutral"
            )
            
            categories.forEach { entry ->
                val type = entry.first
                val label = entry.second
                val isSelected = type in selectedCategories
                
                Surface(
                    color = if (isSelected) PulseAppColorPrimary else PulseAppColorSurface, 
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            selectedCategories = if (isSelected) selectedCategories - type else selectedCategories + type
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else Color.LightGray
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Specific Apps ---
        Text(
            text = "Specific Apps",
            style = TextStyle(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 16.sp,
                color = PulseAppColorPrimary
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Search
        Surface(
            color = PulseAppColorSurface,
            shape = RoundedCornerShape(PulseAppCornerRadiusMedium),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
                // Clicking the container focuses the text field
                .clickable { focusRequester.requestFocus() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_search), 
                    contentDescription = "Search",
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(PulseAppColorPrimary),
                    modifier = Modifier.focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search by name...", color = Color.Gray, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App List
        LazyColumn(
            modifier = Modifier
                .weight(1f) // Fill remaining space
                .fillMaxWidth()
        ) {
            items(filteredApps) { pkg ->
                val isChecked = pkg in selectedApps
                // Resolve name (cached/async ideally, but synchronous for now for simplicity)
                val appName = try { 
                    com.focux.pulse.utilities.AppInfoHelper.getAppName(context, pkg) 
                } catch (e: Exception) { pkg }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            selectedApps = if (isChecked) selectedApps - pkg else selectedApps + pkg
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start: App Icon
                    // We need to display the icon. Using AndroidView for Drawable
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.ImageView(ctx)
                        },
                        update = { view ->
                             view.setImageDrawable(com.focux.pulse.utilities.AppInfoHelper.getAppIcon(view.context, pkg))
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = appName,
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    
                    Icon(
                        painter = painterResource(id = if (isChecked) R.drawable.checkbox_checked_icon else R.drawable.checkbox_unchecked_icon),
                        contentDescription = null,
                        tint = if (isChecked) PulseAppColorPrimary else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Footer Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clear Filters
            OutlinedButton(
                onClick = {
                    onClear()
                    // Reset local state
                    timeRange = availableRange
                    selectedCategories = emptySet()
                    selectedApps = emptySet()
                    searchQuery = ""
                    activePreset = null
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PulseAppColorPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(
                    text = "Clear Filters",
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            
            // Apply
            Button(
                onClick = { onApply(timeRange, selectedCategories, selectedApps, searchQuery, activePreset) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
            ) {
                Text(
                    text = "Apply",
                    style = TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}
