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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focux.pulse.R
import com.focux.pulse.data.local.entities.AppCategory
import com.focux.pulse.data.local.entities.AppInfo
import com.focux.pulse.ui.theme.*
import com.focux.pulse.utilities.AppInfoHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MindfulLimitsSection(
    viewModel: MindfulLimitsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    
    // State for Bottom Sheets
    var appToTune by remember { mutableStateOf<AppInfo?>(null) }
    var tuningGlobals by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .border(PulseAppBorderWidthThick, PulseAppColorPrimary, RoundedCornerShape(PulseAppCornerRadiusMedium))
            .padding(horizontal = PulseAppPaddingMedium, vertical = PulseAppPaddingSmall)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mindful Limits", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)

                Surface(
                    color = PulseAppColorSurface,
                    shape = RoundedCornerShape(PulseAppCornerRadiusMedium),
                    modifier = Modifier
                        .width(100.dp).height(PulseAppTimelineFilterButtonHeight)
                        .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
                        .clickable { viewModel.toggleEditMode() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (state.isEditMode) "Save" else "Edit",
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = PulseAppColorSecondary)
                        )
                    }
                }
            }

            if (state.isEditMode) {
                MindfulLimitsEditMode(state, viewModel, onTuneApp = { appToTune = it }, onTuneGlobals = { tuningGlobals = true })
            } else {
                MindfulLimitsViewMode(state)
            }
        }
    }

    if (appToTune != null) {
        MindfulAppTuneSheet(
            app = appToTune!!,
            onDismiss = { appToTune = null },
            onConfirm = { session, daily, opens ->
                viewModel.updateAppLimits(appToTune!!.packageName, session, daily, opens)
                appToTune = null
            }
        )
    }

    if (tuningGlobals) {
        MindfulGlobalTuneSheet(
            state = state,
            onDismiss = { tuningGlobals = false },
            onConfirm = { category, session, daily, opens ->
                viewModel.updateGlobalLimits(category, session, daily, opens)
            },
            onConfirmBreathing = {
                viewModel.updateBreathingDuration(it)
            }
        )
    }
}

@Composable
private fun MindfulLimitsViewMode(state: MindfulLimitsUiState) {
    Column {
        fun format(value: Int?) = if (value == null) "No limit" else "${value}"
        
        Text("Central Configurations", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("• Distracting Apps Limit: ${format(state.distSession)}m | Daily: ${format(state.distDaily)}m | Opens: ${format(state.distOpens)}x", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        Text("• Productive Apps Limit: ${format(state.prodSession)}m | Daily: ${format(state.prodDaily)}m | Opens: ${format(state.prodOpens)}x", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        Text("• Neutral Apps Limit: ${format(state.neutSession)}m | Daily: ${format(state.neutDaily)}m | Opens: ${format(state.neutOpens)}x", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        Text("• Breathing Pause: ${state.breathingDuration}s per phase (${state.breathingDuration * 4}s total cycle)", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val customApps = state.apps.filter { it.sessionLimitMins != null || it.dailyLimitMins != null || it.dailyOpensLimit != null }
        Text("Custom App Limits (${customApps.size})", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        
        if (customApps.isEmpty()) {
            Text("No custom overrides set.", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = Color.Gray)
        } else {
            customApps.take(5).forEach { app ->
                val limitStr = "${format(app.sessionLimitMins)}m • ${format(app.dailyLimitMins)}m/day • ${format(app.dailyOpensLimit)}x"
                Text("• ${app.appName.ifEmpty { app.packageName }} ............ $limitStr", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = Color.Gray)
            }
            if (customApps.size > 5) {
                Text("...and ${customApps.size - 5} more", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = Color.Gray)
            }
        }
    }
}

@Composable
private fun MindfulLimitsEditMode(
    state: MindfulLimitsUiState,
    viewModel: MindfulLimitsViewModel,
    onTuneApp: (AppInfo) -> Unit,
    onTuneGlobals: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Central Configurations", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
            Button(
                onClick = onTuneGlobals,
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorSurface),
                shape = RoundedCornerShape(PulseAppCornerRadiusMedium)
            ) {
                Text("⚙️ Tune", color = PulseAppColorSecondary)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Per-App Overrides", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Distracting", "Productive", "Neutral").forEach { filter ->
                val isSelected = state.selectedFilter == filter
                Surface(
                    color = if (isSelected) PulseAppColorPrimary.copy(alpha=0.2f) else PulseAppColorSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { viewModel.setFilter(filter) }
                ) {
                    Text(
                        text = filter,
                        style = PulseAppFontBody.copy(fontSize = 12.sp),
                        color = if (isSelected) PulseAppColorPrimary else PulseAppColorSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        
        Column(modifier = Modifier.heightIn(max = 400.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.apps, key = { it.packageName }) { app ->
                    val isCustom = app.sessionLimitMins != null || app.dailyLimitMins != null || app.dailyOpensLimit != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PulseAppColorBackground.copy(alpha = 0.5f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx -> android.widget.ImageView(ctx) },
                            update = { view -> view.setImageDrawable(AppInfoHelper.getAppIcon(view.context, app.packageName)) },
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.appName, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color.White), maxLines = 1)
                            Text(app.category.lowercase().replaceFirstChar { it.uppercase() }, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Gray))
                        }
                        
                        Surface(
                            color = PulseAppColorSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onTuneApp(app) }
                        ) {
                            Text(
                                text = if (isCustom) "⚙️ Tune" else "Defaults",
                                style = PulseAppFontBody.copy(fontSize = 12.sp),
                                color = if (isCustom) PulseAppColorPrimary else PulseAppColorSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MindfulAppTuneSheet(
    app: AppInfo,
    onDismiss: () -> Unit,
    onConfirm: (session: Int?, daily: Int?, opens: Int?) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var session by remember { mutableStateOf(app.sessionLimitMins) }
    var daily by remember { mutableStateOf(app.dailyLimitMins) }
    var opens by remember { mutableStateOf(app.dailyOpensLimit) }
    
    var useCustom by remember { mutableStateOf(session != null || daily != null || opens != null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = PulseAppColorSurface
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${app.appName} Limits", style = PulseAppFontHeader, color = PulseAppColorPrimary)
            Divider(color = PulseAppColorBackground)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = useCustom,
                    onCheckedChange = { useCustom = it },
                    colors = CheckboxDefaults.colors(checkedColor = PulseAppColorPrimary)
                )
                Text("Enable Custom Limits (otherwise uses defaults)", style = PulseAppFontBody, color = PulseAppColorSecondary)
            }
            
            if (useCustom) {
                Text("Session Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                CounterBox(value = session, suffix = "mins", onValueChange = { session = it })
                
                Text("Daily Total Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                CounterBox(value = daily, suffix = "mins", onValueChange = { daily = it })
                
                Text("Daily Opens Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                CounterBox(value = opens, suffix = "times", onValueChange = { opens = it })
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (useCustom) onConfirm(session, daily, opens)
                    else onConfirm(null, null, null)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
            ) {
                Text("Confirm", color = PulseAppColorBackground)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MindfulGlobalTuneSheet(
    state: MindfulLimitsUiState,
    onDismiss: () -> Unit,
    onConfirm: (category: String, session: Int?, daily: Int?, opens: Int?) -> Unit,
    onConfirmBreathing: (duration: Int) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf("Distracting") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = PulseAppColorSurface
    ) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Central Defaults", style = PulseAppFontHeader, color = PulseAppColorPrimary)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Distracting", "Productive", "Neutral", "Breathing").forEach { tab ->
                    Text(
                        text = tab,
                        style = PulseAppFontBody.copy(fontSize = 12.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal),
                        color = if (selectedTab == tab) PulseAppColorPrimary else PulseAppColorSecondary,
                        modifier = Modifier.clickable { selectedTab = tab }.padding(4.dp)
                    )
                }
            }
            Divider(color = PulseAppColorBackground)
            
            if (selectedTab == "Breathing") {
                var breathing by remember { mutableStateOf(state.breathingDuration) }
                Text("Breathing Pause Duration (per phase)", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                CounterBox(value = breathing, suffix = "secs", onValueChange = { breathing = it ?: 4 }, allowNull = false)
                Button(onClick = { onConfirmBreathing(breathing); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)) { Text("Save Breathing", color = PulseAppColorBackground) }
            } else {
                var initialLoaded by remember { mutableStateOf(false) }
                var session by remember { mutableStateOf<Int?>(null) }
                var daily by remember { mutableStateOf<Int?>(null) }
                var opens by remember { mutableStateOf<Int?>(null) }
                
                LaunchedEffect(selectedTab) {
                    session = when (selectedTab) { "Productive" -> state.prodSession; "Neutral" -> state.neutSession; else -> state.distSession }
                    daily = when (selectedTab) { "Productive" -> state.prodDaily; "Neutral" -> state.neutDaily; else -> state.distDaily }
                    opens = when (selectedTab) { "Productive" -> state.prodOpens; "Neutral" -> state.neutOpens; else -> state.distOpens }
                    initialLoaded = true
                }
                
                if (initialLoaded) {
                    Text("Session Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                    CounterBox(value = session, suffix = "mins", onValueChange = { session = it })
                    Text("Daily Total Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                    CounterBox(value = daily, suffix = "mins", onValueChange = { daily = it })
                    Text("Daily Opens Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                    CounterBox(value = opens, suffix = "times", onValueChange = { opens = it })
                    
                    Button(onClick = { onConfirm(selectedTab, session, daily, opens); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)) { Text("Save Limits", color = PulseAppColorBackground) }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun CounterBox(
    value: Int?,
    suffix: String,
    onValueChange: (Int?) -> Unit,
    allowNull: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = PulseAppColorBackground, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(36.dp).clickable {
            val current = value ?: 0
            if (current > 0) onValueChange(current - 1)
            else if (allowNull) onValueChange(null)
        }) { Box(contentAlignment = Alignment.Center) { Text("-", style = PulseAppFontSubHeader, color = PulseAppColorPrimary) } }
        
        Surface(color = PulseAppColorBackground, shape = RoundedCornerShape(4.dp), modifier = Modifier.width(80.dp).height(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = if (value == null) "No limit" else value.toString(),
                    onValueChange = {
                        val num = it.toIntOrNull()
                        if (num != null) onValueChange(num)
                        else if (allowNull && it.isBlank()) onValueChange(null)
                    },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = PulseAppColorPrimary, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Surface(color = PulseAppColorBackground, shape = RoundedCornerShape(4.dp), modifier = Modifier.size(36.dp).clickable {
            val current = value ?: 0
            onValueChange(current + 1)
        }) { Box(contentAlignment = Alignment.Center) { Text("+", style = PulseAppFontSubHeader, color = PulseAppColorPrimary) } }
        
        Text(suffix, style = PulseAppFontBody, color = PulseAppColorSecondary)
    }
}
