package com.sport.smartwatch

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView

class StatsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val weight=findViewById<EditText>(R.id.edtW)
        val age=findViewById<EditText>(R.id.edtA)
        val gender=findViewById<EditText>(R.id.edtG)
        val duration=findViewById<EditText>(R.id.edtD)
        val calories=findViewById<TextView>(R.id.txtCAL)

        val extras = intent.extras
        if (extras != null) {
            var bpm =intent.getFloatExtra("bpm", 0.0F)

            if(gender.text.toString()=="F"){
                var w = weight.text.toString().toFloat()
                var a = age.text.toString().toFloat()
                var g = gender.text.toString().toFloat()
                var d = duration.text.toString().toFloat()
                val cal = d*(0.4472*bpm-0.1263*w+0.074*a-20.4022)/4.184
                calories.text = cal.toString()
            }
            else if(gender.text.toString()=="M"){
                var w=weight.text.toString().toFloat()
                var a=age.text.toString().toFloat()
                var g=gender.text.toString().toFloat()
                var dur=duration.text.toString().toFloat()
                var cal= dur*(0.6309*bpm-0.1988*w+0.2017*a-55.0969)/4.184
                calories.text = cal.toString()
            }
        }
    }
}