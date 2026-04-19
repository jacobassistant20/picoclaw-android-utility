package com.picoclaw.utility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PicoClawAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "PicoClawAccessibility"
        private var instance: PicoClawAccessibilityService? = null
        var isServiceActive = false
            private set
        
        fun getInstance(): PicoClawAccessibilityService? = instance
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Accessibility Service created")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor relevant events for UI changes
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Window changed - could notify clients
                }
                else -> {}
            }
        }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceActive = true
        Log.d(TAG, "Accessibility Service connected and active")
        
        // Send broadcast that service is ready
        sendBroadcast(Intent("PICOLCLAW_SERVICE_CONNECTED"))
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        isServiceActive = false
        return super.onUnbind(intent)
    }
    
    // ============================================
    // CLICK OPERATIONS
    // ============================================
    
    fun performClick(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (!isServiceActive) {
            callback?.invoke(false)
            return false
        }
        
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
        
        Log.d(TAG, "Click at ($x, $y) dispatched: $result")
        return result
    }
    
    fun performClick(node: AccessibilityNodeInfo?): Boolean {
        return node?.let {
            val result = it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Node click performed: $result")
            result
        } ?: false
    }
    
    // ============================================
    // GESTURE OPERATIONS
    // ============================================
    
    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, 
                     duration: Long = 500, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (!isServiceActive) {
            callback?.invoke(false)
            return false
        }
        
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val stroke = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            GestureDescription.StrokeDescription(path, 0, duration, true)
        } else {
            @Suppress("DEPRECATION")
            GestureDescription.StrokeDescription(path, 0, duration)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
        
        Log.d(TAG, "Swipe from ($startX, $startY) to ($endX, $endY) dispatched: $result")
        return result
    }
    
    fun performLongPress(x: Float, y: Float, duration: Long = 800, 
                         callback: ((Boolean) -> Unit)? = null): Boolean {
        if (!isServiceActive) {
            callback?.invoke(false)
            return false
        }
        
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1, y + 1) // Minimal movement
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
        
        Log.d(TAG, "Long press at ($x, $y) dispatched: $result")
        return result
    }
    
    // ============================================
    // SCREEN READING (TeamViewer-like)
    // ============================================
    
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }
    
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeRecursively(root) { node ->
            node.text?.toString()?.contains(text, ignoreCase = true) == true
        }
    }
    
    fun findNodeById(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeRecursively(root) { node ->
            node.viewIdResourceName == viewId
        }
    }
    
    fun findNodesByContentDescription(description: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesRecursively(root, results) { node ->
            node.contentDescription?.toString()?.contains(description, ignoreCase = true) == true ||
            node.text?.toString()?.contains(description, ignoreCase = true) == true
        }
        return results
    }
    
    fun getCurrentWindowHierarchy(): String {
        val root = rootInActiveWindow ?: return "No active window"
        val sb = StringBuilder()
        buildHierarchyString(root, sb, 0)
        return sb.toString()
    }
    
    fun getClickableElements(): List<ElementInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<ElementInfo>()
        findClickableElements(root, elements)
        return elements
    }
    
    fun getElementBounds(node: AccessibilityNodeInfo?): Rect? {
        return node?.let {
            val rect = Rect()
            it.getBoundsInScreen(rect)
            rect
        }
    }
    
    // ============================================
    // TEXT INPUT
    // ============================================
    
    fun setNodeText(node: AccessibilityNodeInfo?, text: String): Boolean {
        return node?.let {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } ?: false
    }
    
    // ============================================
    // NAVIGATION
    // ============================================
    
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
    
    fun performNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }
    
    fun performQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }
    
    fun performPowerDialog(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
    }
    
    // ============================================
    // SCREENSHOT (via MediaProjection API - requires activity)
    // ============================================
    
    fun getScreenDimensions(): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }
    
    // ============================================
    // PRIVATE HELPERS
    // ============================================
    
    private fun findNodeRecursively(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = findNodeRecursively(child, predicate)
            if (found != null) return found
        }
        return null
    }
    
    private fun findNodesRecursively(node: AccessibilityNodeInfo?, results: MutableList<AccessibilityNodeInfo>, 
                                      predicate: (AccessibilityNodeInfo) -> Boolean) {
        if (node == null) return
        if (predicate(node)) results.add(node)
        
        for (i in 0 until node.childCount) {
            findNodesRecursively(node.getChild(i), results, predicate)
        }
    }
    
    private fun findClickableElements(node: AccessibilityNodeInfo?, elements: MutableList<ElementInfo>) {
        if (node == null) return
        
        val rect = Rect()
        node.getBoundsInScreen(rect)
        
        if (node.isClickable) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            elements.add(ElementInfo(
                text = text,
                viewId = node.viewIdResourceName ?: "",
                bounds = rect,
                className = node.className?.toString() ?: "",
                isClickable = node.isClickable,
                isScrollable = node.isScrollable
            ))
        }
        
        for (i in 0 until node.childCount) {
            findClickableElements(node.getChild(i), elements)
        }
    }
    
    private fun buildHierarchyString(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        
        sb.append("${indent}${node.className?.split('.')?.last() ?: "Unknown"}")
        if (node.text?.isNotEmpty() == true) {
            sb.append(" text=\"${node.text}\"")
        }
        if (node.isClickable) sb.append(" [clickable]")
        if (node.isScrollable) sb.append(" [scrollable]")
        sb.append(" bounds=[${rect.left},${rect.top}-${rect.right},${rect.bottom}]")
        sb.append("\n")
        
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { buildHierarchyString(it, sb, depth + 1) }
        }
    }
}

// Data class for element information
data class ElementInfo(
    val text: String,
    val viewId: String,
    val bounds: Rect,
    val className: String,
    val isClickable: Boolean,
    val isScrollable: Boolean
) {
    fun getCenterX(): Int = bounds.centerX()
    fun getCenterY(): Int = bounds.centerY()
}
