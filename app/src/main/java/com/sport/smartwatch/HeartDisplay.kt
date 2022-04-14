package com.sport.smartwatch

import android.Manifest
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
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.mikephil.charting.charts.LineChart
import java.io.IOException

private const val TAG = "SportsWatch"   // Used for debugging
private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val ARDUINO = "18:E4:40:00:06"  // Not used
private const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"

@RequiresApi(Build.VERSION_CODES.O)
class HeartDisplay : AppCompatActivity() {
    private lateinit var btUtils: BluetoothUtils
    private lateinit var chUtils: ChartUtils
    private lateinit var chart: ChartUtils.Chart
    private val bpmList= ArrayList<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_display)




        btUtils = BluetoothUtils(this)
        chUtils = ChartUtils()
        val txtname=findViewById<TextView>(R.id.txtT)
        val txtBPM = findViewById<TextView>(R.id.txtBPM)

        val btnBlue = findViewById<ImageButton>(R.id.btnBlue)
        var weight=0.0F
        var age=0.0F
        var gender="Male"


        val extras= intent.extras

        if(extras!=null){

            txtname.setText(extras.getString("name"))
            weight = extras.getString("Weight").toString().toFloat()
            age = extras.getString("age").toString().toFloat()
            gender = extras.getString("gender").toString() }


        btnBlue.setOnClickListener {
            // Enable bluetooth if it's disabled
            enableBluetooth()

            // Find nearby devices
            if (btUtils.socket == null) {
                findDevices()
            } else {
                try {
                    checkPermission()
                    btUtils.socket!!.connect()

                    try {
                        val connectedThread = btUtils.ConnectedThread(btUtils.socket!!)
                        connectedThread.start()
                        connectedThread.write("*".toByteArray())
                        Log.d(TAG, "Wrote to output stream")
                    } catch (e: IOException) {
                        Log.d(TAG, "Can't write to output stream")
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Can't connect to socket")
                }
            }

            // Set activity resources
            btnBlue.setImageResource(R.drawable.ic_bluetooth_connected)

            txtBPM.visibility = View.VISIBLE
        }

        chart = chUtils.Chart(findViewById<LineChart>(R.id.chart))
        chart.init()
        chart.update()

        var b = txtBPM.text.toString().toFloat()

        bpmList.add(b)
        var somme = 0.0
        for (item in bpmList){
            somme=somme+item
        }
        val bpmAverage=(somme/bpmList.size).toFloat()

        val calories=calculateCal(age,weight,gender,bpmAverage)
    }

    /**
     * Check bluetooth status and ask the user to enable it if it's disabled
     */
    private fun enableBluetooth() {
        // Inform the user that the device is unsupported
        if (btUtils.adapter == null) {
            Toast.makeText(this, "Sorry, your device doesn't support Bluetooth", Toast.LENGTH_LONG)
                .show()
        }

        // Ask the user to enable bluetooth if it's disabled
        if (btUtils.adapter?.isEnabled == false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // SDK 31 and higher
                requestMultiplePermissions.launch(arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT))
            } else {
                // Legacy Devices
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestBluetooth.launch(enableBtIntent)
            }
        }
    }

    /**
     * Find bluetooth devices and pair with them then start a connection
     */
    private fun findDevices() {
        if (btUtils.adapter?.isEnabled == true) {
            checkPermission()

            // If there is no bonded device look for devices to pair with
            if (btUtils.adapter?.bondedDevices!!.isEmpty()) {
                // Sets filters based on names and supported feature flags (UUIDs)
                val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
                    //.setNamePattern(Pattern.compile("SportsWatch"))
                    //.addServiceUuid(ParcelUuid(UUID(0x123abcL, -1L)), null)
                    .build()

                // The argument provided in setSingleDevice() determines whether a single
                // device name or a list of them appears.
                val pairingRequest: AssociationRequest = AssociationRequest.Builder()
                    .addDeviceFilter(deviceFilter)
                    .setSingleDevice(false)  // false: a list of devices appears
                    .build()

                // When the app tries to pair with a Bluetooth device, show the
                // corresponding dialog box to the user.
                btUtils.manager.associate(pairingRequest,
                    object : CompanionDeviceManager.Callback() {
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            startIntentSenderForResult(chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0)
                        }

                        override fun onFailure(error: CharSequence?) {
                            Log.d(TAG, "Cannot pair with device. <$error>")
                        }
                    }, null)
            }

            // Connect to device if there isn't a current connection
            if (btUtils.socket == null) {
                btUtils.connectDevice()
            } else {
                val pairedDevices: Set<BluetoothDevice>? = btUtils.adapter?.bondedDevices
                pairedDevices?.forEach { device ->
                    val deviceName = device.name
                    Toast.makeText(this, "Already connected to $deviceName", Toast.LENGTH_LONG)
                        .show()
                    btUtils.connectDevice()
                }
            }
        }
    }

    /**
     * Check bluetooth permissions
     */
    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1)
            return
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SELECT_DEVICE_REQUEST_CODE -> when (resultCode) {
                Activity.RESULT_OK -> {
                    // The user chose to pair the app with a Bluetooth device.
                    val deviceToPair: BluetoothDevice? =
                        data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
                    deviceToPair?.let { device ->
                        // Continue to interact with the paired device.
                        device.createBond()

                        if (ActivityCompat.checkSelfPermission(this,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(this,
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
                            return
                        }
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun calculateCal(age:Float = 0.0F,weight:Float =0.0F,gender:String="Male",bpmAverage:Float =0.0F): Float {
        val duration = 45
        return when (gender) {
            "Female" -> {
                val calories = duration*(0.4472*bpmAverage-0.1263*weight+0.074*age-20.4022)/4.184
                calories.toFloat()
            }
            "Male" -> {
                val calories =duration*(0.6309*bpmAverage-0.1988*weight+0.2017*age-55.0969)/4.184
                calories.toFloat()
            }
            else -> {
                0.0F
            }
        }
    }

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Permission Granted
            } else {
                // Permission Denied
            }
        }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(TAG, "${it.key} = ${it.value}")
            }
        }
}

