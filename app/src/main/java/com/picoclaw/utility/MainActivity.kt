package com.picoclaw.utility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PicoClawMainActivity"
        private const val TAG_PERMISSION = "PicoClawPermission"
    }
    
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var serviceStatusCard: View
    private lateinit var enableServiceButton: Button
    private lateinit var inspectElementsButton: Button
    private lateinit var testActionsButton: Button
    private lateinit var featuresInfoText: TextView
    private lateinit var screenStreamCard: View
    private lateinit var openScreenStreamButton: Button
    private lateinit var screenStreamStatusText: TextView
    private lateinit var piperTtsButton: Button
    
    private var isConnected = false
    private var isAccessibilityEnabled = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var piperTtsManager: PiperTtsManager
    
    private val serviceConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "PICOLCLAW_SERVICE_CONNECTED") {
                runOnUiThread {
                    updateUI()
                }
            }
        }
    }
    
    private val accessibilityStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            checkAccessibilityStatus()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize Piper TTS Manager
        piperTtsManager = PiperTtsManager(this)
        
        bindViews()
        setupClickListeners()
        registerReceivers()
        
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
        updateUI()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(serviceConnectionReceiver)
            unregisterReceiver(accessibilityStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        piperTtsManager.stop()
    }
    
    private fun bindViews() {
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        serviceStatusCard = findViewById(R.id.serviceStatusCard)
        enableServiceButton = findViewById(R.id.enableServiceButton)
        inspectElementsButton = findViewById(R.id.inspectElementsButton)
        testActionsButton = findViewById(R.id.testActionsButton)
        featuresInfoText = findViewById(R.id.featuresInfoText)
        screenStreamCard = findViewById(R.id.screenStreamCard)
        openScreenStreamButton = findViewById(R.id.openScreenStreamButton)
        screenStreamStatusText = findViewById(R.id.screenStreamStatusText)
        piperTtsButton = findViewById(R.id.piperTtsButton)
    }
    
    private fun setupClickListeners() {
        connectButton.setOnClickListener {
            toggleConnection()
        }
        
        enableServiceButton.setOnClickListener {
            requestAccessibilityPermission()
        }
        
        inspectElementsButton.setOnClickListener {
            inspectCurrentScreen()
        }
        
        testActionsButton.setOnClickListener {
            showTestActionsDialog()
        }
        
        openScreenStreamButton.setOnClickListener {
            openScreenStreamApp()
        }
        
        piperTtsButton.setOnClickListener {
            testPiperTts()
        }
    }
    
    private fun registerReceivers() {
        val filter = IntentFilter("PICOLCLAW_SERVICE_CONNECTED")
        registerReceiver(serviceConnectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        
        // Monitor accessibility state changes
        val stateFilter = IntentFilter().apply {
            addAction("android.accessibilityservice.AccessibilityService")
        }
        registerReceiver(accessibilityStateReceiver, stateFilter, Context.RECEIVER_NOT_EXPORTED)
    }
    
    private fun checkAccessibilityStatus() {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        
        isAccessibilityEnabled = accessibilityManager.isEnabled &&
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.id.contains("com.picoclaw.utility") }
        
        Log.d(TAG, "Accessibility service enabled: $isAccessibilityEnabled")
    }
    
    private fun toggleConnection() {
        if (!isAccessibilityEnabled) {
            showAccessibilityRequiredDialog()
            return
        }
        
        isConnected = !isConnected
        
        if (isConnected) {
            // Connection established
            ConnectionManager.initialize(this)
            Toast.makeText(this, "PicoClaw connected", Toast.LENGTH_SHORT).show()
        } else {
            // Disconnected
            ConnectionManager.disconnect()
            Toast.makeText(this, "PicoClaw disconnected", Toast.LENGTH_SHORT).show()
        }
        
        updateUI()
    }
    
    private fun updateUI() {
        // Check actual service state
        checkAccessibilityStatus()
        val serviceActive = PicoClawAccessibilityService.isServiceActive
        
        // Update status display
        when {
            serviceActive && isConnected -> {
                statusText.text = "Status: Connected & Ready"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                connectButton.text = "Disconnect"
                connectButton.isEnabled = true
                serviceStatusCard.setBackgroundResource(android.R.color.holo_green_light)
            }
            serviceActive -> {
                statusText.text = "Status: Service Active (Not Connected)"
                statusText.setTextColor(getColor(android.R.color.holo_orange_dark))
                connectButton.text = "Connect"
                connectButton.isEnabled = true
                serviceStatusCard.setBackgroundResource(android.R.color.holo_orange_light)
            }
            isAccessibilityEnabled -> {
                statusText.text = "Status: Permission Granted (Starting Service...)"
                statusText.setTextColor(getColor(android.R.color.holo_blue_dark))
                connectButton.text = "Connect"
                connectButton.isEnabled = true
                serviceStatusCard.setBackgroundResource(android.R.color.holo_blue_light)
            }
            else -> {
                statusText.text = "Status: Service Disabled"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                connectButton.text = "Enable Service First"
                connectButton.isEnabled = false
                serviceStatusCard.setBackgroundResource(android.R.color.holo_red_light)
            }
        }
        
        // Update feature buttons
        val canUseFeatures = serviceActive
        inspectElementsButton.isEnabled = canUseFeatures
        testActionsButton.isEnabled = canUseFeatures
        
        // Update features info
        featuresInfoText.text = buildString {
            appendLine("Features Available:")
            appendLine(if (canUseFeatures) "✓ Tap, Swipe, Long-press gestures" else "✗ Tap, Swipe, Long-press gestures")
            appendLine(if (canUseFeatures) "✓ Screen element detection" else "✗ Screen element detection")
            appendLine(if (canUseFeatures) "✓ Text reading from apps" else "✗ Text reading from apps")
            appendLine(if (canUseFeatures) "✓ Global navigation (Back, Home)" else "✗ Global navigation (Back, Home)")
            appendLine(if (canUseFeatures) "✓ Text input automation" else "✗ Text input automation")
            appendLine(if (canUseFeatures) "✓ Screen streaming (MJPEG)" else "✗ Screen streaming (MJPEG)")
            appendLine(if (piperTtsManager.isReady()) "✓ Piper TTS (offline)" else "✗ Piper TTS (offline)")
        }
    }
    
    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        
        AlertDialog.Builder(this)
            .setTitle("Enable PicoClaw Accessibility Service")
            .setMessage("1. Find \"PicoClaw Remote Control\" in the list\n\n" +
                    "2. Tap it and enable \"Use PicoClaw Remote Control\"\n\n" +
                    "3. Return to this app")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAccessibilityRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service Required")
            .setMessage("PicoClaw needs Accessibility Service permission to control your device. " +
                    "This is similar to how TeamViewer or remote support apps work.")
            .setPositiveButton("Enable") { _, _ ->
                requestAccessibilityPermission()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun inspectCurrentScreen() {
        if (!PicoClawAccessibilityService.isServiceActive) {
            Toast.makeText(this, "Service not active", Toast.LENGTH_SHORT).show()
            return
        }
        
        PicoClawAccessibilityService.getInstance()?.let { service ->
            val elements = service.getClickableElements()
            val hierarchy = service.getCurrentWindowHierarchy()
            
            AlertDialog.Builder(this)
                .setTitle("Screen Elements (${elements.size} clickable)")
                .setMessage("${elements.take(10).joinToString("\n\n") { element ->
                    "${element.text.take(50)}\n  ID: ${element.viewId}\n  Bounds: [${element.bounds}]\n  ${if (element.isClickable) "[CLICKABLE]" else ""}"
                }}${if (elements.size > 10) "\n\n... and ${elements.size - 10} more" else ""}")
                .setPositiveButton("OK", null)
                .setNeutralButton("Show Hierarchy") { _, _ ->
                    showHierarchyDialog(hierarchy)
                }
                .show()
        }
    }
    
    private fun showHierarchyDialog(hierarchy: String) {
        AlertDialog.Builder(this)
            .setTitle("Window Hierarchy")
            .setMessage(hierarchy.take(2000) + if (hierarchy.length > 2000) "\n\n(truncated)" else "")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showTestActionsDialog() {
        val actions = arrayOf("Test Click (Center Screen)", "Test Swipe", "Test Back Button", 
            "Test Home", "Test Long Press", "Find Element by Text")
        
        AlertDialog.Builder(this)
            .setTitle("Test Actions")
            .setItems(actions) { _, which ->
                performTestAction(which)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performTestAction(actionIndex: Int) {
        val service = PicoClawAccessibilityService.getInstance() ?: run {
            Toast.makeText(this, "Service not active", Toast.LENGTH_SHORT).show()
            return
        }
        
        val (width, height) = service.getScreenDimensions()
        
        when (actionIndex) {
            0 -> { // Click center
                service.performClick(width / 2f, height / 2f) { success ->
                    runOnUiThread { Toast.makeText(this, "Click: $success", Toast.LENGTH_SHORT).show() }
                }
            }
            1 -> { // Swipe
                service.performSwipe(width / 2f, height * 0.7f, width / 2f, height * 0.3f) { success ->
                    runOnUiThread { Toast.makeText(this, "Swipe: $success", Toast.LENGTH_SHORT).show() }
                }
            }
            2 -> { // Back
                val success = service.performBack()
                Toast.makeText(this, "Back: $success", Toast.LENGTH_SHORT).show()
            }
            3 -> { // Home
                val success = service.performHome()
                Toast.makeText(this, "Home: $success", Toast.LENGTH_SHORT).show()
            }
            4 -> { // Long press
                service.performLongPress(width / 2f, height / 2f, 1000) { success ->
                    runOnUiThread { Toast.makeText(this, "Long press: $success", Toast.LENGTH_SHORT).show() }
                }
            }
            5 -> { // Find element
                showFindElementDialog()
            }
        }
    }
    
    private fun showFindElementDialog() {
        val input = EditText(this)
        input.hint = "Enter text to find"
        
        AlertDialog.Builder(this)
            .setTitle("Find Element")
            .setView(input)
            .setPositiveButton("Find") { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    findAndShowElement(text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun findAndShowElement(text: String) {
        val service = PicoClawAccessibilityService.getInstance() ?: return
        
        val node = service.findNodeByText(text)
        val bounds = service.getElementBounds(node)
        
        if (bounds != null) {
            AlertDialog.Builder(this)
                .setTitle("Element Found")
                .setMessage("Text: ${node?.text}\nID: ${node?.viewIdResourceName}\nBounds: ${bounds}")
                .setPositiveButton("OK", null)
                .show()
        } else {
            Toast.makeText(this, "Element not found", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ========== SCREEN STREAM ==========
    
    private fun openScreenStreamApp() {
        val packageName = "info.dvkr.screenstream"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
        }
        
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback to browser
            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://f-droid.org/packages/$packageName/")
            }
            try {
                startActivity(browserIntent)
            } catch (e2: Exception) {
                AlertDialog.Builder(this)
                    .setTitle("ScreenStream Not Found")
                    .setMessage("Please install ScreenStream from F-Droid to enable screen streaming.\n\n" +
                            "Download: https://f-droid.org/packages/info.dvkr.screenstream/")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        
        // Show setup instructions
        AlertDialog.Builder(this)
            .setTitle("ScreenStream Setup")
            .setMessage("1. Open ScreenStream app\n\n" +
                    "2. Go to Settings → Stream → Local mode\n\n" +
                    "3. Set Port to 8080 (default)\n\n" +
                    "4. Start streaming\n\n" +
                    "5. Use WebSocket commands:\n" +
                    "   - stream_capture: Get single frame\n" +
                    "   - stream_start: Start continuous streaming\n" +
                    "   - stream_stop: Stop streaming")
            .setPositiveButton("OK", null)
            .show()
    }
    
    // ========== PIPER TTS ==========
    
    private fun testPiperTts() {
        if (!piperTtsManager.isReady()) {
            AlertDialog.Builder(this)
                .setTitle("Piper TTS Not Ready")
                .setMessage("Piper assets are not bundled or failed to extract.\n\n" +
                        "Make sure you have:\n" +
                        "- piper binary in assets/piper/\n" +
                        "- en_US-lessac-medium.onnx model in assets/piper/")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        val testTexts = arrayOf(
            "Hello! This is PicoClaw speaking.",
            "The quick brown fox jumps over the lazy dog.",
            "Testing offline text to speech with Piper."
        )
        
        AlertDialog.Builder(this)
            .setTitle("Test Piper TTS")
            .setItems(testTexts) { _, which ->
                piperTtsManager.speak(testTexts[which]) {
                    runOnUiThread {
                        Toast.makeText(this, "Speech complete", Toast.LENGTH_SHORT).show()
                    }
                }
                Toast.makeText(this, "Playing...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}