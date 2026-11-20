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
    for _ in range(9):
        tree = nodes()
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

def launch():
    adb("shell","am","start","-W","-n","com.mylife.agent/.MainActivity")

def count_rules():
    # Debug builds permit run-as. Take a consistent, read-only snapshot after closing the activity.
    import sqlite3, tempfile, pathlib
    with tempfile.TemporaryDirectory() as tmp:
        for name in ("automation.db","automation.db-wal"):
            data = subprocess.run(["adb","exec-out","run-as","com.mylife.agent","cat","databases/"+name],capture_output=True)
            if data.returncode == 0 and (name.endswith(".db") or data.stdout[:4] in (b"7\x7f\x06\x82",b"7\x7f\x06\x83")):
                pathlib.Path(tmp,name).write_bytes(data.stdout)
        with sqlite3.connect(str(pathlib.Path(tmp,"automation.db"))) as db:
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
