package com.mylife.agent

import android.util.Patterns
import org.json.JSONArray
import org.json.JSONObject

data class ActionSpec(val type:String,val value:String) {
    fun json()=JSONObject().put("type",type).put("value",value)
    fun editorLine()=type+"|"+value
}

object ActionCatalog {
    val labels=linkedMapOf("WEB" to "Open website","AUDIO" to "Play audio URL","WIFI" to "Open Wi-Fi settings",
        "DIAL" to "Open dialer","WHATSAPP" to "Prepare WhatsApp","NOTE" to "Show reminder",
        "BLUETOOTH" to "Bluetooth settings","QURAN" to "Quran player","ALARM" to "Create clock alarm",
        "APP" to "Open application","DELAY" to "Delay","RULE" to "Run automation",
        "PAUSE" to "Pause audio","STOP" to "Stop audio","SPEAK" to "Speak text")
    fun parse(text:String):List<ActionSpec> = text.lineSequence().filter{it.isNotBlank()}.map{
        val parts=it.split('|',limit=2)
        ActionSpec(parts[0].trim(),parts.getOrNull(1)?.trim().orEmpty())
    }.toList()
    fun decode(array:JSONArray):String=(0 until array.length()).joinToString("\n"){
        val row=array.getJSONObject(it)
        ActionSpec(row.getString("type"),row.getString("value")).editorLine()
    }
    fun valid(step:ActionSpec):Boolean {
        val value=step.value
        return when(step.type){
            "WEB","AUDIO"->value.startsWith("https://") && Patterns.WEB_URL.matcher(value).matches()
            "DIAL","WHATSAPP"->value.substringBefore('~').matches(Regex("[+]?[0-9 ()-]{7,25}")) && value.substringBefore('~').count{it in '0'..'9'}>=7
            "DELAY"->(value.toLongOrNull()?:0L) in 1L..86400L
            "RULE"->(value.toIntOrNull()?:0)>0
            "APP"->value.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(?:[.][a-zA-Z0-9_]+)+"))
            "NOTE","SPEAK"->value.isNotBlank()
            else->step.type in labels
        }
    }
}
