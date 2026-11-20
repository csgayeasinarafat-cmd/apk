package com.mylife.agent

import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class RuleLogicTest {
    private val friday = ZonedDateTime.parse("2026-11-20T07:00:00+06:00[Asia/Dhaka]")
    @Test fun banglaVoice(){assertEquals("23:00",RuleLogic.commandTime("রাত এগারোটায় আমাকে মনে করিয়ে দেবে")?.toString())}
    @Test fun englishVoice(){assertEquals("07:00",RuleLogic.commandTime("every morning at seven")?.toString())}
    @Test fun banglaDigits(){assertEquals("08:30",RuleLogic.commandTime("আগামীকাল সকাল ৮:৩০")?.toString())}
    @Test fun missingTimeNeedsClarification(){assertNull(RuleLogic.commandTime("প্রতিদিন সকালে"))}
    @Test fun ambiguousContactNeedsReview(){
        val result=OfflineCommandInterpreter.propose("call Anna",mapOf("Ann" to "123456789","Anna" to "987654321"),emptyMap())
        assertTrue(result is Proposal.Clarify)
    }
    @Test fun localAppAlias(){
        val result=OfflineCommandInterpreter.propose("open notes",emptyMap(),mapOf("notes" to "com.example.notes"))
        assertEquals("APP|com.example.notes",(result as Proposal.Ready).rule.actions)
    }
    @Test fun futureToday(){assertEquals(friday.plusMinutes(1), RuleLogic.next(friday,7,1,"5",""))}
    @Test fun exactMinuteMovesToNextWeek(){assertEquals(friday.plusWeeks(1), RuleLogic.next(friday,7,0,"5",""))}
    @Test fun specificDateRunsOnce(){assertNull(RuleLogic.next(friday,7,0,"1234567","2026-11-20"))}
    @Test fun futureDateIgnoresWeekdayFilter(){assertEquals(friday.plusDays(1),RuleLogic.next(friday,7,0,"1","2026-11-21"))}
    @Test fun overnightCondition(){assertTrue(RuleLogic.inRange(30,1380,60));assertFalse(RuleLogic.inRange(600,1380,60))}
    @Test fun daytimeIncludesEndpoints(){assertTrue(RuleLogic.inRange(120,120,180));assertTrue(RuleLogic.inRange(180,120,180))}
}
