package com.mylife.agent

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Editable daily rules, stored locally. No accessibility automation or silent messaging. */
data class AutomationRule(val id:Int,val title:String,val hour:Int,val minute:Int,val action:String,val value:String,val enabled:Boolean=true) {
 fun json()=JSONObject().put("id",id).put("title",title).put("hour",hour).put("minute",minute).put("action",action).put("value",value).put("enabled",enabled)
 companion object { fun from(o:JSONObject)=AutomationRule(o.getInt("id"),o.optString("title"),o.getInt("hour"),o.getInt("minute"),o.getString("action"),o.optString("value"),o.optBoolean("enabled",true)) }
}
object AutomationEngine {
 private const val PREF="mylife_rules"
 fun load(c:Context):MutableList<AutomationRule> = try { val arr=JSONArray(c.getSharedPreferences(PREF,0).getString("rules","[]"));(0 until arr.length()).map { AutomationRule.from(arr.getJSONObject(it)) }.toMutableList() }catch(_:Exception){mutableListOf()}
 fun save(c:Context,items:List<AutomationRule>){val a=JSONArray();items.forEach { a.put(it.json()) };c.getSharedPreferences(PREF,0).edit().putString("rules",a.toString()).apply();rescheduleAll(c,items)}
 fun seed(c:Context){if(!c.getSharedPreferences(PREF,0).contains("rules"))save(c,listOf(AutomationRule(7001,"Morning Surah Al-Baqarah",7,0,"WEB","https://quran.com/2",false)))}
 private fun alarm(c:Context)=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
 private fun pending(c:Context,id:Int)=PendingIntent.getBroadcast(c,id,Intent(c,AutomationReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun rescheduleAll(c:Context,items:List<AutomationRule> = load(c)){items.forEach { schedule(c,it) }}
 fun schedule(c:Context,r:AutomationRule){val a=alarm(c);val p=pending(c,r.id);a.cancel(p);if(!r.enabled)return
  val cal=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,r.hour);set(Calendar.MINUTE,r.minute);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);if(timeInMillis<=System.currentTimeMillis())add(Calendar.DAY_OF_YEAR,1) }
  scheduleWakeup(a,cal.timeInMillis,p)
 }
 fun remove(c:Context,id:Int){alarm(c).cancel(pending(c,id));save(c,load(c).filterNot { it.id==id })}
 fun execute(c:Context,r:AutomationRule){val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("mylife_automations","MyLife daily automations",NotificationManager.IMPORTANCE_HIGH))
  val target=when(r.action){"WEB"->Intent(Intent.ACTION_VIEW,Uri.parse(r.value));"WIFI"->Intent(Settings.ACTION_WIFI_SETTINGS);"DIAL"->Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+r.value.filter { it.isDigit()||it=='+' }));"WHATSAPP"->Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+r.value.filter { it.isDigit() }));else->Intent(c,MainActivity::class.java)}
  target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  val click=PendingIntent.getActivity(c,r.id,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val message=when(r.action){"WEB"->"Tap to open ${r.value}. Press Play on the site; browser autoplay is restricted.";"WIFI"->"Tap to change Wi-Fi settings";"DIAL"->"Tap to review the phone call";"WHATSAPP"->"Tap to open WhatsApp";"REMINDER"->r.value;else->"Tap to open MyLife AI"}
  val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(c,"mylife_automations") else Notification.Builder(c)
  val n=b.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(r.title).setContentText(message).setStyle(Notification.BigTextStyle().bigText(message)).setContentIntent(click).setAutoCancel(true).build()
  try{nm.notify(r.id,n)}catch(_:SecurityException){}
 }
}
class AutomationReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){val id=i?.getIntExtra("id",-1)?:-1;val r=AutomationEngine.load(c).firstOrNull { it.id==id && it.enabled }?:return;AutomationEngine.execute(c,r);AutomationEngine.schedule(c,r)}}
class AutomationBootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){if(i?.action==Intent.ACTION_BOOT_COMPLETED || i?.action==Intent.ACTION_TIME_CHANGED || i?.action==Intent.ACTION_TIMEZONE_CHANGED || i?.action==AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)AutomationEngine.rescheduleAll(c)}}
