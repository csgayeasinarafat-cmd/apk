package com.mylife.agent

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** A scheduled rule with optional weekday AND filters and OR groups of actions.
 *  Syntax: one action per line; ACTION|value. Supported: WEB, AUDIO, WIFI, DIAL, WHATSAPP, NOTE.
 *  Each line is an independent action; all are offered as separate notification buttons.
 *  No automatic external messages, browser autoplay, or silent device settings changes.
 */
data class AdvancedRule(val id:Int,val name:String,val hour:Int,val minute:Int,val days:String,
                        val actions:String,val enabled:Boolean=true) {
 fun toJson()=JSONObject().put("id",id).put("name",name).put("hour",hour).put("minute",minute)
  .put("days",days).put("actions",actions).put("enabled",enabled)
 companion object { fun parse(o:JSONObject)=AdvancedRule(o.getInt("id"),o.getString("name"),o.getInt("hour"),
  o.getInt("minute"),o.optString("days","1234567"),o.getString("actions"),o.optBoolean("enabled",true)) }
}
object AdvancedRules {
 private const val PREF="mylife_advanced_v12"
 fun load(c:Context):List<AdvancedRule> = try {val a=JSONArray(c.getSharedPreferences(PREF,0).getString("items","[]"));
  (0 until a.length()).map { AdvancedRule.parse(a.getJSONObject(it)) }}catch(_:Exception){emptyList()}
 fun save(c:Context,items:List<AdvancedRule>){val a=JSONArray();items.forEach{a.put(it.toJson())};
  c.getSharedPreferences(PREF,0).edit().putString("items",a.toString()).apply();reschedule(c)}
 fun remove(c:Context,id:Int) {
  val pending=pi(c,id)
  alarm(c).cancel(pending)
  pending.cancel()
  save(c,load(c).filterNot { it.id==id })
 }
 fun seed(c:Context){if(!c.getSharedPreferences(PREF,0).contains("items"))save(c,listOf(
  AdvancedRule(12001,"Morning Surah Al-Baqarah",7,0,"1234567",
   "WEB|https://quran.com/2\nAUDIO|https://download.quranicaudio.com/quran/mishaari_raashid_al_3afaasee/002.mp3",false))) }
 private fun alarm(c:Context)=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
 private fun pi(c:Context,id:Int)=PendingIntent.getBroadcast(c,id,Intent(c,AdvancedReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun reschedule(c:Context){load(c).forEach{schedule(c,it)}}
 fun schedule(c:Context,r:AdvancedRule){val a=alarm(c);val p=pi(c,r.id);a.cancel(p);if(!r.enabled)return
  val now=Calendar.getInstance();val next=Calendar.getInstance().apply {set(Calendar.HOUR_OF_DAY,r.hour);set(Calendar.MINUTE,r.minute);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
  for(i in 0..7){if(next.timeInMillis>now.timeInMillis && r.days.contains(((next.get(Calendar.DAY_OF_WEEK)+5)%7+1).toString()))break;next.add(Calendar.DAY_OF_YEAR,1)}
  scheduleWakeup(a,next.timeInMillis,p)
 }
 fun validActions(s:String):Boolean=s.lines().filter{it.isNotBlank()}.let{lines->lines.isNotEmpty() && lines.all{line->
  val v=line.split('|',limit=2);v.size==2 && v[0] in listOf("WEB","AUDIO","WIFI","DIAL","WHATSAPP","NOTE") &&
  when(v[0]) {"WEB","AUDIO"->v[1].startsWith("https://") && android.util.Patterns.WEB_URL.matcher(v[1]).matches();"DIAL","WHATSAPP"->v[1].filter{it.isDigit()}.length>=7;else->true}
 }}
 fun execute(c:Context,r:AdvancedRule){val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("advanced","MyLife automation rules",NotificationManager.IMPORTANCE_HIGH))
  r.actions.lines().filter{it.isNotBlank()}.forEachIndexed{index,line->
   val bits=line.split('|',limit=2);if(bits.size!=2)return@forEachIndexed
   val kind=bits[0];val value=bits[1];val notificationTag="advanced:${r.id}:$index"
   val target=when(kind){
    "WEB"->Intent(Intent.ACTION_VIEW,Uri.parse(value))
    "AUDIO"->Intent(c,PlayerActivity::class.java).putExtra("url",value)
    "WIFI"->Intent(Settings.ACTION_WIFI_SETTINGS)
    "DIAL"->Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+value.filter{it.isDigit()||it=='+'}))
    "WHATSAPP"->Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+value.filter{it.isDigit()}))
    else->Intent(c,MainActivity::class.java)
   }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   target.identifier=notificationTag
   val click=PendingIntent.getActivity(c,0,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   val body=when(kind){"WEB"->"Tap to open website; browser playback requires interaction";"AUDIO"->"Tap to open the audio player";"WIFI"->"Tap to change Wi-Fi manually";"DIAL"->"Tap to review phone number";"WHATSAPP"->"Tap to open WhatsApp; send manually";else->value}
   val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(c,"advanced") else Notification.Builder(c)
   val n=builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(r.name+" · "+kind)
    .setContentText(body).setStyle(Notification.BigTextStyle().bigText(body)).setContentIntent(click).setAutoCancel(true).build()
   try{nm.notify(notificationTag,0,n)}catch(_:SecurityException){}
  }
 }
}
class AdvancedReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){val id=i?.getIntExtra("id",-1)?:-1
 val r=AdvancedRules.load(c).firstOrNull{it.id==id && it.enabled}?:return;AdvancedRules.execute(c,r);AdvancedRules.schedule(c,r)}}
class AdvancedBootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){if(i?.action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))AdvancedRules.reschedule(c)}}
