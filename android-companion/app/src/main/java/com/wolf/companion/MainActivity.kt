package com.wolf.companion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import java.util.UUID

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val prefs=getSharedPreferences("wolf",Context.MODE_PRIVATE)
        if(prefs.getString("token",null)==null) prefs.edit().putString("token",UUID.randomUUID().toString()+UUID.randomUUID().toString()).apply()
        val token=prefs.getString("token","")!!
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(28,32,28,24); gravity=Gravity.CENTER_HORIZONTAL }
        root.addView(TextView(this).apply { text="WOLF Companion"; textSize=26f })
        root.addView(TextView(this).apply { text="Local device-control bridge · port 8788\nKeep this app running while using the bridge. Enable Accessibility only if you understand the permission."; textSize=15f; setPadding(0,16,0,20) })
        val serverStatus=TextView(this).apply { text="Starting local bridge…"; textSize=14f }
        root.addView(serverStatus)
        root.addView(Button(this).apply { text="Enable WOLF Accessibility"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } })
        root.addView(Button(this).apply { text="Copy WOLF connection token"; setOnClickListener { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("WOLF token",token)); Toast.makeText(this@MainActivity,"Token copied. Paste it into WOLF Android settings.",Toast.LENGTH_LONG).show() } })
        root.addView(Button(this).apply { text="Start / restart bridge"; setOnClickListener { startService(Intent(this@MainActivity,WolfBridgeService::class.java)); serverStatus.text="Bridge start requested at 127.0.0.1:8788" } })
        val url=EditText(this).apply { hint="WOLF dashboard URL (optional)"; setText("http://127.0.0.1:8787"); inputType=android.text.InputType.TYPE_TEXT_VARIATION_URI }
        root.addView(url,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))
        val web=WebView(this).apply { settings.javaScriptEnabled=true; settings.domStorageEnabled=true; webViewClient=WebViewClient(); webChromeClient=WebChromeClient() }
        root.addView(Button(this).apply { text="Load WOLF inside this app"; setOnClickListener { val target=url.text.toString().trim(); if(target.startsWith("http://127.0.0.1:")||target.startsWith("https://")) web.loadUrl(target) else Toast.makeText(this@MainActivity,"Use localhost for the local WOLF server or an HTTPS server you trust.",Toast.LENGTH_LONG).show() } })
        root.addView(web,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        setContentView(root)
        web.loadUrl("http://127.0.0.1:8787")
        startService(Intent(this,WolfBridgeService::class.java))
    }
}
