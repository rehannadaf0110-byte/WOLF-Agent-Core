package com.wolf.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.charset.Charset
import kotlin.concurrent.thread

class WolfBridgeService : Service() {
    @Volatile private var running=false
    private var server:ServerSocket?=null
    override fun onCreate(){super.onCreate(); startForeground(7821,notification()); startBridge()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int { startBridge(); return START_STICKY }
    override fun onBind(intent:Intent?):IBinder?=null
    private fun notification():Notification {
        val manager=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26) manager.createNotificationChannel(NotificationChannel("wolf_bridge","WOLF local bridge",NotificationManager.IMPORTANCE_LOW))
        return if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,"wolf_bridge").setContentTitle("WOLF Companion active").setContentText("Local-only device bridge on port 8788").setSmallIcon(android.R.drawable.ic_menu_manage).build() else Notification.Builder(this).setContentTitle("WOLF Companion active").setContentText("Local-only device bridge").setSmallIcon(android.R.drawable.ic_menu_manage).build()
    }
    @Synchronized private fun startBridge(){ if(running)return; running=true; thread(name="wolf-local-http") { try { server=ServerSocket(8788,8,java.net.InetAddress.getByName("127.0.0.1")); while(running){ val client=server?.accept()?:break; thread { handle(client) } } } catch(_:Exception) { } finally { running=false } } }
    private fun handle(client:Socket){ client.use { s -> try {
        s.soTimeout=5000; val input=BufferedInputStream(s.getInputStream()); val out=BufferedOutputStream(s.getOutputStream())
        val head=StringBuilder(); var last4=""; while(head.length<12000){ val n=input.read(); if(n<0)break; head.append(n.toChar()); last4=(last4+n.toChar()).takeLast(4); if(last4=="\r\n\r\n")break }
        val lines=head.toString().split("\r\n"); val first=lines.firstOrNull().orEmpty().split(" "); val method=first.getOrNull(0).orEmpty(); val route=first.getOrNull(1).orEmpty(); val headers=mutableMapOf<String,String>(); lines.drop(1).takeWhile{it.isNotBlank()}.forEach{val ix=it.indexOf(':');if(ix>0)headers[it.substring(0,ix).trim().lowercase()]=it.substring(ix+1).trim()}
        val len=(headers["content-length"]?:"0").toIntOrNull()?.coerceIn(0,20000)?:0; val bytes=ByteArray(len); var read=0; while(read<len){val n=input.read(bytes,read,len-read);if(n<0)break;read+=n}; val body=String(bytes,0,read,Charset.forName("UTF-8"))
        val expected=getSharedPreferences("wolf",Context.MODE_PRIVATE).getString("token","")
        if(headers["x-wolf-token"]!=expected){respond(out,401,JSONObject().put("ok",false).put("error","Unauthorized"));return}
        if(method=="GET"&&route=="/status"){respond(out,200,JSONObject().put("ok",true).put("accessibilityEnabled",WolfAccessibilityService.instance!=null).put("port",8788));return}
        if(method=="POST"&&route=="/action"){
            val json=JSONObject(body); if(json.optBoolean("confirmAction")!=true){respond(out,403,JSONObject().put("ok",false).put("error","confirmAction=true required"));return}
            val service=WolfAccessibilityService.instance; if(service==null){respond(out,409,JSONObject().put("ok",false).put("error","Enable WOLF Accessibility in Android Settings first."));return}
            val result=service.execute(json); respond(out,if(result.optBoolean("ok"))200 else 400,result);return
        }
        respond(out,404,JSONObject().put("ok",false).put("error","Not found"))
    }catch(e:Exception){try{val out=BufferedOutputStream(s.getOutputStream());respond(out,500,JSONObject().put("ok",false).put("error",e.message?:"Bridge error"))}catch(_:Exception){}} } }
    private fun respond(out:BufferedOutputStream,status:Int,obj:JSONObject){val body=obj.toString().toByteArray(Charset.forName("UTF-8"));val phrase=when(status){200->"OK";401->"Unauthorized";403->"Forbidden";404->"Not Found";409->"Conflict";else->"Error"};val header="HTTP/1.1 $status $phrase\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n";out.write(header.toByteArray());out.write(body);out.flush()}
    override fun onDestroy(){running=false;try{server?.close()}catch(_:Exception){};super.onDestroy()}
}
