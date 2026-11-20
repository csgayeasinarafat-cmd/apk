package com.mylife.agent

import android.app.*
import android.content.*
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.*
import java.time.LocalDate
import java.util.Locale

class VoiceActivity:Activity(){
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
            try{startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,if(language.selectedItemPosition==0)"bn-BD" else "en-US"),41)}
            catch(_:ActivityNotFoundException){feedback.text="Speech service unavailable. Type your command."}
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
        val s=original.lowercase(Locale.ROOT).map{if(it in '০'..'৯')('0'+(it-'০')) else it}.joinToString("")
        val daily=listOf("প্রতিদিন","daily","every day").any{s.contains(it)}
        val tomorrow=s.contains("আগামীকাল")||s.contains("tomorrow")
        val morning=listOf("সকাল","morning").any{s.contains(it)}
        val night=listOf("রাত","বিকাল","বিকেল","evening","night").any{s.contains(it)} || Regex("\\bpm\\b").containsMatchIn(s)
        val numbers=listOf("বারো","এগারো","দশ","নয়","নয়","আট","সাত","ছয়","ছয়","পাঁচ","চার","তিন","দুই","এক")
        val values=listOf(12,11,10,9,9,8,7,6,6,5,4,3,2,1)
        val time=Regex("(?<![0-9])([0-9]{1,2})(?::([0-9]{2}))?(?![0-9])").find(s)
        val word=numbers.indexOfFirst { s.contains(it+"টা") || s.contains(it+"টায়") }
        var h=time?.groupValues?.get(1)?.toIntOrNull() ?: values.getOrNull(word)
        val m=time?.groupValues?.get(2)?.toIntOrNull()?:0
        if(h!=null && night && h in 1..11)h+=12
        if(h==12 && (morning || s.contains(" am")))h=0
        val memory=getSharedPreferences("private_memory",0)
        val call=s.contains("ডায়ালার")||s.contains("ডায়ালার")||s.contains("ফোন")||Regex("\\b(call|dial)\\b").containsMatchIn(s)
        val message=s.contains("মেসেজ")||s.contains("message")||s.contains("whatsapp")
        val quran=listOf("বাকারা","কুরআন","quran","baqarah").any{s.contains(it)}
        val reminder=listOf("মনে কর","remind").any{s.contains(it)}
        if(listOf(call,message,quran,reminder).count{it}!=1 || (daily && tomorrow)){feedback.text="Please request one action at a time · একটি কাজ বলুন";return}
        val action=when{
            quran->"WEB|https://quran.com/2"
            call||message->{
                val matches=memory.all.filterKeys{it.startsWith("Contact/") && s.contains(it.substringAfter('/').lowercase(Locale.ROOT))}
                if(matches.size!=1){feedback.text="Include one saved contact nickname · পরিচিতির ডাকনাম দিন";return}
                val number=matches.values.single().toString().filter{it.isDigit()||it=='+'}
                if(call)"DIAL|"+number else "WHATSAPP|"+number+"~"+if(s.contains("good morning")||s.contains("গুড মর্নিং"))"Good morning!" else {feedback.text="Specify a supported message: Good morning";return}
            }
            else->"NOTE|"+original
        }
        if((daily||tomorrow) && h==null){feedback.text="Please include a time, for example 07:00 · সময় দিন";return}
        if(h!=null && !daily && !tomorrow){feedback.text="Specify daily or tomorrow · প্রতিদিন অথবা আগামীকাল বলুন";return}
        if((h?:0) !in 0..23 || m !in 0..59 || !AdvancedRules.validActions(action)){feedback.text="Check the time and contact";return}
        val event=if(tomorrow)"DATE" else if(daily)"TIME" else "MANUAL"
        val date=if(tomorrow)LocalDate.now().plusDays(1).toString() else ""
        val r=AdvancedRule(0,original,h?:0,m,"1234567",action,true,event,date)
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
