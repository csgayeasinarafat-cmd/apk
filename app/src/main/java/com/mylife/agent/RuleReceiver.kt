package com.mylife.agent
import android.app.*
import android.content.*
import android.os.Build
import android.provider.Settings
import java.util.Calendar

class RuleReceiver: BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent?) {
  if(!context.getSharedPreferences("MainActivity",0).getBoolean("wifi",false))return
  val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel("routines","Daily Routines",NotificationManager.IMPORTANCE_HIGH))
  val pi=PendingIntent.getActivity(context,0,Intent(Settings.ACTION_WIFI_SETTINGS),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=Notification.Builder(context,"routines").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("11 PM Wi-Fi routine").setContentText("Tap to open Wi-Fi settings and switch Wi-Fi off").setContentIntent(pi).setAutoCancel(true).build()
  try { manager.notify(2300,notification) }catch(_:SecurityException){}
  schedule(context)
 }
 companion object {
  private fun pending(context:Context)=PendingIntent.getBroadcast(context,2300,Intent(context,RuleReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  fun cancel(context:Context){(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))}
  fun schedule(context:Context){ val c=Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY,23);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0);if(timeInMillis<=System.currentTimeMillis())add(Calendar.DAY_OF_YEAR,1) }; val a=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;val p=pending(context)
   scheduleWakeup(a,c.timeInMillis,p)
  }
 }
}
class BootReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent?) {
  val triggers = setOf(
   Intent.ACTION_BOOT_COMPLETED,
   Intent.ACTION_TIME_CHANGED,
   Intent.ACTION_TIMEZONE_CHANGED,
   AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
  )
  if (intent?.action in triggers && context.getSharedPreferences("MainActivity", 0).getBoolean("wifi", false)) {
   RuleReceiver.schedule(context)
  }
 }
}

internal fun scheduleWakeup(alarm: AlarmManager, timeMillis: Long, operation: PendingIntent) {
 if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
  try {
   alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, operation)
   return
  } catch (_: SecurityException) {
   // Exact alarm access may change between the check and scheduling.
  }
 }
 alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, operation)
}
