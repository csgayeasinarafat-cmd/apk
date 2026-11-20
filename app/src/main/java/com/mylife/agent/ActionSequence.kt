package com.mylife.agent

import android.app.*
import android.content.*
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.*
import org.json.JSONObject
import java.util.UUID

object ActionSequence {
    private fun prefs(c:Context)=c.getSharedPreferences("pending_actions",0)
    private fun pending(c:Context,key:String)=PendingIntent.getBroadcast(c,0,
        Intent(c,SequenceReceiver::class.java).setAction(key),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun defer(c:Context,r:AdvancedRule,index:Int,seconds:Long,path:Set<Int>){
        val key=UUID.randomUUID().toString()
        val due=System.currentTimeMillis()+seconds*1000L
        val state=JSONObject().put("rule",r.toJson()).put("step",index).put("due",due).put("path",path.joinToString(","))
        check(prefs(c).edit().putString(key,state.toString()).commit())
        scheduleWakeup(c.getSystemService(AlarmManager::class.java),due,pending(c,key))
    }
    @Synchronized fun restore(c:Context){prefs(c).all.forEach{(key,value)->
        val due=JSONObject(value as String).getLong("due")
        scheduleWakeup(c.getSystemService(AlarmManager::class.java),due,pending(c,key))
    }}
    @Synchronized fun deliver(c:Context,key:String){
        val json=prefs(c).getString(key,null)?:return
        val state=JSONObject(json);val stored=AdvancedRule.parse(state.getJSONObject("rule"))
        if(!prefs(c).edit().remove(key).commit())return
        val current=AdvancedRules.load(c).find{it==stored}?:return
        val path=state.getString("path").split(",").filter{it.isNotBlank()}.map{it.toInt()}.toSet()
        AdvancedRules.steps(c,current,state.getInt("step"),path)
    }
    @Synchronized fun cancelChanged(c:Context,rules:List<AdvancedRule>){
        val byId=rules.associateBy{it.id}
        prefs(c).all.forEach{(key,value)->
            val r=AdvancedRule.parse(JSONObject(value as String).getJSONObject("rule"))
            if(byId[r.id]!=r){c.getSystemService(AlarmManager::class.java).cancel(pending(c,key));prefs(c).edit().remove(key).apply()}
        }
    }
}
class SequenceReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){i.action?.let{ActionSequence.deliver(c,it)}}}
class SpeakActivity:Activity(),TextToSpeech.OnInitListener {
    private var engine:TextToSpeech?=null
    private lateinit var status:TextView
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        val page=LinearLayout(this).apply{orientation=1;setPadding(30,40,30,30)};setContentView(page)
        status=TextView(this).apply{text=intent.getStringExtra("text");textSize=20f};page.addView(status)
        page.addView(Button(this).apply{text="Stop";setOnClickListener{finish()}})
        engine=TextToSpeech(this,this)
    }
    override fun onInit(result:Int){if(result==TextToSpeech.SUCCESS)engine?.speak(intent.getStringExtra("text"),TextToSpeech.QUEUE_FLUSH,null,"rule") else status.text="Speech engine unavailable"}
    override fun onDestroy(){engine?.stop();engine?.shutdown();super.onDestroy()}
}
