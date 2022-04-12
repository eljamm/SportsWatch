package com.sport.smartwatch

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi

class StatsActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        val name=findViewById<EditText>(R.id.edtN)
        val weight=findViewById<EditText>(R.id.edtW)
        val age=findViewById<EditText>(R.id.edtA)
        val gender=findViewById<EditText>(R.id.edtG)
        val submit=findViewById<Button>(R.id.btnSUBMIT)

        submit.setOnClickListener{
            val intent=Intent(this,MainActivity::class.java)
            intent.putExtra("name",name.text)
            intent.putExtra("weight",weight.text)
            intent.putExtra("age",age.text)
            intent.putExtra("gender",gender.text)
            startActivity(intent)



        }





    }
}