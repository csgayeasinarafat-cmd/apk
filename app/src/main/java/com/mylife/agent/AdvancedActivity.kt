package com.mylife.agent
import android.app.*
import android.content.*
import android.os.Bundle
import android.graphics.Color
import android.widget.*

/** Local rule editor. Multiple THEN actions: one ACTION|value per line.
 * AND condition: time plus allowed weekdays. OR behavior: any chosen day triggers the rule.
 */
class AdvancedActivity:Activity(){private var editing:Int?=null
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);AdvancedRules.seed(this);render()}
 private fun render(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(25,25,25,25);setBackgroundColor(Color.WHITE)}
 val scroll=ScrollView(this).apply{addView(root)};setContentView(scroll)
 fun label(s:String){root.addView(TextView(this).apply{text=s;textSize=17f;setPadding(0,12,0,4)})}
 label("MyLife AI · Advanced Rule Builder")
 label("IF time AND selected weekdays → THEN all actions")
 val name=EditText(this).apply{hint="Rule name"};root.addView(name)
 val time=EditText(this).apply{hint="HH:mm (24h)"};root.addView(time)
 label("Days: 1=Mon, 2=Tue ... 7=Sun. Examples: 1234567 / 135")
 val days=EditText(this).apply{setText("1234567")};root.addView(days)
 label("Actions: one ACTION|value per line. Multiple lines = multiple actions.")
 val actions=EditText(this).apply{minLines=4;hint="WEB|https://quran.com/2\nAUDIO|https://example.com/audio.mp3\nNOTE|Drink water"};root.addView(actions)
 val info=TextView(this).apply{text="Supported: WEB, AUDIO, WIFI, DIAL, WHATSAPP, NOTE. Audio URLs must point to playable HTTPS audio files. Actions requiring user interaction open from notifications."};root.addView(info)
 val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
 fun clear(){editing=null;name.text.clear();time.text.clear();days.setText("1234567");actions.text.clear()}
 fun refresh(){list.removeAllViews();AdvancedRules.load(this).forEach{r->
  list.addView(TextView(this).apply{text="${r.name} · %02d:%02d · days ${r.days} · ${if(r.enabled)"ON" else "OFF"}".format(r.hour,r.minute);textSize=16f;setPadding(0,15,0,5)})
  fun button(t:String,cb:()->Unit){list.addView(Button(this).apply{text=t;setOnClickListener{cb()}})}
  button("Edit") {editing=r.id;name.setText(r.name);time.setText("%02d:%02d".format(r.hour,r.minute));days.setText(r.days);actions.setText(r.actions)}
  button(if(r.enabled)"Disable" else "Enable") {AdvancedRules.save(this,AdvancedRules.load(this).map{if(it.id==r.id)it.copy(enabled=!it.enabled)else it});refresh()}
  button("Run now (notifications)"){AdvancedRules.execute(this,r)}
  button("Delete") {AdvancedRules.remove(this,r.id);if(editing==r.id)clear();refresh()}
 }}
 root.addView(Button(this).apply{text="Save rule";setOnClickListener{
  val parts=time.text.toString().trim().split(":");val h=parts.getOrNull(0)?.toIntOrNull();val m=parts.getOrNull(1)?.toIntOrNull();val d=days.text.toString()
  if(name.text.isBlank()||parts.size!=2||h==null||m==null||h !in 0..23||m !in 0..59||d.isEmpty()||d.any{it !in '1'..'7'}||!AdvancedRules.validActions(actions.text.toString())){
   Toast.makeText(this@AdvancedActivity,"Check name, time, days and ACTION|value syntax",Toast.LENGTH_LONG).show();return@setOnClickListener}
  val existing=AdvancedRules.load(this@AdvancedActivity)
  val previous=existing.find{it.id==editing}
  val usedIds=existing.map{it.id}.toSet()
  val id=previous?.id?:(1..Int.MAX_VALUE).first{it !in usedIds}
  val r=AdvancedRule(id,name.text.toString().trim(),h,m,d,actions.text.toString().trim(),previous?.enabled?:true)
  AdvancedRules.save(this@AdvancedActivity,existing.filterNot{it.id==id}+r)
  clear();refresh();Toast.makeText(this@AdvancedActivity,"Rule saved",Toast.LENGTH_SHORT).show()
 }})
 root.addView(Button(this).apply{text="Open exact alarm permission settings (Android 12+)";setOnClickListener{
  if(android.os.Build.VERSION.SDK_INT>=31)try{startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:$packageName")))}catch(_:Exception){}
 }})
 label("Saved rules");root.addView(list);refresh()
 }
}
