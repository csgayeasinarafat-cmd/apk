"""Exercise the public dashboard, rule list and SQLite migration on an API 29 emulator."""
import subprocess
import re
import time
import xml.etree.ElementTree as ET

def adb(*args):
    return subprocess.check_output(["adb", *args], text=True).strip()

def nodes():
    for _ in range(3):
        adb("shell", "uiautomator", "dump", "/sdcard/mylife-qa.xml")
        xml = adb("shell", "cat", "/sdcard/mylife-qa.xml")
        if "<hierarchy" in xml:
            return ET.fromstring(xml).findall(".//node")
    raise AssertionError("No UI hierarchy")

def button(label, up=False):
    last=None
    for _ in range(15):
        tree = nodes()
        here=[(n.get("text"),n.get("bounds")) for n in tree]
        if here==last:up=not up
        last=here
        for node in tree:
            if node.get("text","").casefold() == label.casefold():
                x1,y1,x2,y2 = map(int,re.findall(r"\d+",node.get("bounds")))
                adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
                time.sleep(0.4)
                return
        root = tree[0]
        _,_,w,h = map(int,re.findall(r"\d+",root.get("bounds")))
        start,end = (h//4,h*3//4) if up else (h*3//4,h//4)
        adb("shell","input","swipe",str(w//2),str(start),str(w//2),str(end),"350")
    raise AssertionError("Missing control: "+label+"; visible: "+str([n.get("text") for n in tree if n.get("text")]))

def hide_keyboard():
    if "mInputShown=true" in adb("shell","dumpsys","input_method"):
        adb("shell","input","keyevent","KEYCODE_BACK")

def launch():
    adb("shell","am","start","-W","-n","com.mylife.agent/.MainActivity")

def count_rules(description=None,detail=None):
    # Debug builds permit run-as. Take a consistent, read-only snapshot after closing the activity.
    import sqlite3, tempfile, pathlib
    with tempfile.TemporaryDirectory() as tmp:
        for name in ("automation.db","automation.db-wal"):
            data = subprocess.run(["adb","exec-out","run-as","com.mylife.agent","cat","databases/"+name],capture_output=True)
            if data.returncode == 0 and (name.endswith(".db") or data.stdout[:4] in (b"7\x7f\x06\x82",b"7\x7f\x06\x83")):
                pathlib.Path(tmp,name).write_bytes(data.stdout)
        with sqlite3.connect(str(pathlib.Path(tmp,"automation.db"))) as db:
            if detail is not None:
                return db.execute("SELECT count(*) FROM history WHERE detail=?",(detail,)).fetchone()[0]
            if description is not None:
                import json
                return sum(json.loads(row[0]).get("description")==description for row in db.execute("SELECT data FROM rules"))
            return db.execute("SELECT count(*) FROM rules").fetchone()[0]

launch()
start=count_rules()
button("Automation studio")
button("Duplicate · কপি")
adb("shell","am","force-stop","com.mylife.agent")
launch()
assert count_rules()==start+1, "Duplicate did not persist after restart"
button("Automation studio")
button("Run now (notifications)")
assert "Morning Surah" in adb("shell","dumpsys","notification","--noredact"), "Notification absent"
button("Delete")
assert count_rules()==start, "Delete did not persist"
print("PASS: visual duplication, restart persistence, manual notification and deletion")

adb("shell","am","start","-W","--activity-clear-top","-n","com.mylife.agent/.MainActivity")
button("Voice assistant")
button("প্রতিদিন সকাল সাতটায় সূরা বাকারা চালু করবে")
adb("shell","input","text","daily%sat%s07:00%sremind%sme%sQA")
hide_keyboard()
button("Review command · যাচাই করুন")
button("Save schedule")
assert count_rules()==start+1,"Confirmed voice proposal did not create a rule"
adb("shell","am","start","-W","--activity-clear-top","-n","com.mylife.agent/.MainActivity")
button("Automation studio")
button("Edit")
button("Description",up=True)
adb("shell","input","text","edited-via-ui")
hide_keyboard()
button("Save rule")
assert count_rules("edited-via-ui")==1,"Edited description was not persisted"
print("PASS: typed voice proposal, confirmation, rule creation and visual editing")

# Isolated upgrade fixture in the disposable emulator. Keep the legacy JSON untouched
# and require the real AlarmManager receiver to post after the launcher goes to background.
import json
from xml.sax.saxutils import escape
adb("shell","pm","clear","com.mylife.agent")
hh,mm=map(int,adb("shell","date","+%H:%M").split(":"))
next_min=(hh*60+mm+2)%1440
legacy=[dict(id=83001,name="Scheduled migration QA",hour=next_min//60,minute=next_min%60,days="1234567",actions="NOTE|Scheduled delivery verified",enabled=True)]
xml='<map><string name="items">'+escape(json.dumps(legacy))+'</string></map>'
adb("shell","run-as","com.mylife.agent","mkdir","-p","shared_prefs")
subprocess.run(["adb","shell","run-as","com.mylife.agent","tee","shared_prefs/mylife_advanced_v12.xml"],input=xml,text=True,stdout=subprocess.DEVNULL,check=True)
launch()
assert count_rules()==1,"Legacy rules did not migrate"
adb("shell","input","keyevent","KEYCODE_HOME")
deadline=time.monotonic()+150
while time.monotonic()<deadline:
    if "Scheduled delivery verified" in adb("shell","dumpsys","notification","--noredact"):
        break
    time.sleep(4)
else:
    raise AssertionError("Scheduled notification did not arrive")
print("PASS: preference migration and background scheduled notification")

adb("shell","cmd","appops","set","com.mylife.agent","POST_NOTIFICATION","deny")
launch()
button("Automation studio")
button("Run now (notifications)")
assert count_rules(detail="Notifications disabled")>0,"Missing notification-denial history"
adb("shell","cmd","appops","set","com.mylife.agent","POST_NOTIFICATION","allow")
print("PASS: blocked notification recorded in history")

# Exercise MediaPlayer with an original test tone. Grant the launcher temporary read
# access to the emulator MediaStore URI, as a user selecting a recording would.
import io
import math
import struct
import wave
from xml.sax.saxutils import escape as xml_escape
title="MyLife QA tone"
file_name="mylife_qa_tone.wav"
stream=io.BytesIO()
with wave.open(stream,"wb") as wav:
    wav.setparams((1,2,8000,0,"NONE","uncompressed"))
    wav.writeframes(b"".join(struct.pack("<h",int(1000*math.sin(2*math.pi*220*i/8000))) for i in range(8000*12)))
base="content://media/external/audio/media"
adb("shell","content","insert","--uri",base,"--bind","_display_name:s:"+file_name,"--bind","mime_type:s:audio/wav")
raw=adb("shell","content query --uri "+base+" --projection _id --where \"_display_name='"+file_name+"'\"")
match=re.search(r"_id=(\d+)",raw)
assert match, "Test recording missing: "+raw
uri=base+"/"+match.group(1)
subprocess.run(["adb","shell","-T","content","write","--uri",uri],input=stream.getvalue(),check=True)
adb("shell","am","force-stop","com.mylife.agent")
player_xml='<map><string name="recordings">'+xml_escape(json.dumps({title:uri}))+'</string><string name="selected">'+title+'</string></map>'
subprocess.run(["adb","shell","run-as","com.mylife.agent","tee","shared_prefs/quran_player.xml"],input=player_xml,text=True,stdout=subprocess.DEVNULL,check=True)
adb("shell","am","start","-W","-n","com.mylife.agent/.MainActivity","--grant-read-uri-permission","-d",uri)
button("Quran player")
button("Play · চালান")
def position():
    for attempt in range(4):
      items=nodes()
      for n in items:
        m=re.match(r"(\d+):(\d+) / (\d+):(\d+)$",n.get("text",""))
        if m:return int(m[1])*60+int(m[2]),int(m[3])*60+int(m[4])
      _,_,w,h=map(int,re.findall(r"\d+",items[0].get("bounds")))
      adb("shell","input","swipe",str(w//2),str(h*3//4),str(w//2),str(h//4),"350")
    raise AssertionError("Player progress not found")
time.sleep(2)
p,duration=position()
assert duration>=11 and p>0,(p,duration)
button("Pause")
p,_=position()
time.sleep(1)
assert position()[0]==p,"Pause failed"
button("Resume")
adb("shell","input","keyevent","KEYCODE_HOME")
time.sleep(2)
services=adb("shell","dumpsys","activity","services","com.mylife.agent")
assert "AudioService" in services and "isForeground=true" in services,"Foreground audio service missing"
adb("shell","am","start","-W","--activity-clear-top","-n","com.mylife.agent/.MainActivity")
button("Quran player")
button("Stop")
assert position()==(0,0),"Audio stop failed"
print("PASS: local audio decode, progress, pause, resume, background foreground service and stop")
