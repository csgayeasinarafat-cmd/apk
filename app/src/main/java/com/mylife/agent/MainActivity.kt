package com.mylife.agent

import android.app.*
import android.os.*
import android.content.*
import android.provider.AlarmClock
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.*
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
 private lateinit var status: TextView
 private lateinit var contact: EditText
 private lateinit var command: EditText
 private val speechCode=11
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(Color.rgb(245,248,255)) }
  val scroll=ScrollView(this); scroll.addView(root); setContentView(scroll)
  fun heading(s:String,size:Float) { root.addView(TextView(this).apply { text=s; textSize=size; setTextColor(Color.rgb(20,51,110)); setPadding(0,12,0,12) }) }
  fun btn(s:String,action:()->Unit) { root.addView(Button(this).apply { text=s; setOnClickListener { action() } }) }
  heading("MyLife AI",30f);heading("Personal Automation Agent · Huawei Y8p",14f)
  status=TextView(this).apply { text="Ready. Configure a trusted contact and create your rules."; textSize=15f };root.addView(status)
  heading("Trusted contact phone (country code, e.g. +880...)",16f)
  contact=EditText(this).apply { hint="+8801XXXXXXXXX"; setSingleLine(true); setText(getPreferences(0).getString("contact","")) };root.addView(contact)
  btn("Save trusted contact") { getPreferences(0).edit().putString("contact",contact.text.toString().trim()).apply();status.text="Contact saved locally" }
  heading("Command Center",20f)
  command=EditText(this).apply { hint="e.g. মাকে কল দাও / good morning / wifi" };root.addView(command)
  btn("Run command") { runCommand(command.text.toString()) }
  btn("🎙 Speak Bangla / English") { try { startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"bn-BD") },speechCode) } catch(e:Exception){status.text="Speech recognition unavailable. Type a command instead."} }
  AutomationEngine.seed(this)
  AutomationEngine.rescheduleAll(this)
  AdvancedRules.reschedule(this)
  if (getPreferences(0).getBoolean("wifi",false)) RuleReceiver.schedule(this)
  heading("Advanced IF / THEN Rule Builder",20f)
  btn("Open Advanced Rule Builder v1.2") { startActivity(Intent(this,AdvancedActivity::class.java)) }
  heading("Custom Daily Automations (legacy)",20f)
  customRulesUI(root)
  heading("Automations",20f)
  btn("Set 7:00 AM daily alarm in Clock") { try { startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_HOUR,7);putExtra(AlarmClock.EXTRA_MINUTES,0);putExtra(AlarmClock.EXTRA_MESSAGE,"MyLife AI Morning Alarm");putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS,ArrayList((Calendar.SUNDAY..Calendar.SATURDAY).toList())) });status.text="Confirm alarm in Clock app" }catch(e:Exception){status.text="No compatible Clock app found"} }
  btn("Enable 11:00 PM Wi-Fi reminder") { getPreferences(0).edit().putBoolean("wifi",true).apply();RuleReceiver.schedule(this);status.text="Daily 11 PM Wi-Fi reminder enabled" }
  btn("Disable 11:00 PM Wi-Fi reminder") { getPreferences(0).edit().putBoolean("wifi",false).apply();RuleReceiver.cancel(this);status.text="Wi-Fi reminder disabled" }
  btn("Open Wi-Fi Settings") { openExternal(Intent(Settings.ACTION_WIFI_SETTINGS)) }
  btn("Dial trusted contact") { dial() }
  btn("Prepare Good Morning WhatsApp") { whatsapp() }
  heading("Privacy: rules and phone number stay on this device. Messages require your confirmation.",13f)
  if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"),10)
 }
 private fun customRulesUI(root:LinearLayout) {
  val title=EditText(this).apply { hint="Rule name (e.g. Morning Quran)" };root.addView(title)
  val time=EditText(this).apply { hint="Time 24h HH:mm (e.g. 07:00)";setSingleLine(true);inputType=android.text.InputType.TYPE_CLASS_DATETIME };root.addView(time)
  val actions=listOf("WEB","WIFI","DIAL","WHATSAPP","REMINDER")
  val spinner=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,actions) };root.addView(spinner)
  val value=EditText(this).apply { hint="URL (https://quran.com/2), phone number, or reminder text" };root.addView(value)
  val rules=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };root.addView(rules)
  fun clearEditor(){editingId=null;title.text.clear();time.text.clear();value.text.clear()}
  fun refresh(){rules.removeAllViews();AutomationEngine.load(this).forEach { r->
   val line=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
   line.addView(TextView(this).apply { text="${r.title} • %02d:%02d • ${r.action} • ${if(r.enabled) "ON" else "OFF"}".format(r.hour,r.minute);textSize=15f })
   line.addView(Button(this).apply { text="Edit";setOnClickListener { title.setText(r.title);time.setText("%02d:%02d".format(r.hour,r.minute));spinner.setSelection(actions.indexOf(r.action).coerceAtLeast(0));value.setText(r.value);editingId=r.id;status.text="Editing ${r.title}. Tap Save Rule." } })
   line.addView(Button(this).apply { text=if(r.enabled)"Disable" else "Enable";setOnClickListener { AutomationEngine.save(this@MainActivity,AutomationEngine.load(this@MainActivity).map { if(it.id==r.id)it.copy(enabled=!it.enabled)else it });refresh() } })
   line.addView(Button(this).apply { text="Run now (show notification)";setOnClickListener { AutomationEngine.execute(this@MainActivity,r) } })
   line.addView(Button(this).apply { text="Delete";setOnClickListener { AutomationEngine.remove(this@MainActivity,r.id);if(editingId==r.id)clearEditor();refresh() } })
   rules.addView(line)
  } }
  root.addView(Button(this).apply { text="Save Rule";setOnClickListener {
   val parts=time.text.toString().trim().split(":");val h=parts.getOrNull(0)?.toIntOrNull();val m=parts.getOrNull(1)?.toIntOrNull()
   val a=spinner.selectedItem.toString();val v=value.text.toString().trim()
   if(parts.size!=2||h==null||m==null||h !in 0..23||m !in 0..59||title.text.isBlank()){status.text="Enter rule name and valid HH:mm";return@setOnClickListener}
   if(a=="WEB" && (!v.startsWith("https://") || !android.util.Patterns.WEB_URL.matcher(v).matches())){status.text="Enter a valid https:// URL";return@setOnClickListener}
   if(a in listOf("DIAL","WHATSAPP") && v.count { it in '0'..'9' }<7){status.text="Enter a valid phone number";return@setOnClickListener}
   if(a=="REMINDER" && v.isBlank()){status.text="Enter the reminder text";return@setOnClickListener}
   val existing=AutomationEngine.load(this@MainActivity)
   val previous=existing.find{it.id==editingId}
   val usedIds=existing.map{it.id}.toSet()
   val id=previous?.id?:(1..Int.MAX_VALUE).first{it !in usedIds}
   val rule=AutomationRule(id,title.text.toString().trim(),h,m,a,v,previous?.enabled?:true)
   AutomationEngine.save(this@MainActivity,existing.filterNot{it.id==id}+rule)
   clearEditor();status.text="Rule saved. Android may delay reminders due to battery restrictions.";refresh()
  } })
  refresh()
 }
 private var editingId:Int?=null
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data);if(requestCode==speechCode && resultCode==RESULT_OK) { val s=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty();command.setText(s);runCommand(s) } }
 private fun number()=getPreferences(0).getString("contact","")!!.filter { it.isDigit()||it=='+' }
 private fun dial() { val n=number();if(n.isBlank()){status.text="Save a trusted contact first";return};openExternal(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$n"))) }
 private fun whatsapp() { val n=number().filter { it.isDigit() };if(n.isBlank()){status.text="Save a trusted contact first";return}; val url="https://wa.me/$n?text="+Uri.encode("Good morning! Have a wonderful day.");if(openExternal(Intent(Intent.ACTION_VIEW,Uri.parse(url))))status.text="Review and tap Send in WhatsApp" }
 private fun openExternal(intent: Intent): Boolean = try {
  startActivity(intent)
  true
 } catch (_: ActivityNotFoundException) {
  status.text="No compatible app found on this phone"
  false
 }
 private fun runCommand(raw:String) { val s=raw.lowercase(Locale.ROOT);when {
  s.contains("wifi")||s.contains("ওয়াইফাই")||s.contains("ওয়াইফাই")->openExternal(Intent(Settings.ACTION_WIFI_SETTINGS))
  s.contains("good morning")||s.contains("গুড মর্নিং")->whatsapp()
  s.contains("call")||s.contains("কল")||s.contains("ফোন কর")->dial()
  s.contains("alarm")||s.contains("অ্যালার্ম")->status.text="Tap the 7 AM alarm button to configure your Clock"
  else->status.text="Unknown command. Try: wifi, good morning, call, alarm"
 } }
}
