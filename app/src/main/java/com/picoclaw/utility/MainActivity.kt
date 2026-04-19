package com.picoclaw.utility

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    
    private var isConnected = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)
        
        connectButton.setOnClickListener {
            toggleConnection()
        }
        
        updateUI()
    }
    
    private fun toggleConnection() {
        isConnected = !isConnected
        val message = if (isConnected) "Connected to PicoClaw" else "Disconnected"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        updateUI()
    }
    
    private fun updateUI() {
        if (isConnected) {
            statusText.text = "Status: Connected"
            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            connectButton.text = "Disconnect"
        } else {
            statusText.text = "Status: Disconnected"
            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            connectButton.text = "Connect"
        }
    }
}
