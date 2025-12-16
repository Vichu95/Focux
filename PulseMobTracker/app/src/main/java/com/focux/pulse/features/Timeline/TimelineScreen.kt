package com.focux.pulse.features.Timeline

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focux.pulse.data.dummyTimelineEvents
import com.focux.pulse.features.Timeline.components.TimelineItem

@Composable
fun TimelineScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        itemsIndexed(dummyTimelineEvents) { index, event ->
            TimelineItem(
                event = event,
                isFirst = index == 0,
                isLast = index == dummyTimelineEvents.lastIndex
            )
        }
    }
}
