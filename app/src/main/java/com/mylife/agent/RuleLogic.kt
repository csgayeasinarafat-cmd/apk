package com.mylife.agent

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object RuleLogic {
    /** Resolve an explicit command time. A null result requires clarification for scheduled commands. */
    fun commandTime(raw:String):LocalTime? {
        val text=raw.lowercase(java.util.Locale.ROOT).map {
            if(it in '০'..'৯') '0'+(it-'০') else it
        }.joinToString("")
        val digits=Regex("(?<![0-9])([0-9]{1,2})(?::([0-9]{2}))?(?![0-9])").find(text)
        val english=listOf("one","two","three","four","five","six","seven","eight","nine","ten","eleven","twelve")
        val bengali=mapOf("বারো" to 12,"এগারো" to 11,"দশ" to 10,"নয়" to 9,"নয়" to 9,"আট" to 8,"সাত" to 7,"ছয়" to 6,"ছয়" to 6,"পাঁচ" to 5,"চার" to 4,"তিন" to 3,"দুই" to 2,"এক" to 1)
        var h=digits?.groupValues?.get(1)?.toIntOrNull()
            ?: bengali.entries.find{(word,_)->text.contains(word+"টা") || text.contains(word+" টা")}?.value
            ?: english.indexOfFirst{Regex("\\b"+it+"\\b").containsMatchIn(text)}.takeIf{it>=0}?.plus(1)
            ?: return null
        val m=digits?.groupValues?.get(2)?.toIntOrNull()?:0
        val pm=Regex("(?<![a-z])pm(?![a-z])").containsMatchIn(text)||listOf("রাত","বিকাল","বিকেল","evening","night").any{text.contains(it)}
        val am=Regex("(?<![a-z])am(?![a-z])").containsMatchIn(text)||text.contains("সকাল")||text.contains("morning")
        if((am&&pm) || ((am||pm)&&h !in 1..12) || h !in 0..23 || m !in 0..59)return null
        if(pm && h<12)h+=12
        if(am && h==12)h=0
        return LocalTime.of(h,m)
    }
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
