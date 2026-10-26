package com.wolf.companion

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import org.json.JSONArray
import android.os.Bundle

class WolfAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance:WolfAccessibilityService?=null }
    override fun onServiceConnected(){super.onServiceConnected();instance=this}
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy(){if(instance===this)instance=null;super.onDestroy()}
    fun execute(b:JSONObject):JSONObject { return try { when(val action=b.optString("action")) {
        "back" -> JSONObject().put("ok",performGlobalAction(GLOBAL_ACTION_BACK)).put("action",action)
        "home" -> JSONObject().put("ok",performGlobalAction(GLOBAL_ACTION_HOME)).put("action",action)
        "notifications" -> JSONObject().put("ok",performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)).put("action",action)
        "read_ui" -> { val arr=JSONArray(); collect(rootInActiveWindow,arr,0); JSONObject().put("ok",true).put("action",action).put("items",arr) }
        "click_text" -> { val query=b.optString("text").take(160); val node=findText(rootInActiveWindow,query,0); val ok=node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)?:false; JSONObject().put("ok",ok).put("action",action).put("matchedText",query).put("error",if(ok) JSONObject.NULL else "No clickable text match found") }
        "type_text" -> { val text=b.optString("text").take(5000); val node=findFocusedEditable(rootInActiveWindow,0); val args=Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)}; val ok=node?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)?:false; JSONObject().put("ok",ok).put("action",action).put("chars",text.length).put("error",if(ok) JSONObject.NULL else "No focused editable field") }
        "tap" -> { val x=b.optDouble("x",-1.0).toFloat(); val y=b.optDouble("y",-1.0).toFloat(); if(x<0||y<0) JSONObject().put("ok",false).put("error","x and y must be non-negative") else { val p=Path().apply{moveTo(x,y)}; val g=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p,0,80)).build(); val ok=dispatchGesture(g,null,null); JSONObject().put("ok",ok).put("action",action) } }
        "scroll" -> { val down=b.optBoolean("down",true); val root=rootInActiveWindow; val n=findScrollable(root,0); val ok=n?.performAction(if(down)AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)?:false; JSONObject().put("ok",ok).put("action",action).put("direction",if(down)"down" else "up") }
        else -> JSONObject().put("ok",false).put("error","Unsupported action. Use back, home, notifications, read_ui, click_text, type_text, tap, or scroll.")
    } } catch(e:Exception){JSONObject().put("ok",false).put("error",e.message?:"Accessibility action failed")}}
    private fun collect(node:AccessibilityNodeInfo?,arr:JSONArray,depth:Int){if(node==null||depth>18||arr.length()>180)return;val t=(node.text?:node.contentDescription)?.toString()?.trim().orEmpty();if(t.isNotBlank())arr.put(JSONObject().put("text",t.take(180)).put("class",node.className?.toString()?:"").put("clickable",node.isClickable).put("editable",node.isEditable));for(i in 0 until node.childCount)collect(node.getChild(i),arr,depth+1)}
    private fun findText(node:AccessibilityNodeInfo?,q:String,depth:Int):AccessibilityNodeInfo?{if(node==null||depth>18||q.isBlank())return null;val t=(node.text?:node.contentDescription)?.toString().orEmpty();if(t.contains(q,true)){if(node.isClickable)return node;var parent=node.parent;var hops=0;while(parent!=null&&hops<5){if(parent.isClickable)return parent;parent=parent.parent;hops++}};for(i in 0 until node.childCount){val n=findText(node.getChild(i),q,depth+1);if(n!=null)return n};return null}
    private fun findFocusedEditable(node:AccessibilityNodeInfo?,depth:Int):AccessibilityNodeInfo?{if(node==null||depth>18)return null;if(node.isFocused&&node.isEditable)return node;for(i in 0 until node.childCount){val n=findFocusedEditable(node.getChild(i),depth+1);if(n!=null)return n};return null}
    private fun findScrollable(node:AccessibilityNodeInfo?,depth:Int):AccessibilityNodeInfo?{if(node==null||depth>18)return null;if(node.isScrollable)return node;for(i in 0 until node.childCount){val n=findScrollable(node.getChild(i),depth+1);if(n!=null)return n};return null}
}
