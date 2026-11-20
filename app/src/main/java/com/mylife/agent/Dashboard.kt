package com.mylife.agent

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.widget.*

class Dashboard(private val a:Activity){
    private val prefs=a.getSharedPreferences("appearance",0)
    private val bn get()=prefs.getBoolean("bn",false)
    private fun t(en:String,ba:String)=if(bn)ba else en
    private val night get()=prefs.getBoolean("dark",false)
    fun show(){
        val page=LinearLayout(a).apply{orientation=1;setPadding(32,48,32,32);setBackgroundColor(if(night)Color.rgb(20,29,32) else Color.rgb(244,248,244))}
        a.setContentView(ScrollView(a).apply{addView(page)})
        val fg=if(night)Color.WHITE else Color.rgb(18,44,42)
        fun heading(s:String,size:Float){page.addView(TextView(a).apply{text=s;textSize=size;setTextColor(fg);setTypeface(null,Typeface.BOLD);setPadding(4,16,4,12)})}
        fun tile(en:String,ba:String,f:()->Unit){
            page.addView(Button(a).apply{text=t(en,ba);isAllCaps=false;gravity=android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START;setPadding(26,10,20,10);textSize=17f;setTextColor(fg)
                background=GradientDrawable().apply{cornerRadius=18f;setColor(if(night)Color.rgb(38,52,54) else Color.WHITE)}
                setOnClickListener{f()}
            },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=10})
        }
        fun open(cls:Class<*>){a.startActivity(Intent(a,cls))}
        heading("MyLife AI",32f)
        val rules=AdvancedRules.load(a)
        heading(t("Automations","অটোমেশন")+" · "+rules.count{it.enabled}+"/"+rules.size,18f)
        tile("Automation studio","নিয়ম তৈরি ও সম্পাদনা"){open(AdvancedActivity::class.java)}
        tile("Quran player","কুরআন প্লেয়ার"){open(PlayerActivity::class.java)}
        tile("Voice assistant","ভয়েস সহকারী"){open(VoiceActivity::class.java)}
        tile("Personal memory","ব্যক্তিগত তথ্য"){open(MemoryActivity::class.java)}
        tile("Contacts","পরিচিতি"){a.startActivity(Intent(a,MemoryActivity::class.java).putExtra("contacts",true))}
        tile("Execution history","কাজের ইতিহাস"){AlertDialog.Builder(a).setTitle(t("Recent activity","সাম্প্রতিক কাজ")).setMessage(AdvancedRules.history(a).joinToString("\n\n").ifBlank{t("No entries yet","এখনো কোনো কাজ হয়নি")}).setPositiveButton("OK",null).show()}
        tile("Permissions","অনুমতি"){
            val options=arrayOf("Notifications","Exact alarms","Notification event access","Bluetooth devices","Location while using app","Background location settings","App usage access")
            AlertDialog.Builder(a).setTitle(t("Permissions","অনুমতি")).setItems(options){_,i->
                runCatching { when(i){
                    0->if(Build.VERSION.SDK_INT>=33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)a.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"),70) else a.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.packageName))
                    1->if(Build.VERSION.SDK_INT>=31)a.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:"+a.packageName))) else Toast.makeText(a,"Exact alarms available",0).show()
                    2->a.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    3->if(Build.VERSION.SDK_INT>=31)a.requestPermissions(arrayOf("android.permission.BLUETOOTH_CONNECT"),71) else Toast.makeText(a,"Bluetooth permission available",0).show()
                    4->a.requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),72)
                    5->a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.packageName)))
                    6->a.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    else->Unit
                } }.onFailure{Toast.makeText(a,"Settings unavailable",0).show()}
            }.show()
        }
        tile("Settings","সেটিংস"){AlertDialog.Builder(a).setTitle(t("Settings","সেটিংস")).setItems(arrayOf("বাংলা / English","Dark / Light")){_,i->val key=if(i==0)"bn" else "dark";prefs.edit().putBoolean(key,!prefs.getBoolean(key,false)).apply();a.recreate()}.show()}
        tile("Legacy tools","পুরোনো টুল"){a.startActivity(Intent(a,MainActivity::class.java).putExtra("legacy",true))}
    }
}
