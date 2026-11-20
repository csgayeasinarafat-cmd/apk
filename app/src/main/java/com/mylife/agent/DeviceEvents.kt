package com.mylife.agent

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.*
import android.net.*
import android.os.*
import android.service.notification.*

class DeviceEvents:Application(){
    private var previousBattery=-1
    private val observer=object:BroadcastReceiver(){
        override fun onReceive(c:Context,i:Intent){
            val event=when(i.action){
                Intent.ACTION_BATTERY_CHANGED->{
                    val value=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
                    val prior=previousBattery;previousBattery=value
                    if(prior<0 || prior==value)return
                    "BATTERY"
                }
                Intent.ACTION_POWER_CONNECTED->"POWER_ON"
                Intent.ACTION_POWER_DISCONNECTED->"POWER_OFF"
                else->return
            }
            AdvancedRules.fire(c,event)
        }
    }
    override fun onCreate(){super.onCreate()
        registerReceiver(observer,IntentFilter().apply{addAction(Intent.ACTION_BATTERY_CHANGED);addAction(Intent.ACTION_POWER_CONNECTED);addAction(Intent.ACTION_POWER_DISCONNECTED)})
        val cm=getSystemService(ConnectivityManager::class.java)
        cm.registerDefaultNetworkCallback(object:ConnectivityManager.NetworkCallback(){
            var state:Pair<Boolean,Boolean>?=null
            fun changed(next:Pair<Boolean,Boolean>){
                val old=state;state=next
                if(old==null)return
                if(old.first!=next.first)AdvancedRules.fire(this@DeviceEvents,"NETWORK")
                if(old.second!=next.second)AdvancedRules.fire(this@DeviceEvents,"WIFI")
            }
            override fun onCapabilitiesChanged(network:Network,cap:NetworkCapabilities){changed(cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) to cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))}
            override fun onLost(network:Network){changed(false to false)}
        },Handler(Looper.getMainLooper()))
    }
}

class NotificationEvents:NotificationListenerService(){
    override fun onNotificationPosted(sbn:StatusBarNotification){
        if(sbn.packageName==packageName)return
        AdvancedRules.load(this).filter{it.enabled && it.event=="NOTIFICATION" && it.date==sbn.packageName}.forEach{AdvancedRules.execute(this,it)}
    }
}
class BluetoothEvents:BroadcastReceiver(){
    override fun onReceive(c:Context,i:Intent){
        val event=when(i.action){BluetoothDevice.ACTION_ACL_CONNECTED->"BT_ON";BluetoothDevice.ACTION_ACL_DISCONNECTED->"BT_OFF";else->return}
        val device=i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?:return
        val name=try{device.name}catch(_:SecurityException){return}
        AdvancedRules.load(c).filter{it.enabled && it.event==event && it.date.equals(name,true)}.forEach{AdvancedRules.execute(c,it)}
    }
}
