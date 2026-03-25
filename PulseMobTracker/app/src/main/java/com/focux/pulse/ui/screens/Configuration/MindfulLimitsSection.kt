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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
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

                // Single button: Edit in view mode, Close in edit mode
                Surface(
                    color = PulseAppColorSurface,
                    shape = RoundedCornerShape(PulseAppCornerRadiusMedium),
                    modifier = Modifier
                        .height(PulseAppTimelineFilterButtonHeight)
                        .clip(RoundedCornerShape(PulseAppCornerRadiusMedium))
                        .clickable { viewModel.toggleEditMode() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (state.isEditMode) {
                            Icon(painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel), contentDescription = "Close", tint = PulseAppColorSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Close", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = PulseAppColorSecondary))
                        } else {
                            Icon(painter = painterResource(id = android.R.drawable.ic_menu_edit), contentDescription = "Edit", tint = PulseAppColorPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = PulseAppColorSecondary))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            MasterTogglesCard(state = state, onUpdate = { m, a, d -> viewModel.updateMasterToggles(m, a, d) })
            Spacer(modifier = Modifier.height(16.dp))

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
            onConfirmMindful = { breathing, penalty, exemption, doomWindowSecs, doomThreshold ->
                viewModel.updateMindfulInterventions(breathing, penalty, exemption, doomWindowSecs, doomThreshold)
            }
        )
    }
}

@Composable
private fun MasterTogglesCard(state: MindfulLimitsUiState, onUpdate: (Boolean, Boolean, Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PulseAppColorSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Master Controls", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            
            val switchColors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = PulseAppColorBackground,
                checkedTrackColor = PulseAppColorPrimary,
                uncheckedThumbColor = PulseAppColorBackground,
                uncheckedTrackColor = PulseAppColorSecondary
            )
            
            // Master toggle — always interactive
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Enable Pulse Tracking", style = PulseAppFontBody, color = PulseAppColorSecondary)
                androidx.compose.material3.Switch(
                    checked = state.isMasterEnabled, 
                    onCheckedChange = { onUpdate(it, state.isAppLimitsEnabled, state.isDoomScrollEnabled) },
                    colors = switchColors
                )
            }
            
            // Dependent toggles — visually disabled when master is off
            val dependentAlpha = if (state.isMasterEnabled) 1f else 0.38f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp)
                    .graphicsLayer(alpha = dependentAlpha)
            ) {
                // Left accent bar to show these are children of master
                if (!state.isMasterEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                color = PulseAppColorSecondary.copy(alpha = 0.3f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Disabled — Enable Pulse Tracking first",
                            style = PulseAppFontBody.copy(fontSize = 11.sp),
                            color = PulseAppColorSecondary.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Enable App Limits (Reminders)", style = PulseAppFontBody, color = PulseAppColorSecondary)
                    androidx.compose.material3.Switch(
                        checked = state.isAppLimitsEnabled && state.isMasterEnabled,
                        enabled = state.isMasterEnabled,
                        onCheckedChange = { onUpdate(state.isMasterEnabled, it, state.isDoomScrollEnabled) },
                        colors = switchColors
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Enable Doom Scroll Detection", style = PulseAppFontBody, color = PulseAppColorSecondary)
                    androidx.compose.material3.Switch(
                        checked = state.isDoomScrollEnabled && state.isMasterEnabled,
                        enabled = state.isMasterEnabled,
                        onCheckedChange = { onUpdate(state.isMasterEnabled, state.isAppLimitsEnabled, it) },
                        colors = switchColors
                    )
                }
            }
        }
    }
}

@Composable
private fun MindfulLimitsViewMode(state: MindfulLimitsUiState) {
    Column {
        fun formatMin(value: Int?) = if (value == null || value == com.focux.pulse.utilities.NO_LIMIT) "-" else "${value}m"
        fun formatX(value: Int?) = if (value == null || value == com.focux.pulse.utilities.NO_LIMIT) "-" else "${value}x"
        
        Text("Central Configurations", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("• Distracting Apps Limit:\n  Session: ${formatMin(state.distSession)} | Daily: ${formatMin(state.distDaily)} | Opens: ${formatX(state.distOpens)}", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text("• Productive Apps Limit:\n  Session: ${formatMin(state.prodSession)} | Daily: ${formatMin(state.prodDaily)} | Opens: ${formatX(state.prodOpens)}", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text("• Neutral Apps Limit:\n  Session: ${formatMin(state.neutSession)} | Daily: ${formatMin(state.neutDaily)} | Opens: ${formatX(state.neutOpens)}", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text("• Mindful Interventions:\n  Pause: ${state.breathingDuration}s | Over-limit Penalty: ${state.penaltyMultiplier}x | Exemption: ${state.exemptionWindowSecs}s\n  Doom Scroll: ${state.doomScrollThreshold} switches in ${state.doomScrollWindowSecs}s", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
        
        Spacer(modifier = Modifier.height(12.dp))
        
        val customApps = state.apps.filter { it.sessionLimitMins != null || it.dailyLimitMins != null || it.dailyOpensLimit != null }
        Text("Custom App Limits (${customApps.size})", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        
        if (customApps.isEmpty()) {
            Text("No custom overrides set.", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = Color.Gray)
        } else {
            customApps.take(5).forEach { app ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("• ${app.appName.ifEmpty { app.packageName }}", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = Color.Gray)
                    Text("  Session: ${formatMin(app.sessionLimitMins)} | Daily: ${formatMin(app.dailyLimitMins)} | Opens: ${formatX(app.dailyOpensLimit)}", style = PulseAppFontBody.copy(fontSize = PulseAppFontSizeSmall), color = PulseAppColorSecondary)
                }
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
            Surface(
                color = PulseAppColorSurface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { onTuneGlobals() }
            ) {
                Text(
                    text = "⚙️ Tune",
                    style = PulseAppFontBody.copy(fontSize = 12.sp),
                    color = PulseAppColorSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Per-App Overrides", style = PulseAppFontSubHeader, color = PulseAppColorPrimary)
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Distracting", "Productive", "Neutral").forEach { filter ->
                val isSelected = filter in state.selectedFilters
                Surface(
                    color = if (isSelected) PulseAppColorPrimary else PulseAppColorSurface,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(50)).clickable { viewModel.toggleFilter(filter) }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            text = filter,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (isSelected) Color.White else Color.LightGray)
                        )
                    }
                }
            }
        }
        
        val focusRequester = remember { FocusRequester() }
        Surface(
            color = PulseAppColorSurface,
            shape = RoundedCornerShape(PulseAppCornerRadiusMedium),
            modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(PulseAppCornerRadiusMedium)).clickable { focusRequester.requestFocus() }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(painter = painterResource(id = android.R.drawable.ic_menu_search), contentDescription = "Search", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(PulseAppColorPrimary),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (state.searchQuery.isEmpty()) Text("Search by name...", color = Color.Gray, fontSize = 14.sp)
                        innerTextField()
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MindfulAppTuneSheet(
    app: AppInfo,
    onDismiss: () -> Unit,
    onConfirm: (session: Int, daily: Int, opens: Int) -> Unit
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var session by remember { mutableStateOf(app.sessionLimitMins.takeIf { it != com.focux.pulse.utilities.NO_LIMIT }) }
    var daily by remember { mutableStateOf(app.dailyLimitMins.takeIf { it != com.focux.pulse.utilities.NO_LIMIT }) }
    var opens by remember { mutableStateOf(app.dailyOpensLimit.takeIf { it != com.focux.pulse.utilities.NO_LIMIT }) }
    
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
                Text("Enable custom limits", style = PulseAppFontBody, color = PulseAppColorSecondary)
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PulseAppColorDistracting)
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = {
                        if (useCustom) onConfirm(
                            session ?: com.focux.pulse.utilities.NO_LIMIT, 
                            daily ?: com.focux.pulse.utilities.NO_LIMIT, 
                            opens ?: com.focux.pulse.utilities.NO_LIMIT
                        )
                        else onConfirm(
                            com.focux.pulse.utilities.NO_LIMIT, 
                            com.focux.pulse.utilities.NO_LIMIT, 
                            com.focux.pulse.utilities.NO_LIMIT
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)
                ) {
                    Text("Save", color = PulseAppColorBackground)
                }
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
    onConfirm: (category: String, session: Int, daily: Int, opens: Int) -> Unit,
    onConfirmMindful: (breathing: Int, penalty: Int, exemption: Int, doomWindowSecs: Int, doomThreshold: Int) -> Unit
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
                listOf("Distracting", "Productive", "Neutral", "Mindful Interventions").forEach { tab ->
                    Text(
                        text = tab,
                        style = PulseAppFontBody.copy(fontSize = 10.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal),
                        color = if (selectedTab == tab) PulseAppColorPrimary else PulseAppColorSecondary,
                        modifier = Modifier.clickable { selectedTab = tab }.padding(4.dp)
                    )
                }
            }
            Divider(color = PulseAppColorBackground)
            
            if (selectedTab == "Mindful Interventions") {
                var breathing by remember { mutableStateOf(state.breathingDuration) }
                var penalty by remember { mutableStateOf(state.penaltyMultiplier) }
                var exemption by remember { mutableStateOf(state.exemptionWindowSecs) }
                var windowSecs by remember { mutableStateOf(state.doomScrollWindowSecs) }
                var threshold by remember { mutableStateOf(state.doomScrollThreshold) }
                
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    item {
                        Text("Breathing Limits", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                        Text("Base Pause Duration", style = PulseAppFontBody.copy(fontSize = 12.sp), color = Color.Gray)
                        CounterBox(value = breathing, suffix = "secs", onValueChange = { breathing = it ?: 0 }, allowNull = false)
                        
                        Text("Over-Limit Penalty Multiplier", style = PulseAppFontBody.copy(fontSize = 12.sp), color = Color.Gray)
                        CounterBox(value = penalty, suffix = "x", onValueChange = { penalty = it ?: 0 }, allowNull = false)
                        
                        Text("Post-Breathing Exemption", style = PulseAppFontBody.copy(fontSize = 12.sp), color = Color.Gray)
                        CounterBox(value = exemption, suffix = "secs", onValueChange = { exemption = it ?: 0 }, allowNull = false)
                        
                        Divider(color = PulseAppColorBackground, modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text("Doom Scroll Detection", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                        Text("Detection Time Window", style = PulseAppFontBody.copy(fontSize = 12.sp), color = Color.Gray)
                        CounterBox(value = windowSecs, suffix = "secs", onValueChange = { windowSecs = it ?: 0 }, allowNull = false)
                        
                        Text("Rapid Switch Threshold", style = PulseAppFontBody.copy(fontSize = 12.sp), color = Color.Gray)
                        CounterBox(value = threshold, suffix = "switches", onValueChange = { threshold = it ?: 0 }, allowNull = false)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = PulseAppColorDistracting)) { Text("Cancel") }
                    Button(onClick = { onConfirmMindful(breathing, penalty, exemption, windowSecs, threshold); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)) { Text("Save Interventions", color = PulseAppColorBackground) }
                }
            } else {
                var initialLoaded by remember { mutableStateOf(false) }
                var session by remember { mutableStateOf<Int?>(null) }
                var daily by remember { mutableStateOf<Int?>(null) }
                var opens by remember { mutableStateOf<Int?>(null) }
                
                LaunchedEffect(selectedTab) {
                    session = when (selectedTab) { "Productive" -> state.prodSession; "Neutral" -> state.neutSession; else -> state.distSession }.takeIf { it != com.focux.pulse.utilities.NO_LIMIT }
                    daily = when (selectedTab) { "Productive" -> state.prodDaily; "Neutral" -> state.neutDaily; else -> state.distDaily }.takeIf { it != com.focux.pulse.utilities.NO_LIMIT }
                    opens = when (selectedTab) { "Productive" -> state.prodOpens; "Neutral" -> state.neutOpens; else -> state.distOpens }.takeIf { it != com.focux.pulse.utilities.NO_LIMIT }
                    initialLoaded = true
                }
                
                if (initialLoaded) {
                    Text("Session Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                    CounterBox(value = session, suffix = "mins", onValueChange = { session = it })
                    Text("Daily Total Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                    CounterBox(value = daily, suffix = "mins", onValueChange = { daily = it })
                    Text("Daily Opens Limit", style = PulseAppFontSubHeader, color = PulseAppColorSecondary)
                    CounterBox(value = opens, suffix = "times", onValueChange = { opens = it })
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = PulseAppColorDistracting)) { Text("Cancel") }
                        Button(onClick = { 
                            onConfirm(
                                selectedTab, 
                                session ?: com.focux.pulse.utilities.NO_LIMIT, 
                                daily ?: com.focux.pulse.utilities.NO_LIMIT, 
                                opens ?: com.focux.pulse.utilities.NO_LIMIT
                            )
                            onDismiss() 
                        }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PulseAppColorPrimary)) { Text("Save Limits", color = PulseAppColorBackground) }
                    }
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
        }) { Box(contentAlignment = Alignment.Center) { Text("-", style = PulseAppFontSubHeader, color = PulseAppColorPrimary) } }
        
        Surface(color = PulseAppColorBackground, shape = RoundedCornerShape(4.dp), modifier = Modifier.width(60.dp).height(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = value?.toString() ?: "",
                    onValueChange = {
                        val num = it.toIntOrNull()
                        if (num != null) onValueChange(num)
                        else if (it.isBlank()) onValueChange(0) // Require explicit checkbox to set to null
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
        
        if (allowNull) {
            Spacer(modifier = Modifier.weight(1f))
            Checkbox(
                checked = value == null,
                onCheckedChange = { isChecked -> 
                    if (isChecked) onValueChange(null) else onValueChange(0) 
                },
                colors = CheckboxDefaults.colors(checkedColor = PulseAppColorPrimary)
            )
            Text("No limit", style = PulseAppFontBody.copy(fontSize = 12.sp), color = PulseAppColorSecondary)
        }
    }
}
