package com.mylife.agent

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object RuleLogic {
    fun next(now: ZonedDateTime, hour:Int, minute:Int, days:String, date:String):ZonedDateTime? {
        val time=LocalTime.of(hour,minute)
        if(date.isNotBlank()) {
            val target=LocalDate.parse(date).atTime(time).atZone(now.zone)
            return target.takeIf { it>now }
        }
        return (0L..7L).asSequence().map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek.value.toString() in days }
            .map { it.atTime(time).atZone(now.zone) }.firstOrNull { it>now }
    }
    fun inRange(minute:Int,start:Int,end:Int):Boolean =
        if(start<=end) minute in start..end else minute>=start || minute<=end
}
