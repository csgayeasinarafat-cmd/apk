package com.mylife.agent

import android.app.AlertDialog
import android.content.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.widget.*
import org.json.*
import java.time.LocalDateTime

object RuleConditions {
    val events=linkedMapOf("TIME" to "Scheduled time","DATE" to "Specific date","MANUAL" to "Manual / voice","BOOT" to "Device restart",
        "POWER_ON" to "Charging connected","POWER_OFF" to "Charging disconnected","BATTERY" to "Battery level changed",
        "NETWORK" to "Network changed","WIFI" to "Wi-Fi changed")
    fun matches(context:Context, expression:String):Boolean {
        if(expression.isBlank())return true
        val battery=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1
        val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,0)?:0
        val percent=if(level>=0 && scale>0) 100*level/scale else -1
        val cm=context.getSystemService(ConnectivityManager::class.java)
        val caps=cm.getNetworkCapabilities(cm.activeNetwork)
        val now=LocalDateTime.now()
        fun eval(node:JSONObject):Boolean {
            val kids=node.optJSONArray("children")?:JSONArray()
            return when(node.getString("op")){
                "AND"->(0 until kids.length()).all{eval(kids.getJSONObject(it))}
                "OR"->(0 until kids.length()).any{eval(kids.getJSONObject(it))}
                "NOT"->kids.length()==1 && !eval(kids.getJSONObject(0))
                "CHARGING"->(battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)?:0)>0
                "BATTERY"->percent>=node.getInt("value")
                "WIFI"->caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
                "ONLINE"->caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
                "DAYS"->now.dayOfWeek.value.toString() in node.getString("value")
                "TIME"->RuleLogic.inRange(now.hour*60+now.minute,node.getInt("from"),node.getInt("to"))
                else->false
            }
        }
        return eval(JSONObject(expression))
    }
    fun describe(s:String):String {
        if(s.isBlank())return "Always"
        fun render(n:JSONObject):String {
            val op=n.getString("op")
            val kids=n.optJSONArray("children")?:return when(op){
                "TIME"->"Time "+n.getInt("from")/60+":"+"%02d".format(n.getInt("from")%60)+"–"+n.getInt("to")/60+":"+"%02d".format(n.getInt("to")%60)
                "BATTERY"->"Battery ≥ "+n.getInt("value")+"%"
                "DAYS"->"Weekdays "+n.getString("value")
                else->op
            }
            return if(op=="NOT") "NOT ("+render(kids.getJSONObject(0))+")"
                else (0 until kids.length()).joinToString(" $op ","(",")"){render(kids.getJSONObject(it))}
        }
        return render(JSONObject(s))
    }
    fun edit(c:Context,old:String,done:(String)->Unit){
        val types=arrayOf("Charging","Wi-Fi connected","Online","Battery at least (%)","Weekdays (1–7)","Time range (HH:mm-HH:mm)","Clear conditions")
        fun join(new:JSONObject){
            fun append(part:JSONObject){
                if(old.isBlank()){done(part.toString());return}
                val ops=arrayOf("AND","OR")
                AlertDialog.Builder(c).setTitle(describe(old)).setItems(ops){_,i->
                    done(JSONObject().put("op",ops[i]).put("children",JSONArray().put(JSONObject(old)).put(part)).toString())
                }.show()
            }
            AlertDialog.Builder(c).setTitle("Condition").setItems(arrayOf("Is true","Is false (NOT)")){_,i->
                append(if(i==0)new else JSONObject().put("op","NOT").put("children",JSONArray().put(new)))
            }.show()
        }
        AlertDialog.Builder(c).setTitle("Add condition").setItems(types){_,i->
            if(i==6)done("") else if(i<3)join(JSONObject().put("op",arrayOf("CHARGING","WIFI","ONLINE")[i])) else {
                val input=EditText(c)
                AlertDialog.Builder(c).setTitle(types[i]).setView(input).setPositiveButton("Apply"){_,_->
                    val value=input.text.toString().trim()
                    val part=runCatching { when(i){
                        3->JSONObject().put("op","BATTERY").put("value",value.toInt().also{require(it in 0..100)})
                        4->JSONObject().put("op","DAYS").put("value",value.also{require(it.isNotBlank()&&it.all{ch->ch in '1'..'7'})})
                        else->{val t=value.split("-").map{java.time.LocalTime.parse(it)};require(t.size==2)
                            JSONObject().put("op","TIME").put("from",t[0].toSecondOfDay()/60).put("to",t[1].toSecondOfDay()/60)}
                    } }.getOrNull()
                    if(part==null)Toast.makeText(c,"Invalid condition",Toast.LENGTH_SHORT).show() else join(part)
                }.setNegativeButton("Cancel",null).show()
            }
        }.show()
    }
}
