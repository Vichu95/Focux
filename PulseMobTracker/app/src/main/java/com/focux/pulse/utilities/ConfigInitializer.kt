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
                "limit_distracting_session" to "5",
                "limit_distracting_daily" to "30",
                "limit_distracting_opens" to "10",
                "limit_productive_session" to NO_LIMIT.toString(),
                "limit_productive_daily" to NO_LIMIT.toString(),
                "limit_productive_opens" to NO_LIMIT.toString(),
                "limit_neutral_session" to NO_LIMIT.toString(),
                "limit_neutral_daily" to NO_LIMIT.toString(),
                "limit_neutral_opens" to NO_LIMIT.toString(),
                "mindful_doomscroll_threshold" to "8",
                "mindful_doomscroll_window_secs" to "60",
                "mindful_exemption_window_secs" to "10",
                "mindful_base_duration" to "4",
                "mindful_penalty_multiplier" to "3",
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
