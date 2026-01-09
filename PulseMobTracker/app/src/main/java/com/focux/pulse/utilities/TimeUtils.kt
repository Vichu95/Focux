package com.focux.pulse.utilities

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun format(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }
}
