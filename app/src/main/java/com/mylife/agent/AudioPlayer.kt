package com.mylife.agent

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.*
import android.widget.*

/** Foreground audio only starts after a user taps a notification or a button. */
class AudioService:Service(){private var player:MediaPlayer?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action=="STOP"){stopSelf();return START_NOT_STICKY}
  val url=intent?.getStringExtra("url")?:return START_NOT_STICKY
  if(!url.startsWith("https://")){stopSelf();return START_NOT_STICKY}
  val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("quran_audio","MyLife Quran audio",NotificationManager.IMPORTANCE_LOW))
  val stop=PendingIntent.getService(this,1,Intent(this,AudioService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,"quran_audio") else Notification.Builder(this)
  val note=builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("MyLife AI · Quran Audio")
   .setContentText("Playing audio · Tap Stop to finish").addAction(android.R.drawable.ic_media_pause,"Stop",stop).setOngoing(true).build()
  startForeground(4401,note)
  player?.release();player=MediaPlayer().apply{
   setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
   setOnErrorListener{_,_,_->stopSelf();true}
   setOnCompletionListener{stopSelf()}
   try{setDataSource(url);prepareAsync();setOnPreparedListener{it.start()}}catch(_:Exception){stopSelf()}
  }
  return START_NOT_STICKY
 }
 override fun onDestroy(){player?.run{try{stop()}catch(_:Exception){};release()};player=null;super.onDestroy()}
}
class PlayerActivity:Activity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
 val url=intent.getStringExtra("url").orEmpty();val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,48,32,32)}
 layout.addView(TextView(this).apply{text="Quran Audio Player\nAudio starts only when you tap Play. Playback stops when the track ends or you tap Stop.";textSize=18f})
 layout.addView(Button(this).apply{text="Play audio";setOnClickListener{
  if(!url.startsWith("https://")){Toast.makeText(this@PlayerActivity,"Invalid audio URL",Toast.LENGTH_SHORT).show();return@setOnClickListener}
  val i=Intent(this@PlayerActivity,AudioService::class.java).putExtra("url",url)
  if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
 }})
 layout.addView(Button(this).apply{text="Stop audio";setOnClickListener{stopService(Intent(this@PlayerActivity,AudioService::class.java))}})
 layout.addView(Button(this).apply{text="Open Surah Al-Baqarah in browser";setOnClickListener{startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://quran.com/2")))}})
 setContentView(layout)
}}
