package com.mylife.agent

import android.Manifest
import android.app.*
import android.app.job.*
import android.app.usage.*
import android.content.*
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import org.json.JSONObject

object OptionalEvents {
    private const val JOB=9031
    fun hasLocation(c:Context)=c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    fun area(spec:String):Triple<Double,Double,Float>{
        val parts=spec.split(",").map{it.trim().toDouble()}
        require(parts.size==3 && parts[0] in -90.0..90.0 && parts[1] in -180.0..180.0 && parts[2] in 50.0..100000.0)
        return Triple(parts[0],parts[1],parts[2].toFloat())
    }
    private fun token(c:Context,id:Int,flag:Int):PendingIntent? {
        val mutable=if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(c,id,Intent(c,AreaReceiver::class.java).setAction("com.mylife.agent.AREA").putExtra("id",id),flag or mutable)
    }
    fun cancel(c:Context,id:Int){
        val pi=token(c,id,PendingIntent.FLAG_NO_CREATE)?:return
        runCatching{c.getSystemService(LocationManager::class.java).removeProximityAlert(pi)};pi.cancel()
    }
    fun watch(c:Context,r:AdvancedRule):Boolean {
        if(!hasLocation(c) || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)!=0)return false
        return runCatching {
            val (lat,lon,radius)=area(r.date)
            c.getSystemService(LocationManager::class.java).addProximityAlert(lat,lon,radius,-1L,requireNotNull(token(c,r.id,PendingIntent.FLAG_UPDATE_CURRENT)))
        }.isSuccess
    }
    fun inside(c:Context,spec:String):Boolean {
        require(hasLocation(c))
        val manager=c.getSystemService(LocationManager::class.java)
        val last=manager.getProviders(true).mapNotNull{runCatching{manager.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull{it.elapsedRealtimeNanos}
        require(last!=null && SystemClock.elapsedRealtimeNanos()-last.elapsedRealtimeNanos<300_000_000_000L)
        val (lat,lon,radius)=area(spec);val result=FloatArray(1)
        Location.distanceBetween(lat,lon,last.latitude,last.longitude,result)
        return result[0]<=radius
    }
    fun monitorApps(c:Context,rules:List<AdvancedRule>){
        val job=c.getSystemService(JobScheduler::class.java)
        if(rules.none{it.enabled && it.event=="APP_OPEN"}){job.cancel(JOB);return}
        if(job.getPendingJob(JOB)!=null)return
        c.getSharedPreferences("app_events",0).edit().putLong("cursor",System.currentTimeMillis()).apply()
        job.schedule(JobInfo.Builder(JOB,ComponentName(c,AppEventsJob::class.java)).setPeriodic(15*60*1000L).setPersisted(true).build())
    }
}
class AreaReceiver:BroadcastReceiver(){
    override fun onReceive(c:Context,i:Intent){
        val event=if(i.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING,false))"ARRIVE" else "DEPART"
        AdvancedRules.load(c).find{it.id==i.getIntExtra("id",-1) && it.event==event && it.enabled}?.let{AdvancedRules.execute(c,it)}
    }
}
class AppEventsJob:JobService(){
    override fun onStartJob(params:JobParameters):Boolean {
        val prefs=getSharedPreferences("app_events",0);val now=System.currentTimeMillis()
        val from=prefs.getLong("cursor",now).coerceIn(now-24*60*60*1000,now)
        val events=getSystemService(UsageStatsManager::class.java).queryEvents(from,now)?:return false
        val rules=AdvancedRules.load(this).filter{it.event=="APP_OPEN" && it.enabled}
        val e=UsageEvents.Event()
        while(events.hasNextEvent()){events.getNextEvent(e)
            if(e.eventType==UsageEvents.Event.ACTIVITY_RESUMED)rules.filter{it.date==e.packageName}.forEach{AdvancedRules.execute(this,it)}
        }
        prefs.edit().putLong("cursor",now).apply()
        return false
    }
    override fun onStopJob(params:JobParameters)=true
}
