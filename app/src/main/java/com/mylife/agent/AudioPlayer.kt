package com.mylife.agent

import android.app.*
import android.content.*
import android.media.*
import android.media.session.MediaSession
import android.net.Uri
import android.os.*
import android.widget.*
import org.json.JSONObject

private const val RECITATION = "Al-Baqarah · Aaqib Azeez"
private const val RECITATION_URL = "https://upload.wikimedia.org/wikipedia/commons/4/4f/Chapter_2%2C_Al-Baqarah_%28Murattal%29_-_Recitation_of_the_Holy_Qur%27an.mp3"
private const val PLAYER = "quran_player"
private const val CHANNEL = "quran_audio"
private const val NOTE_ID = 4401

class AudioService : Service() {
    private var player: MediaPlayer? = null
    private var ready = false
    private var label = ""
    private lateinit var session: MediaSession
    private lateinit var focus: AudioFocusRequest
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val prefs by lazy { getSharedPreferences(PLAYER, 0) }
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { val service get() = this@AudioService }
    override fun onBind(intent: Intent?) = binder
    fun duration() = if (ready) player?.duration ?: 0 else 0
    fun position() = if (ready) player?.currentPosition ?: 0 else 0
    fun seek(ms: Int) { if (ready) player?.seekTo(ms) }
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Quran playback", NotificationManager.IMPORTANCE_LOW))
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { if (it < 0) pause() }.build()
        session = MediaSession(this, "Quran").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = finishPlayback()
            })
        }
    }
    private fun control(action: String) = PendingIntent.getService(this, 0,
        Intent(this, AudioService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
    private fun notification(): Notification {
        val playing = ready && player?.isPlaying == true
        val open = PendingIntent.getActivity(this, 0, Intent(this, PlayerActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(label).setContentText(if (!ready) "Loading audio" else if (playing) "Playing" else "Paused")
            .setContentIntent(open).setOngoing(playing).setOnlyAlertOnce(true)
            .addAction(if(playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if(playing) "Pause" else "Resume", control(if(playing) "PAUSE" else "RESUME"))
            .addAction(android.R.drawable.ic_delete,"Stop",control("STOP"))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0,1)).build()
    }
    private fun refresh() { if (player != null) getSystemService(NotificationManager::class.java).notify(NOTE_ID,notification()) }
    fun pause() {
        if (ready && player?.isPlaying == true) player?.pause()
        refresh()
    }
    fun resume() {
        if (!ready || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        player?.start()
        configure()
        refresh()
    }
    fun configure() {
        if (!ready) return
        player?.isLooping = prefs.getBoolean("repeat", true)
        player?.let { if (it.isPlaying) runCatching { it.playbackParams = it.playbackParams.setSpeed(prefs.getFloat("speed",1f)) } }
    }
    fun finishPlayback() {
        ready=false
        player?.release()
        player=null
        audio.abandonAudioFocusRequest(focus)
        session.isActive=false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onStartCommand(intent: Intent?, flags:Int, startId:Int):Int {
        when(intent?.action) {
            "STOP" -> finishPlayback()
            "PAUSE" -> if(player==null)stopSelf() else pause()
            "RESUME" -> if(player==null)stopSelf() else resume()
            else -> {
                val uri=Uri.parse(intent?.getStringExtra("url").orEmpty())
                label=intent?.getStringExtra("title") ?: "Quran audio"
                ready=false
                player?.release()
                player=null
                startForeground(NOTE_ID,notification())
                if (uri.scheme !in setOf("content","https")) { finishPlayback(); return START_NOT_STICKY }
                val media=MediaPlayer()
                player=media
                try {
                    media.setAudioAttributes(attributes)
                    media.setOnErrorListener { _,_,_-> Toast.makeText(this,"Unable to play recording",Toast.LENGTH_LONG).show();finishPlayback();true }
                    media.setOnCompletionListener { finishPlayback() }
                    media.setOnPreparedListener { if (player===it) { ready=true;session.isActive=true;resume();refresh() } }
                    media.setDataSource(this,uri)
                    media.prepareAsync()
                } catch (_:Exception) { Toast.makeText(this,"Recording unavailable",Toast.LENGTH_LONG).show();finishPlayback() }
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { finishPlayback(); session.release();super.onDestroy() }
}

class PlayerActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(PLAYER,0) }
    private lateinit var track: Spinner
    private lateinit var surah: EditText
    private lateinit var reciter: EditText
    private lateinit var progress: SeekBar
    private lateinit var elapsed: TextView
    private var service:AudioService?=null
    private var bound=false
    private val handler=Handler(Looper.getMainLooper())
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder){service=(binder as AudioService.LocalBinder).service}
        override fun onServiceDisconnected(name:ComponentName){service=null}
    }
    private val tick=object:Runnable {
        override fun run(){
            val end=service?.duration()?:0
            val pos=service?.position()?:0
            if(!progress.isPressed){progress.max=end;progress.progress=pos}
            elapsed.text="%d:%02d / %d:%02d".format(pos/60000,pos/1000%60,end/60000,end/1000%60)
            handler.postDelayed(this,500)
        }
    }
    private fun library()=JSONObject(prefs.getString("recordings","{}") ?: "{}").apply { if(!has(RECITATION))put(RECITATION,RECITATION_URL) }
    private fun reload(){
        val names=library().keys().asSequence().toList().sorted()
        track.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,names)
        track.setSelection(names.indexOf(prefs.getString("selected","")).coerceAtLeast(0))
    }
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,32,28,24)}
        setContentView(ScrollView(this).apply{addView(root)})
        fun button(label:String, action:()->Unit){root.addView(Button(this).apply{text=label;setOnClickListener{action()}})}
        root.addView(TextView(this).apply{text="Quran player · কুরআন";textSize=26f})
        surah=EditText(this).apply{hint="Surah name or number"};root.addView(surah)
        reciter=EditText(this).apply{hint="Reciter"};root.addView(reciter)
        button("Add a recording you have permission to use"){
            if(surah.text.isBlank() || reciter.text.isBlank())return@button Toast.makeText(this,"Enter surah and reciter",Toast.LENGTH_SHORT).show()
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"),50)
        }
        root.addView(TextView(this).apply{text="Al-Baqarah · Aaqib Azeez (CC0, Wikimedia Commons). Play streams the recording. Imported files play locally."})
        track=Spinner(this);root.addView(track);reload()
        button("Play · চালান"){
            val selected=track.selectedItem?.toString().orEmpty()
            val explicit=intent.getStringExtra("url")
            val uri=explicit ?: library().optString(selected)
            if(uri.isBlank()){Toast.makeText(this,"Select a recording first",Toast.LENGTH_SHORT).show();return@button}
            prefs.edit().putString("selected",selected).apply()
            startForegroundService(Intent(this,AudioService::class.java).putExtra("url",uri).putExtra("title",if(explicit==null) selected else "Scheduled recording"))
        }
        button("Pause"){service?.pause()};button("Resume"){service?.resume()};button("Stop"){service?.finishPlayback()}
        root.addView(CheckBox(this).apply{text="Repeat until stopped";isChecked=prefs.getBoolean("repeat",true);setOnCheckedChangeListener{_,on->prefs.edit().putBoolean("repeat",on).apply();service?.configure()}})
        button("Playback speed"){val speeds=arrayOf("0.75","1.0","1.25","1.5","2.0")
            AlertDialog.Builder(this).setTitle("Playback speed").setItems(speeds){_,i->prefs.edit().putFloat("speed",speeds[i].toFloat()).apply();service?.configure()}.show()}
        elapsed=TextView(this);root.addView(elapsed);progress=SeekBar(this);root.addView(progress)
        progress.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,n:Int,user:Boolean){}
            override fun onStartTrackingTouch(s:SeekBar?){}
            override fun onStopTrackingTouch(s:SeekBar){service?.seek(s.progress)}
        })
        button("Open Al-Baqarah in browser"){startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW,Uri.parse("https://quran.com/2")),"Browser"))}
    }
    override fun onActivityResult(request:Int,result:Int,data:Intent?){
        super.onActivityResult(request,result,data)
        if(request!=50 || result!=RESULT_OK)return
        val uri=data?.data?:return
        try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch(_:SecurityException){Toast.makeText(this,"Choose a file with persistent access",Toast.LENGTH_LONG).show();return}
        val name=surah.text.toString().trim()+" · "+reciter.text.toString().trim()
        prefs.edit().putString("recordings",library().put(name,uri.toString()).toString()).putString("selected",name).apply()
        intent.removeExtra("url");reload()
    }
    override fun onStart(){super.onStart();bound=bindService(Intent(this,AudioService::class.java),connection,BIND_AUTO_CREATE);handler.post(tick)}
    override fun onStop(){handler.removeCallbacks(tick);if(bound)unbindService(connection);bound=false;service=null;super.onStop()}
}
