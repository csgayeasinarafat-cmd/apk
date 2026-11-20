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
        tile("Create automation","নতুন নিয়ম"){a.startActivity(Intent(a,AdvancedActivity::class.java).putExtra("create",true))}
        tile("Quran player","কুরআন প্লেয়ার"){open(PlayerActivity::class.java)}
        tile("Voice assistant","ভয়েস সহকারী"){open(VoiceActivity::class.java)}
        tile("Personal memory","ব্যক্তিগত তথ্য"){open(MemoryActivity::class.java)}
        tile("Contacts","পরিচিতি"){a.startActivity(Intent(a,MemoryActivity::class.java).putExtra("contacts",true))}
        tile("Execution history","কাজের ইতিহাস"){AlertDialog.Builder(a).setTitle(t("Recent activity","সাম্প্রতিক কাজ")).setMessage(AdvancedRules.history(a).joinToString("\n\n").ifBlank{t("No entries yet","এখনো কোনো কাজ হয়নি")}).setPositiveButton("OK",null).show()}
        tile("Permissions","অনুমতি"){
            val options=arrayOf("Notifications","Exact alarms","Notification event access","Bluetooth devices","Location while using app","Background location settings","App usage access")
            AlertDialog.Builder(a).setTitle(t("Permissions","অনুমতি")).setItems(options.map{a.uiLabel(it)}.toTypedArray()){_,i->
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

private val banglaUi=mapOf(
    "Rule name" to "নিয়মের নাম",
    "Description" to "বিবরণ",
    "Folder" to "গ্রুপ",
    "Phrase to say" to "যে কথাটি বলবেন",
    "Android package name" to "অ্যাপের প্যাকেজের নাম",
    "Bluetooth device name" to "ব্লুটুথ ডিভাইসের নাম",
    "Latitude, longitude, radius (metres)" to "অক্ষাংশ, দ্রাঘিমাংশ, ব্যাসার্ধ (মিটার)",
    "Scheduled time" to "নির্ধারিত সময়",
    "Specific date" to "নির্দিষ্ট তারিখ",
    "Manual" to "নিজে চালান",
    "Voice phrase" to "কণ্ঠ নির্দেশ",
    "Device restart" to "ফোন চালু হলে",
    "Charging connected" to "চার্জার যুক্ত হলে",
    "Charging disconnected" to "চার্জার খুললে",
    "Battery level changed" to "ব্যাটারির মাত্রা পরিবর্তন",
    "Network changed" to "নেটওয়ার্ক পরিবর্তন",
    "Wi-Fi changed" to "ওয়াই-ফাই পরিবর্তন",
    "App opened (periodic check)" to "অ্যাপ খুললে (পর্যায়ক্রমে পরীক্ষা)",
    "Location arrival" to "স্থানে পৌঁছালে",
    "Location departure" to "স্থান ছেড়ে গেলে",
    "Notification from package" to "নির্দিষ্ট অ্যাপের নোটিফিকেশন",
    "Bluetooth device connected" to "ব্লুটুথ ডিভাইস যুক্ত হলে",
    "Bluetooth device disconnected" to "ব্লুটুথ সংযোগ বিচ্ছিন্ন হলে",
    "Edit" to "সম্পাদনা",
    "Delete" to "মুছুন",
    "Enable" to "চালু করুন",
    "Disable" to "বন্ধ করুন",
    "Save rule" to "নিয়ম সংরক্ষণ",
    "Saved rules" to "সংরক্ষিত নিয়ম",
    "Run now (notifications)" to "এখনই চালান (নোটিফিকেশন)",
    "Open website" to "ওয়েবসাইট খুলুন",
    "Play audio URL" to "লিংক থেকে অডিও চালান",
    "Open Wi-Fi settings" to "ওয়াই-ফাই সেটিংস খুলুন",
    "Open dialer" to "ডায়ালার খুলুন",
    "Prepare WhatsApp" to "হোয়াটসঅ্যাপ বার্তা তৈরি",
    "Show reminder" to "রিমাইন্ডার দেখান",
    "Bluetooth settings" to "ব্লুটুথ সেটিংস",
    "Quran player" to "কুরআন প্লেয়ার",
    "Create clock alarm" to "ঘড়িতে অ্যালার্ম তৈরি",
    "Open application" to "অ্যাপ খুলুন",
    "Delay" to "বিরতি",
    "Run automation" to "অন্য নিয়ম চালান",
    "Pause audio" to "অডিও বিরতি",
    "Stop audio" to "অডিও বন্ধ করুন",
    "Speak text" to "লেখা পড়ে শোনান",
    "Charging" to "চার্জ হচ্ছে",
    "Wi-Fi connected" to "ওয়াই-ফাই সংযুক্ত",
    "Online" to "ইন্টারনেট সংযুক্ত",
    "Battery at least (%)" to "ব্যাটারি কমপক্ষে (%)",
    "Weekdays (1–7)" to "সপ্তাহের দিন (১–৭)",
    "Time range (HH:mm-HH:mm)" to "সময়সীমা (ঘণ্টা:মিনিট-ঘণ্টা:মিনিট)",
    "Clear conditions" to "সব শর্ত সরান",
    "Location lat,lon,radius (m)" to "অবস্থান ও ব্যাসার্ধ (মিটার)",
    "Named preference equals true" to "সংরক্ষিত পছন্দ সত্য হলে",
    "Add condition" to "শর্ত যোগ করুন",
    "Is true" to "সত্য",
    "Is false (NOT)" to "মিথ্যা (NOT)",
    "Condition" to "শর্ত",
    "Apply" to "প্রয়োগ",
    "Cancel" to "বাতিল",
    "Add" to "যোগ করুন",
    "Stop" to "বন্ধ করুন",
    "Pause" to "বিরতি",
    "Resume" to "আবার চালান",
    "Playback speed" to "চালানোর গতি",
    "Repeat until stopped" to "বন্ধ না করা পর্যন্ত পুনরাবৃত্তি",
    "Add a recording you have permission to use" to "ব্যবহারের অনুমতি আছে এমন রেকর্ডিং যোগ করুন",
    "Open Al-Baqarah in browser" to "ব্রাউজারে সূরা বাকারা খুলুন",
    "Surah name or number" to "সূরার নাম বা নম্বর",
    "Reciter" to "কারি",
    "Name / nickname" to "নাম বা ডাকনাম",
    "Value / phone / message" to "তথ্য, ফোন বা বার্তা",
    "Export memory and advanced rules" to "তথ্য ও নিয়মের ব্যাকআপ নিন",
    "Import backup" to "ব্যাকআপ ফিরিয়ে আনুন",
    "Close" to "বন্ধ করুন",
    "Weekdays" to "সপ্তাহের দিন",
    "Monday" to "সোমবার",
    "Tuesday" to "মঙ্গলবার",
    "Wednesday" to "বুধবার",
    "Thursday" to "বৃহস্পতিবার",
    "Friday" to "শুক্রবার",
    "Saturday" to "শনিবার",
    "Sunday" to "রবিবার",
    "Recent activity" to "সাম্প্রতিক কাজ",
    "Save schedule" to "সময়সূচি সংরক্ষণ",
    "Save and notify" to "সংরক্ষণ করে নোটিফিকেশন দিন",
    "Notifications" to "নোটিফিকেশন",
    "Exact alarms" to "নির্দিষ্ট সময়ের অ্যালার্ম",
    "Notification event access" to "নোটিফিকেশন পড়ার অনুমতি",
    "Bluetooth devices" to "ব্লুটুথ ডিভাইস",
    "Location while using app" to "অ্যাপ ব্যবহারের সময় অবস্থান",
    "Background location settings" to "ব্যাকগ্রাউন্ডে অবস্থানের অনুমতি",
    "App usage access" to "অ্যাপ ব্যবহারের তথ্য",
    "Rule saved" to "নিয়ম সংরক্ষিত হয়েছে",
    "Trigger → Conditions → Actions" to "কখন → কোন শর্তে → কী করবে",
    "Then…" to "তারপর…",
    "Actions appear in order. External apps open when you tap their notifications. Add a Delay to wait between steps." to "কাজগুলো ক্রমানুসারে আসে। নোটিফিকেশন চাপলে বাইরের অ্যাপ খুলবে। দুটি কাজের মাঝে অপেক্ষা করতে বিরতি যোগ করুন।"
)

internal fun android.content.Context.uiLabel(value:String):String =
    if(getSharedPreferences("appearance",0).getBoolean("bn",false)) banglaUi[value] ?: value else value
