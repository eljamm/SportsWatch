package com.sport.smartwatch

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {
    var btn= findViewById<Button>(R.id.btnSCAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btn.setOnClickListener{
             intent = Intent(this,HeartDisplay::class.java)
             startActivity(intent)
        }
    }
}