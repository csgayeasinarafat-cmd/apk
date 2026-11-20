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
 private fun render(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(25,25,25,25)}
 val scroll=ScrollView(this).apply{addView(root)};setContentView(scroll)
 fun label(s:String){root.addView(TextView(this).apply{text=s;textSize=17f;setPadding(0,12,0,4)})}
 label("MyLife AI · Advanced Rule Builder")
 label("Trigger → Conditions → Actions")
 val name=EditText(this).apply{hint="Rule name"};root.addView(name)
 val description=EditText(this).apply{hint="Description"};root.addView(description)
 val events=RuleConditions.events.keys.toList()
 val trigger=Spinner(this).apply{adapter=ArrayAdapter(this@AdvancedActivity,android.R.layout.simple_spinner_dropdown_item,RuleConditions.events.values.toList())};root.addView(trigger)
 val date=EditText(this).apply{hint="Date, package, device name or lat,lon,radius"};root.addView(date)
 var condition=""
 val cond=Button(this).apply{text="Condition · Always"};root.addView(cond)
 cond.setOnClickListener{RuleConditions.edit(this,condition){condition=it;cond.text="Condition · "+RuleConditions.describe(it)}}
 val delivery=TextView(this);root.addView(delivery)
 trigger.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
  override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
  override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:android.view.View?,position:Int,id:Long){
   val type=events[position]
   date.visibility=if(type in setOf("DATE","APP_OPEN","NOTIFICATION","BT_ON","BT_OFF","ARRIVE","DEPART"))android.view.View.VISIBLE else android.view.View.GONE
   date.hint=when(type){"DATE"->"YYYY-MM-DD";"BT_ON","BT_OFF"->"Bluetooth device name";"ARRIVE","DEPART"->"Latitude, longitude, radius (metres)";else->"Android package name"}
   delivery.text=when(type){"BATTERY","POWER_ON","POWER_OFF","WIFI","NETWORK"->"Observed while MyLife's process is running.";"APP_OPEN"->"Periodic check. Enable usage access in Permissions.";"ARRIVE","DEPART"->"Allow location access in the background.";"NOTIFICATION"->"Enable notification access. Only the package name is matched.";else->""}
  }
 }
 val time=EditText(this).apply{hint="HH:mm (24h)"};root.addView(time)
 label("Days: 1=Mon, 2=Tue ... 7=Sun. Examples: 1234567 / 135")
 val days=EditText(this).apply{setText("1234567")};root.addView(days)
 label("Actions: one ACTION|value per line. Multiple lines = multiple actions.")
 val actions=EditText(this).apply{minLines=4;hint="WEB|https://quran.com/2\nQURAN|settings\nNOTE|Drink water"};root.addView(actions)
 root.addView(Button(this).apply{text="Choose time · সময়";setOnClickListener{
  val now=java.util.Calendar.getInstance()
  android.app.TimePickerDialog(this@AdvancedActivity,{_,h,m->time.setText(java.lang.String.format(java.util.Locale.ROOT,"%02d:%02d",h,m))},now.get(11),now.get(12),true).show()
 }})
 root.addView(Button(this).apply{text="Select weekdays · দিন";setOnClickListener{
  val checked=BooleanArray(7){(it+1).toString() in days.text.toString()}
  android.app.AlertDialog.Builder(this@AdvancedActivity).setTitle("Weekdays").setMultiChoiceItems(arrayOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"),checked){_,i,on->checked[i]=on}
   .setPositiveButton("Apply"){_,_->days.setText(checked.indices.filter{checked[it]}.joinToString(""){(it+1).toString()})}.setNegativeButton("Cancel",null).show()
 }})
 root.addView(Button(this).apply{text="Add action · কাজ যোগ করুন";setOnClickListener{
  val types=ActionCatalog.labels.keys.toTypedArray()
  val labels=ActionCatalog.labels.values.toTypedArray()
  android.app.AlertDialog.Builder(this@AdvancedActivity).setTitle("Then…").setItems(labels){_,index->
   fun append(value:String){actions.setText(listOf(actions.text.toString().trim(),types[index]+"|"+value).filter{it.isNotEmpty()}.joinToString("\n"))}
   if(types[index] in setOf("WIFI","BLUETOOTH","QURAN","ALARM","PAUSE","STOP"))append("settings") else {
    val input=EditText(this@AdvancedActivity).apply{setSingleLine();hint=when(index){0,1->"https://";3->"+880…";4->"+880…~Message";9->"com.example.app";10->"Seconds (1–86400)";11->"Rule ID";else->"Text"}}
    android.app.AlertDialog.Builder(this@AdvancedActivity).setTitle(labels[index]).setView(input).setPositiveButton("Add"){_,_->append(input.text.toString().trim())}.setNegativeButton("Cancel",null).show()
   }
  }.show()
 }})
 val info=TextView(this).apply{text="Actions appear in order. External apps open when you tap their notifications. Add a Delay to wait between steps."};root.addView(info)
 val search=EditText(this).apply{hint="Find by name or description · খুঁজুন"}
 val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
 fun clear(){editing=null;description.text.clear();trigger.setSelection(0);date.text.clear();condition="";cond.text="Condition · Always";name.text.clear();time.text.clear();days.setText("1234567");actions.text.clear()}
 fun refresh(){list.removeAllViews();AdvancedRules.load(this).filter{it.name.contains(search.text,true)||it.description.contains(search.text,true)}.sortedBy{it.name.lowercase()}.forEach{r->
  list.addView(TextView(this).apply{text="#${r.id} ${r.name} · %02d:%02d · days ${r.days} · ${if(r.enabled)"ON" else "OFF"}".format(r.hour,r.minute);textSize=16f;setPadding(0,15,0,5)})
  fun button(t:String,cb:()->Unit){list.addView(Button(this).apply{text=t;setOnClickListener{cb()}})}
  button("Duplicate · কপি") {
   val rules=AdvancedRules.load(this)
   val used=rules.map{it.id}.toSet()
   AdvancedRules.save(this,rules+r.copy(id=(1..Int.MAX_VALUE).first{it !in used},name=r.name+" (copy)",enabled=false))
   refresh()
  }
  button("Edit") {editing=r.id;description.setText(r.description);trigger.setSelection(events.indexOf(r.event).coerceAtLeast(0));date.setText(r.date);condition=r.condition;cond.text="Condition · "+RuleConditions.describe(condition);name.setText(r.name);time.setText("%02d:%02d".format(r.hour,r.minute));days.setText(r.days);actions.setText(r.actions)}
  button(if(r.enabled)"Disable" else "Enable") {AdvancedRules.save(this,AdvancedRules.load(this).map{if(it.id==r.id)it.copy(enabled=!it.enabled)else it});refresh()}
  button("Run now (notifications)"){AdvancedRules.execute(this,r)}
  button("Delete") {AdvancedRules.remove(this,r.id);if(editing==r.id)clear();refresh()}
 }}
 root.addView(Button(this).apply{text="Save rule";setOnClickListener{
  val event=events[trigger.selectedItemPosition];val day=date.text.toString().trim();if(event in setOf("ARRIVE","DEPART") && runCatching{OptionalEvents.area(day)}.isFailure){Toast.makeText(this@AdvancedActivity,"Enter latitude,longitude,radius in metres",0).show();return@setOnClickListener};if(event in setOf("BT_ON","BT_OFF","APP_OPEN","NOTIFICATION") && day.isBlank()){Toast.makeText(this@AdvancedActivity,"Enter package or device name",0).show();return@setOnClickListener};if(event=="DATE" && runCatching{java.time.LocalDate.parse(day)}.isFailure){Toast.makeText(this@AdvancedActivity,"Enter YYYY-MM-DD",Toast.LENGTH_SHORT).show();return@setOnClickListener};val parts=(if(event in setOf("TIME","DATE"))time.text.toString().trim() else "00:00").split(":");val h=parts.getOrNull(0)?.toIntOrNull();val m=parts.getOrNull(1)?.toIntOrNull();val d=days.text.toString()
  if(name.text.isBlank()||parts.size!=2||h==null||m==null||h !in 0..23||m !in 0..59||d.isEmpty()||d.any{it !in '1'..'7'}||!RuleConditions.valid(condition)||!AdvancedRules.validActions(actions.text.toString())){
   Toast.makeText(this@AdvancedActivity,"Check name, time, days and ACTION|value syntax",Toast.LENGTH_LONG).show();return@setOnClickListener}
  val existing=AdvancedRules.load(this@AdvancedActivity)
  val previous=existing.find{it.id==editing}
  val usedIds=existing.map{it.id}.toSet()
  val id=previous?.id?:(1..Int.MAX_VALUE).first{it !in usedIds}
  val r=AdvancedRule(id,name.text.toString().trim(),h,m,d,actions.text.toString().trim(),previous?.enabled?:true,event,day,condition,description.text.toString())
  AdvancedRules.save(this@AdvancedActivity,existing.filterNot{it.id==id}+r)
  clear();refresh();Toast.makeText(this@AdvancedActivity,"Rule saved",Toast.LENGTH_SHORT).show()
 }})
 root.addView(Button(this).apply{text="Open exact alarm permission settings (Android 12+)";setOnClickListener{
  if(android.os.Build.VERSION.SDK_INT>=31)try{startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:$packageName")))}catch(_:Exception){}
 }})
 root.addView(Button(this).apply{text="Execution history · ইতিহাস";setOnClickListener{
  val entries=AdvancedRules.history(this@AdvancedActivity)
  android.app.AlertDialog.Builder(this@AdvancedActivity).setTitle("Recent activity").setMessage(entries.joinToString("\n\n").ifEmpty{"No executions yet"}).setPositiveButton("OK",null).show()
 }})
 label("Saved rules");root.addView(search)
 search.addTextChangedListener(object:android.text.TextWatcher{
  override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
  override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh()}
  override fun afterTextChanged(s:android.text.Editable?){}
 });root.addView(list);refresh()
 }
}
