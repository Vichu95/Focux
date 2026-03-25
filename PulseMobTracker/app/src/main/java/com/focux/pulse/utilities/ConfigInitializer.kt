package com.focux.pulse.utilities

import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.SystemState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ConfigInitializer {
    suspend fun initializeDefaults(db: PulseDatabase) {
        withContext(Dispatchers.IO) {
            val dao = db.analyticsDao()
            val defaults = mapOf(
                // Distracting: 10min session, 60min daily, 50 opens
                "limit_distracting_session" to "10",
                "limit_distracting_daily" to "60",
                "limit_distracting_opens" to "50",
                // Productive & Neutral: no limits
                "limit_productive_session" to NO_LIMIT.toString(),
                "limit_productive_daily" to NO_LIMIT.toString(),
                "limit_productive_opens" to NO_LIMIT.toString(),
                "limit_neutral_session" to NO_LIMIT.toString(),
                "limit_neutral_daily" to NO_LIMIT.toString(),
                "limit_neutral_opens" to NO_LIMIT.toString(),
                // Doom scroll: relaxed preset (15 switches in 10s)
                "mindful_doomscroll_threshold" to "15",
                "mindful_doomscroll_window_secs" to "10",
                // Breathing: 2s pause, 2x penalty, 5min exemption (stored in minutes)
                "mindful_exemption_window_mins" to "5",
                "mindful_base_duration" to "2",
                "mindful_penalty_multiplier" to "2",
                // Master toggles
                "pulse_master_enabled" to "true",
                "pulse_app_limits_enabled" to "true",
                "pulse_doomscroll_enabled" to "true"
            )
            
            for ((key, value) in defaults) {
                if (dao.getState(key) == null) {
                    dao.updateState(SystemState(key, value))
                }
            }
        }
    }
}
