package com.mylife.agent

import android.app.*
import android.content.*
import android.os.Bundle
import android.widget.*
import org.json.*

class MemoryActivity:Activity(){
    private val store by lazy { getSharedPreferences("private_memory",0) }
    private val kinds=listOf("Contact","Application","Website","Routine","Reciter","Preference","Command","Message template")
    private lateinit var category:Spinner
    private lateinit var name:EditText
    private lateinit var value:EditText
    private lateinit var list:LinearLayout
    private var editing:String?=null
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val page=LinearLayout(this).apply { orientation=1;setPadding(24,30,24,24) }
        setContentView(ScrollView(this).apply { addView(page) })
        page.addView(TextView(this).apply{text="Personal memory · ব্যক্তিগত তথ্য";textSize=24f})
        category=Spinner(this).apply{adapter=ArrayAdapter(this@MemoryActivity,android.R.layout.simple_spinner_dropdown_item,kinds)};page.addView(category)
        if(intent.getBooleanExtra("contacts",false))category.setSelection(0)
        name=EditText(this).apply{hint="Name / nickname"};page.addView(name)
        value=EditText(this).apply{hint="Value / phone / message"};page.addView(value)
        fun btn(label:String,run:()->Unit){page.addView(Button(this).apply{text=label;setOnClickListener{run()}})}
        btn("Save · সংরক্ষণ"){
            val n=name.text.toString().trim();val v=value.text.toString().trim()
            if(n.isEmpty() || v.isEmpty())return@btn
            val k=category.selectedItem.toString()+"/"+n
            if(k!=editing && store.contains(k)){toast("That name already exists");return@btn}
            store.edit().apply{editing?.let{remove(it)};putString(k,v)}.apply()
            reset();refresh()
        }
        btn("Export memory and advanced rules"){
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE,"mylife-private-backup.json"),1)
        }
        btn("Import backup"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),2)}
        list=LinearLayout(this).apply{orientation=1};page.addView(list);refresh()
    }
    private fun reset(){editing=null;name.text.clear();value.text.clear()}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun refresh(){list.removeAllViews();store.all.filterKeys{!intent.getBooleanExtra("contacts",false)||it.startsWith("Contact/")}.toSortedMap().forEach{(k,v)->
        list.addView(Button(this).apply{text=k;setOnClickListener{
            AlertDialog.Builder(this@MemoryActivity).setTitle(k).setMessage(v.toString()).setPositiveButton("Edit"){_,_->
                category.setSelection(kinds.indexOf(k.substringBefore('/')).coerceAtLeast(0));name.setText(k.substringAfter('/'));value.setText(v.toString());editing=k
            }.setNegativeButton("Delete"){_,_->store.edit().remove(k).apply();reset();refresh()}.setNeutralButton("Close",null).show()
        }})
    }}
    override fun onActivityResult(request:Int,result:Int,data:Intent?){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK)return
        val uri=data?.data?:return
        runCatching {
            if(request==1){
                val rules=JSONArray();AdvancedRules.load(this).forEach{rules.put(it.toJson())}
                val obj=JSONObject().put("format",1).put("memory",JSONObject(store.all)).put("rules",rules)
                requireNotNull(contentResolver.openOutputStream(uri,"wt")).bufferedWriter().use{it.write(obj.toString(2))}
                toast("Backup saved")
            }else if(request==2){
                val body=requireNotNull(contentResolver.openInputStream(uri)).use { stream ->
                    val buf=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
                    while(true){val count=stream.read(chunk);if(count<0)break;require(buf.size()+count<=1_000_000);buf.write(chunk,0,count)};buf.toString("UTF-8")
                }
                val obj=JSONObject(body);require(obj.getInt("format")==1)
                val memory=obj.getJSONObject("memory");val values=memory.keys().asSequence().associateWith {
                    require(it.substringBefore('/') in kinds && '/' in it && memory.get(it) is String);memory.getString(it)
                }
                val rows=obj.getJSONArray("rules")
                val imports=(0 until rows.length()).map{AdvancedRule.parse(rows.getJSONObject(it)).also{r->
                    require(r.id>0 && r.name.isNotBlank() && r.hour in 0..23 && r.minute in 0..59 && r.days.isNotEmpty() && r.days.all{it in '1'..'7'} && AdvancedRules.validActions(r.actions) && r.event in RuleConditions.events)
                    if(r.event=="DATE")java.time.LocalDate.parse(r.date)
                    require(RuleConditions.valid(r.condition))
                    if(r.event in setOf("ARRIVE","DEPART"))OptionalEvents.area(r.date)
                }}
                AlertDialog.Builder(this).setTitle("Import backup?").setMessage("Add missing memory entries and rules. Imported rules start disabled. Existing entries with matching IDs are kept.")
                    .setPositiveButton("Import"){_,_-> val current=AdvancedRules.load(this);val ids=current.map{it.id}.toSet()
                        AdvancedRules.save(this,current+imports.distinctBy{it.id}.filterNot{it.id in ids}.map{it.copy(enabled=false)})
                        store.edit().apply{values.filterKeys{!store.contains(it)}.forEach{(k,v)->putString(k,v)}}.apply();refresh()
                    }.setNegativeButton("Cancel",null).show()
            }
        }.onFailure {toast("Backup could not be read or written")}
    }
}
