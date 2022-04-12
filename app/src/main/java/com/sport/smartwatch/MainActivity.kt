package com.sport.smartwatch

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException


private const val TAG = "SportsWatch"   // Used for debugging
private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val ARDUINO = "18:E4:40:00:06"  // Not used
private const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {
    private lateinit var listview:ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val intentHeart = Intent(this,HeartDisplay::class.java)

        listview=findViewById(R.id.lstPerson)
        val txtName=findViewById<TextView>(R.id.txtName)

        val extras = intent.extras

        if (extras!=null){
            val name = extras.getString("name")
            val weight= extras.getString("weight")
            val age= extras.getString("age")
            val gender = extras.getString("gender")

            txtName.setText(name)

            listview.setOnItemClickListener { adapterView, view, i, l ->

                intentHeart.putExtra("name",name)
                intentHeart.putExtra("weight",weight)
                intentHeart.putExtra("age",age)
                intentHeart.putExtra("gender",gender)
                startActivity(intentHeart)
            }





        }






    }}

