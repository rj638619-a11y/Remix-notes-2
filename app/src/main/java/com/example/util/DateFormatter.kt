package com.example.util

import java.util.Calendar

object DateFormatter {
    private val MON = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun fmtItem(ts: Long): String {
        val dateCal = Calendar.getInstance().apply { timeInMillis = ts }
        val nowCal = Calendar.getInstance()

        val isSameDay = dateCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                dateCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

        if (isSameDay) {
            val h = dateCal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
            val m = dateCal.get(Calendar.MINUTE).toString().padStart(2, '0')
            return "$h:$m"
        }

        val month = MON[dateCal.get(Calendar.MONTH)]
        val day = dateCal.get(Calendar.DAY_OF_MONTH)
        return if (dateCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
            "$month $day"
        } else {
            "$month $day, ${dateCal.get(Calendar.YEAR)}"
        }
    }

    fun fmtClock(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val h = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val m = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        return "$h:$m"
    }

    fun groupOf(ts: Long): String {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = todayCal.timeInMillis
        val oneDay = 86_400_000L

        return when {
            ts >= todayStart -> "Today"
            ts >= todayStart - oneDay -> "Yesterday"
            ts >= todayStart - 7 * oneDay -> "Previous 7 Days"
            ts >= todayStart - 30 * oneDay -> "Previous 30 Days"
            else -> {
                val cal = Calendar.getInstance().apply { timeInMillis = ts }
                "${MON[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
            }
        }
    }

    fun fmtMS(ms: Long): String {
        val safeMs = maxOf(0L, ms)
        val totalSecs = (safeMs + 999L) / 1000L
        val m = totalSecs / 60
        val s = totalSecs % 60
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    }

    fun fmtSW(ms: Long): String {
        val safeMs = maxOf(0L, ms)
        val m = safeMs / 60000
        val s = (safeMs / 1000) % 60
        val d = (safeMs / 100) % 10
        return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}.$d"
    }
}
