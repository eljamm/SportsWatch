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
import android.os.*
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

private const val TAG = "SportsWatch"   // Used for debugging

// Intent request codes
private const val REQUEST_CONNECT_DEVICE = 0
private const val REQUEST_PERMISSION = 1
private const val REQUEST_ENABLE_BT = 2

//
private const val SELECT_DEVICE_REQUEST_CODE = 0

@RequiresApi(Build.VERSION_CODES.O)
class HeartDisplay : AppCompatActivity() {
    // Bluetooth
    //private val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
    //private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private lateinit var btUtils: BluetoothUtils

    // Chart
    private lateinit var chUtils: ChartUtils
    private lateinit var chart: ChartUtils.Chart

    // Device
    private var connectedDeviceName = ""
    private lateinit var mOutStringBuffer: StringBuffer

    // Calorie Calculation
    private val bpmList = ArrayList<Float>()
    private var weight = 0.0F
    private var age = 0.0F
    private var gender = "Male"

    // Views
    private lateinit var txtCalories: TextView
    private lateinit var txtBPM: TextView
    private lateinit var btnBlue: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_display)

        setupApp()
        checkPermission()

        // Get extras from MainActivity
        val extras = intent.extras
        if (extras != null) {
            weight = extras.getFloat("weight")
            age = extras.getFloat("age")
            gender = extras.getString("gender")!!
        }

        btnBlue.setOnClickListener {
            // Enable bluetooth if it's disabled
            enableBluetooth()

            // Find nearby devices
            findDevices()

            // Set activity resources
            btnBlue.setImageResource(R.drawable.ic_bluetooth_connected)
            
            try {
                btUtils.write("*".toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "onCreate: why", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        try {
            if (btUtils.getState() == STATE_NONE) {
                btUtils.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume: btUtils is null", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        btUtils.stop()
    }

    /**
     * TODO
     */
    private val handler: Handler = object : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    STATE_CONNECTED -> {
                        Log.d(TAG, "handleMessage: STATE_CONNECTED to $connectedDeviceName")
                    }
                    STATE_CONNECTING -> {
                        Log.d(TAG, "handleMessage: STATE_CONNECTING to $connectedDeviceName")
                    }
                    STATE_LISTEN, STATE_NONE -> {
                        Log.d(TAG, "handleMessage: STATE_LISTEN, STATE_NONE")
                    }
                }
                MESSAGE_WRITE -> {
                    val writeBuf = msg.obj as ByteArray
                    // construct a string from the buffer
                    val writeMessage = String(writeBuf)
                    Log.d(TAG, "MESSAGE_WRITE: $writeMessage")
                }
                MESSAGE_READ -> {
                    var message: String = msg.obj as String
                    message = message.replace("\r", "").replace("\n", "")
                    Log.d(TAG, "Read: $message")

                    //val txtBPM: TextView = (this as Activity).findViewById(R.id.txtBPM)
                    //txtBPM.text = message
                }
                MESSAGE_DEVICE_NAME -> {
                    // save the connected device's name
                    connectedDeviceName = msg.data.getString(DEVICE_NAME)!!
                    Toast.makeText(this@HeartDisplay, "Connected to "
                            + connectedDeviceName, Toast.LENGTH_SHORT).show()
                }
                MESSAGE_TOAST -> Toast.makeText(this@HeartDisplay, msg.data.getString(TOAST),
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * TODO
     */
    private fun setupApp() {
        Log.d(TAG, "setupApp()")

        // Bluetooth
        btUtils = BluetoothUtils(this@HeartDisplay, handler)

        // buffer for outgoing messages
        mOutStringBuffer = StringBuffer()

        // Chart
        chUtils = ChartUtils()

        chart = chUtils.Chart(findViewById(R.id.chart))
        chart.init()
        //chart.update()

        txtBPM = findViewById(R.id.txtBPM)
        txtCalories = findViewById(R.id.txtCalories)
        btnBlue = findViewById(R.id.btnBlue)
    }

    /**
     * Check bluetooth status and ask the user to enable it if it's disabled
     */
    private fun enableBluetooth() {
        Log.d(TAG, "enableBluetooth()")

        // Device does not support bluetooth
        if (btUtils.adapter == null) {
            Log.d(TAG, "enableBluetooth: Device does not support Bluetooth")

            // Inform the user that the device is unsupported
            Toast.makeText(this@HeartDisplay,
                "Sorry, your device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
            return
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
                
                try {
                    btUtils.connectDevice()
                    sendMessage("*")
                } catch (e: Exception) {
                    Log.d(TAG, "findDevices: couldn't connect to device")
                }
            } else {
                val pairedDevices: Set<BluetoothDevice>? = btUtils.adapter?.bondedDevices
                pairedDevices?.forEach { device ->
                    try {
                        btUtils.connect(device)
                        sendMessage("*")
                    } catch (e: Exception) {
                        Log.e(TAG, "findDevices: can't connect to bonded device", e)
                    }
                }
            }
        }
    }

    /**
     * TODO
     */
    private fun sendMessage(message: String) {
        // Check that we're actually connected before trying anything
        if (btUtils.getState() !== STATE_CONNECTED) {
            Toast.makeText(this@HeartDisplay, R.string.not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        // Check that there's actually something to send
        if (message.length > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            val send = message.toByteArray()
            btUtils.write(send)

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0)
            //mOutEditText.setText(mOutStringBuffer)
            Log.d(TAG, "sendMessage: $mOutStringBuffer")
        }
    }

    /**
     * Check bluetooth permissions
     */
    private fun checkPermission() {
        // If Bluetooth permissions are not granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If user has already denied the permission once
            if (ActivityCompat.shouldShowRequestPermissionRationale(this@HeartDisplay,
                    Manifest.permission.BLUETOOTH_CONNECT)) {
                // Explain to user why the permission is necessary
                AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("Bluetooth permission is needed to connect to devices.")
                    .setPositiveButton("OK") { _, _ ->      // Directly call OnClickListener
                        ActivityCompat.requestPermissions(
                            this@HeartDisplay,
                            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                            REQUEST_PERMISSION)
                    }
                    .setNegativeButton("Cancel") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                    }.create().show()
            } else {
                // First time checking permission
                ActivityCompat.requestPermissions(this@HeartDisplay,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_PERMISSION)
            }
        }
        return
    }

    private fun calculateCal(
        age: Float = 0.0F, weight: Float = 0.0F, gender: String = "Male",
        bpmAverage: Float = 0.0F,
    ): Float {
        val duration = 45

        return when (gender) {
            "Female" -> {
                val calories = duration*(0.4472*bpmAverage-0.1263*weight+0.074*age-20.4022)/4.184
                calories.toFloat()
            }
            "Male" -> {
                val calories = duration*(0.6309*bpmAverage-0.1988*weight+0.2017*age-55.0969)/4.184
                calories.toFloat()
            }
            else -> {
                0.0F
            }
        }
    }

    private fun bpmAverage(): Float {
        var somme = 0.0F
        for (item in bpmList) somme += item

        return (somme / bpmList.size)
    }

    internal fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_CONNECT_DEVICE -> {
                // When DeviceListActivity returns with a device to connect
                if (resultCode == RESULT_OK) {
                    btUtils.connectDevice()
                }
            }
            REQUEST_ENABLE_BT -> {
                if (requestCode == Activity.RESULT_OK) {
                    setupApp()
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled")
                    if (this@HeartDisplay != null) {
                        Toast.makeText(this@HeartDisplay, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show()
                        this@HeartDisplay.finish()
                    }
                }
            }
        }
    }

    private var requestBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Permission Granted")
            } else {
                Log.d(TAG, "Permission Denied")
            }
        }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions -> permissions.entries.forEach {
                Log.d(TAG, "${it.key} = ${it.value}")
            }
        }
}

