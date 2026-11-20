package com.mylife.agent

import android.app.*
import android.content.*
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
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
                        val actions:String,val enabled:Boolean=true,val event:String="TIME",val date:String="",val condition:String="",val description:String="") {
 fun toJson()=JSONObject().put("id",id).put("name",name).put("hour",hour).put("minute",minute)
  .put("event",event).put("date",date).put("condition",condition).put("description",description).put("days",days).put("actions",actions).put("enabled",enabled)
 companion object { fun parse(o:JSONObject)=AdvancedRule(o.getInt("id"),o.getString("name"),o.getInt("hour"),
  o.getInt("minute"),o.optString("days","1234567"),o.getString("actions"),o.optBoolean("enabled",true),o.optString("event","TIME"),o.optString("date"),o.optString("condition"),o.optString("description")) }
}
object AdvancedRules {
 private const val PREF="mylife_advanced_v12"
 private class Store(context:Context):SQLiteOpenHelper(context,"automation.db",null,1) {
  private val prefs=context.getSharedPreferences(PREF,0)
  override fun onCreate(db:SQLiteDatabase) {
   db.execSQL("CREATE TABLE rules(id INTEGER PRIMARY KEY, data TEXT NOT NULL)")
   db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY, stamp INTEGER, name TEXT, detail TEXT)")
   val previous=prefs.getString("items",null)
   val initial=if(previous==null) listOf(AdvancedRule(12001,"Morning Surah Al-Baqarah",7,0,"1234567","WEB|https://quran.com/2",false))
    else JSONArray(previous).let { array -> (0 until array.length()).map { AdvancedRule.parse(array.getJSONObject(it)) } }
   initial.forEach { db.execSQL("INSERT INTO rules VALUES (?,?)",arrayOf(it.id,it.toJson().toString())) }
  }
  override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
 }
 @Volatile private var database:Store?=null
 private fun db(c:Context):SQLiteDatabase = (database ?: synchronized(this) {
  database ?: Store(c.applicationContext).also { database=it }
 }).writableDatabase
 fun load(c:Context):List<AdvancedRule> = db(c).rawQuery("SELECT data FROM rules ORDER BY id",null).use { cursor ->
  buildList { while(cursor.moveToNext()) add(AdvancedRule.parse(JSONObject(cursor.getString(0)))) }
 }
 fun save(c:Context,items:List<AdvancedRule>) {
  val storage=db(c)
  storage.beginTransaction()
  try {
   storage.delete("rules",null,null)
   items.forEach { storage.execSQL("INSERT INTO rules VALUES (?,?)",arrayOf(it.id,it.toJson().toString())) }
   storage.setTransactionSuccessful()
  } finally { storage.endTransaction() }
  reschedule(c)
 }
 fun history(c:Context):List<String> = db(c).rawQuery("SELECT stamp,name,detail FROM history ORDER BY id DESC LIMIT 100",null).use { cursor ->
  buildList { while(cursor.moveToNext()) add(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(cursor.getLong(0)))+" · "+cursor.getString(1)+" · "+cursor.getString(2)) }
 }
 private fun record(c:Context,r:AdvancedRule,detail:String) {
  val storage=db(c)
  storage.execSQL("INSERT INTO history(stamp,name,detail) VALUES (?,?,?)",arrayOf(System.currentTimeMillis(),r.name,detail))
  storage.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY id DESC LIMIT 100)")
 }
 fun remove(c:Context,id:Int) {
  val pending=pi(c,id)
  alarm(c).cancel(pending)
  pending.cancel()
  save(c,load(c).filterNot { it.id==id })
 }
 fun seed(c:Context){db(c)}
 private fun alarm(c:Context)=c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
 private fun pi(c:Context,id:Int)=PendingIntent.getBroadcast(c,id,Intent(c,AdvancedReceiver::class.java).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 fun reschedule(c:Context){load(c).forEach{schedule(c,it)}}
 fun schedule(c:Context,r:AdvancedRule){val a=alarm(c);val p=pi(c,r.id);a.cancel(p);if(!r.enabled)return
  if(r.event !in setOf("TIME","DATE"))return
  val next=RuleLogic.next(java.time.ZonedDateTime.now(),r.hour,r.minute,r.days,if(r.event=="DATE")r.date else "")?:return
  scheduleWakeup(a,next.toInstant().toEpochMilli(),p)
 }
 fun validActions(s:String):Boolean=s.lines().filter{it.isNotBlank()}.let{lines->lines.isNotEmpty() && lines.all{line->
  val v=line.split('|',limit=2);v.size==2 && v[0] in listOf("WEB","AUDIO","WIFI","DIAL","WHATSAPP","NOTE","BLUETOOTH","QURAN","ALARM") &&
  when(v[0]) {"WEB","AUDIO"->v[1].startsWith("https://") && android.util.Patterns.WEB_URL.matcher(v[1]).matches();"DIAL","WHATSAPP"->v[1].filter{it.isDigit()}.length>=7;else->true}
 }}
 fun fire(c:Context,event:String){load(c).filter{it.enabled && it.event==event}.forEach{execute(c,it)}}
 fun execute(c:Context,r:AdvancedRule){
  if(!RuleConditions.matches(c,r.condition)){record(c,r,"Skipped by condition");return}
  val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("advanced","MyLife automation rules",NotificationManager.IMPORTANCE_HIGH))
  if (!nm.areNotificationsEnabled() || nm.getNotificationChannel("advanced")?.importance==NotificationManager.IMPORTANCE_NONE) {
   record(c,r,"Notifications disabled");return
  }
  r.actions.lines().filter{it.isNotBlank()}.forEachIndexed{index,line->
   val bits=line.split('|',limit=2);if(bits.size!=2)return@forEachIndexed
   val kind=bits[0];val value=bits[1];val notificationTag="advanced:${r.id}:$index"
   val target=when(kind){
    "WEB"->Intent.createChooser(Intent(Intent.ACTION_VIEW,Uri.parse(value)),"Open website")
    "AUDIO"->Intent(c,PlayerActivity::class.java).putExtra("url",value)
    "BLUETOOTH"->Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    "QURAN"->Intent(c,PlayerActivity::class.java)
    "ALARM"->Intent(android.provider.AlarmClock.ACTION_SET_ALARM).putExtra(android.provider.AlarmClock.EXTRA_HOUR,r.hour).putExtra(android.provider.AlarmClock.EXTRA_MINUTES,r.minute)
    "WIFI"->Intent(Settings.ACTION_WIFI_SETTINGS)
    "DIAL"->Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+value.filter{it.isDigit()||it=='+'}))
    "WHATSAPP"->Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+value.substringBefore('~').filter{it.isDigit()}+"?text="+Uri.encode(value.substringAfter('~',""))))
    else->Intent(c,MainActivity::class.java)
   }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   target.identifier=notificationTag
   val click=PendingIntent.getActivity(c,0,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   val body=when(kind){"WEB"->"Tap to open website; browser playback requires interaction";"AUDIO"->"Tap to open the audio player";"WIFI"->"Tap to change Wi-Fi manually";"DIAL"->"Tap to review phone number";"WHATSAPP"->"Tap to open WhatsApp; send manually";else->value}
   val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(c,"advanced") else Notification.Builder(c)
   val n=builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(r.name+" · "+kind)
    .setContentText(body).setStyle(Notification.BigTextStyle().bigText(body)).setContentIntent(click).setAutoCancel(true).build()
   try{nm.notify(notificationTag,0,n);record(c,r,"$kind notification posted")}catch(_:SecurityException){record(c,r,"$kind permission denied")}
  }
 }
}
class AdvancedReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){val id=i?.getIntExtra("id",-1)?:-1
 val r=AdvancedRules.load(c).firstOrNull{it.id==id && it.enabled}?:return;AdvancedRules.execute(c,r);AdvancedRules.schedule(c,r)}}
class AdvancedBootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent?){if(i?.action in listOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)){AdvancedRules.reschedule(c);if(i?.action==Intent.ACTION_BOOT_COMPLETED)AdvancedRules.fire(c,"BOOT")}}}
