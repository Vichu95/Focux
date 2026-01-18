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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineFilterSheet(
    onDismiss: () -> Unit,
    onApply: (ClosedFloatingPointRange<Float>, Set<ActivityType>, Set<String>, String) -> Unit,
    onClear: () -> Unit,
    initialState: com.focux.pulse.ui.screens.Timeline.TimelineViewModel.FilterState,
    availableApps: List<String>,
    modifier: Modifier = Modifier
) {
    // Local State initialized from VM state
    var timeRange by remember { mutableStateOf(initialState.timeRange) }
    var selectedCategories by remember { mutableStateOf(initialState.selectedCategories) }
    var searchQuery by remember { mutableStateOf(initialState.searchQuery) }
    var selectedApps by remember { mutableStateOf(initialState.selectedApps) }
    
    // Filter available apps by search query
    val filteredApps = remember(searchQuery, availableApps) {
        if (searchQuery.isEmpty()) availableApps else availableApps.filter { 
            it.contains(searchQuery, ignoreCase = true) // In real app, search by Label not package
        }
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PulseAppColorBackground)
            .padding(PulseAppPaddingMedium)
            .heightIn(max = 600.dp) 
    ) {
        // --- Header ---
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
            Icon(
                painter = painterResource(id = R.drawable.close_icon),
                contentDescription = "Close",
                tint = PulseAppColorPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onDismiss() }
            )
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
        Spacer(modifier = Modifier.height(8.dp))
        
        RangeSlider(
            value = timeRange,
            onValueChange = { timeRange = it },
            valueRange = 0f..24f,
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
                val h = float.toInt()
                val m = ((float - h) * 60).toInt()
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
                        .height(32.dp)
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
            modifier = Modifier.fillMaxWidth().height(40.dp)
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
                            android.widget.ImageView(ctx).apply {
                                setImageDrawable(com.focux.pulse.utilities.AppInfoHelper.getAppIcon(ctx, pkg))
                            }
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
                    timeRange = 0f..24f
                    selectedCategories = emptySet()
                    selectedApps = emptySet()
                    searchQuery = ""
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
                        color = Color.White // Fix: Ensure text is white
                    )
                )
            }
            
            // Apply
            Button(
                onClick = { onApply(timeRange, selectedCategories, selectedApps, searchQuery) },
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
