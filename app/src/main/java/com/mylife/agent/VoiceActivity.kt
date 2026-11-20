package com.mylife.agent

import android.app.*
import android.content.*
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.*
import java.time.LocalDate
import java.util.Locale

class VoiceActivity:Activity(){
    private val interpreter:CommandInterpreter=OfflineCommandInterpreter
    private lateinit var input:EditText
    private lateinit var feedback:TextView
    private lateinit var language:Spinner
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val page=LinearLayout(this).apply{orientation=1;setPadding(32,40,32,32)}
        setContentView(ScrollView(this).apply{addView(page)})
        page.addView(TextView(this).apply{text="Voice assistant · ভয়েস সহকারী";textSize=26f})
        input=EditText(this).apply{hint="প্রতিদিন সকাল সাতটায় সূরা বাকারা চালু করবে";minLines=2};page.addView(input)
        language=Spinner(this).apply{adapter=ArrayAdapter(this@VoiceActivity,android.R.layout.simple_spinner_dropdown_item,listOf("বাংলা","English"))};page.addView(language)
        fun button(label:String,f:()->Unit){page.addView(Button(this).apply{text=label;setOnClickListener{f()}})}
        button("Speak · বলুন"){
            AlertDialog.Builder(this).setTitle("Use the installed speech service?").setMessage("Offline recognition is preferred. Your speech provider may process audio online.").setNegativeButton("Type instead",null).setPositiveButton("Use speech"){_,_->
            try{startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true).putExtra(RecognizerIntent.EXTRA_LANGUAGE,if(language.selectedItemPosition==0)"bn-BD" else "en-US"),41)}
            catch(_:ActivityNotFoundException){feedback.text="Speech service unavailable. Type your command."}
            }.show()
        }
        button("Review command · যাচাই করুন"){preview()}
        feedback=TextView(this).apply{textSize=17f};page.addView(feedback);intent.getStringExtra("command")?.let{input.setText(it);preview()}
    }
    override fun onActivityResult(request:Int,result:Int,data:Intent?){
        super.onActivityResult(request,result,data)
        if(request==41 && result==RESULT_OK){input.setText(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty());preview()}
    }
    private fun preview(){
        val original=input.text.toString().trim()
        val memory=getSharedPreferences("private_memory",0)
        val contacts=memory.all.filterKeys{it.startsWith("Contact/")}.mapKeys{it.key.substringAfter('/')}.mapValues{it.value.toString()}
        val apps=memory.all.filterKeys{it.startsWith("Application/")}.mapKeys{it.key.substringAfter('/')}.mapValues{it.value.toString()}
        val proposal=interpreter.propose(original,contacts,apps)
        if(proposal is Proposal.Clarify){feedback.text=proposal.message;return}
        val r=(proposal as Proposal.Ready).rule
        val event=r.event
        val date=r.date
        val action=r.actions
        val summary=listOf(original,event,date,if(event=="MANUAL")"" else "%02d:%02d".format(r.hour,r.minute),action).filter{it.isNotBlank()}.joinToString("\n")
        feedback.text=summary
        AlertDialog.Builder(this).setTitle("Confirm rule · নিশ্চিত করুন").setMessage(summary)
            .setPositiveButton(if(event=="MANUAL")"Save and notify" else "Save schedule"){_,_->
                val rules=AdvancedRules.load(this);val ids=rules.map{it.id}.toSet();val saved=r.copy(id=(1..Int.MAX_VALUE).first{it !in ids})
                AdvancedRules.save(this,rules+saved);memory.edit().putString("Command/"+original,original).apply()
                if(event=="MANUAL")AdvancedRules.execute(this,saved)
                feedback.text="Saved · সংরক্ষিত"
            }.setNegativeButton("Cancel",null).show()
    }
}


sealed interface Proposal {
    data class Ready(val rule:AdvancedRule):Proposal
    data class Clarify(val message:String):Proposal
}

interface CommandInterpreter {
    fun propose(original:String,contacts:Map<String,String>,apps:Map<String,String>):Proposal
}

object OfflineCommandInterpreter:CommandInterpreter {
    override fun propose(original:String,contacts:Map<String,String>,apps:Map<String,String>):Proposal {
        val s=original.lowercase(Locale.ROOT).map{if(it in '০'..'৯')('0'+(it-'০')) else it}.joinToString("")
        val daily=listOf("প্রতিদিন","daily","every day").any{s.contains(it)}
        val tomorrow=s.contains("আগামীকাল")||s.contains("tomorrow")
        val clock=RuleLogic.commandTime(s)
        val h=clock?.hour
        val m=clock?.minute?:0
        val call=s.contains("ডায়ালার")||s.contains("ডায়ালার")||s.contains("ফোন")||Regex("\\b(call|dial)\\b").containsMatchIn(s)
        val message=s.contains("মেসেজ")||s.contains("message")||s.contains("whatsapp")
        val quran=listOf("বাকারা","কুরআন","quran","baqarah").any{s.contains(it)}
        val reminder=listOf("মনে কর","remind").any{s.contains(it)}
        val launch=apps.filterKeys{s.contains(it.lowercase(Locale.ROOT))}.takeIf{listOf("open","খুল","চালু").any{s.contains(it)}}.orEmpty()
        if(listOf(call,message,quran,reminder,launch.isNotEmpty()).count{it}!=1 || (daily && tomorrow)){return Proposal.Clarify("Please request one action at a time · একটি কাজ বলুন")}
        val action=when{
            launch.isNotEmpty()->if(launch.size==1)"APP|"+launch.values.single() else return Proposal.Clarify("Select one application")
            quran->"WEB|https://quran.com/2"
            call||message->{
                val matches=contacts.filterKeys{s.contains(it.lowercase(Locale.ROOT))}
                if(matches.size!=1){return Proposal.Clarify("Include one saved contact nickname · পরিচিতির ডাকনাম দিন")}
                val number=matches.values.single().toString().filter{it.isDigit()||it=='+'}
                if(call)"DIAL|"+number else "WHATSAPP|"+number+"~"+if(s.contains("good morning")||s.contains("গুড মর্নিং"))"Good morning!" else {return Proposal.Clarify("Specify a supported message: Good morning")}
            }
            else->"NOTE|"+original
        }
        if((daily||tomorrow) && h==null){return Proposal.Clarify("Please include a time, for example 07:00 · সময় দিন")}
        if(h!=null && !daily && !tomorrow){return Proposal.Clarify("Specify daily or tomorrow · প্রতিদিন অথবা আগামীকাল বলুন")}
        if((h?:0) !in 0..23 || m !in 0..59 || !AdvancedRules.validActions(action)){return Proposal.Clarify("Check the time and contact")}
        val event=if(tomorrow)"DATE" else if(daily)"TIME" else "MANUAL"
        val date=if(tomorrow)LocalDate.now().plusDays(1).toString() else ""
        return Proposal.Ready(AdvancedRule(0,original,h?:0,m,"1234567",action,true,event,date))
    }
}
