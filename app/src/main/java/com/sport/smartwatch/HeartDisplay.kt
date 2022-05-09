package com.sport.smartwatch
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.os.Bundle
import android.os.Handler
import android.widget.ImageView
import kotlin.math.roundToInt


private const val TAG = "SportsWatch"   // Used for debugging

// Intent request codes
private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val REQUEST_CONNECT_DEVICE = 0
private const val REQUEST_PERMISSION = 1
private const val REQUEST_ENABLE_BT = 2

@RequiresApi(Build.VERSION_CODES.O)
class HeartDisplay : AppCompatActivity() {
    /**
     * Attributes Declaration
     */
    // Bluetooth
    private lateinit var btUtils: BluetoothUtils

    // Device
    private var connectedDeviceName = ""
    private lateinit var mOutStringBuffer: StringBuffer

    // Calorie Calculation
    private var weight = 0.0F
    private var age: Int = 0
    private var gender = "Male"
    private var startCalc = false
    private var beginExercise: Long = System.currentTimeMillis()

    // Timer
    var timeHandler = Handler(Looper.myLooper()!!)
    var millisecondTime: Long = 0
    var startTime: Long = 0
    var timeBuff: Long = 0
    var updateTime = 0L
    var seconds = 0
    var minutes = 0
    var milliSeconds = 0

    // Views
    private lateinit var txtCalories: TextView
    private lateinit var txtBPM: TextView
    private lateinit var btnBlue: Button
    private lateinit var heartImage: ImageView
    private lateinit var txtTimer: TextView
    private lateinit var btnTimer: Button
    private lateinit var btnReset: Button


    /**
     * When the activity is created
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_display)

        // Initialize essential attributes
        setupApp()

        // Check bluetooth permission
        checkPermission()

        //call time function for stopwatch
        //time()

        // Get extras from MainActivity
        val extras = intent.extras
        if (extras != null) {
            weight = extras.getFloat("weight")
            age = extras.getInt("age")
            gender = extras.getString("gender")!!
        }

        btnBlue.setOnClickListener {
            // Enable bluetooth if it's disabled
            enableBluetooth()

            // Find nearby devices
            findDevices()

            // Connect to paired devices
            connectDevice()
        }

        //serviceIntent = Intent(applicationContext,TimerService::class.java)
    }


    /**
     * When the app is resumed in the foreground
     */
    override fun onResume() {
        super.onResume()

        // Cancel all running threads
        try {
            if (btUtils.getState() == STATE_NONE) {
                btUtils.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume: btUtils is null", e)
        }
    }


    /**
     * Before the activity is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()

        // Cancel all running threads
        btUtils.stop()
    }


    /**
     * Process thread messages and execute actions
     */
    private val handler: Handler = object : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    STATE_CONNECTED -> {
                        Log.d(TAG, "handleMessage: STATE_CONNECTED to $connectedDeviceName")
                        btnBlue.text = getString(R.string.connected)
                        btnBlue.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth_connected,
                            0, 0, 0)
                    }
                    STATE_CONNECTING -> {
                        Log.d(TAG, "handleMessage: STATE_CONNECTING to $connectedDeviceName")
                        btnBlue.text = getString(R.string.connecting)
                        btnBlue.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth_connecting,
                            0, 0, 0)
                    }
                    STATE_LISTEN, STATE_NONE -> {
                        Log.d(TAG, "handleMessage: STATE_LISTEN, STATE_NONE")
                        btnBlue.text = getString(R.string.connect)
                        btnBlue.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth_disabled,
                            0, 0, 0)
                    }
                }
                MESSAGE_WRITE -> {
                    // Receive bytes
                    val writeBuf = msg.obj as ByteArray

                    // Convert buffer into a String
                    val writeMessage = String(writeBuf)

                    // Log the data
                    Log.d(TAG, "MESSAGE_WRITE: $writeMessage")
                }
                MESSAGE_READ -> {
                    // Receive message String
                    var message: String = msg.obj as String

                    // Remove newline escape characters from the message
                    message = message
                        .replace("\r", "")
                        .replace("\n", "")

                    Log.d(TAG, "Read: $message")

                    try {
                        if (message.contains("*")) {
                            val chartValues = message.split("*")
                            for (value in chartValues) {
                                if (value.isNotEmpty() && !value.contains(".", ignoreCase = true)) {
                                    Log.d(TAG, "Handling '*' Values")
                                }
                            }
                        } else if (message.contains("#")){
                            val txtBPM: TextView = this@HeartDisplay.findViewById(R.id.txtBPM)
                            val bpm = message.split("#")[1]

                            // Set BPM TextView content
                            txtBPM.text = bpm

                            // Check Max BPM
                            val maxBPM: Int = 222-age
                            val currentBPM = bpm.toFloat().roundToInt()

                            checkMaxBPM(currentBPM, maxBPM)

                            // Calculate Calories
                            if (startCalc) {
                                if (btnTimer.text == "Pause") {
                                    val currentTime = System.currentTimeMillis()
                                    val duration: Long = currentTime - beginExercise
                                    val floatBPM = bpm.toFloat()

                                    val calories = calculateCal(age, weight, gender, floatBPM, duration)
                                    txtCalories.text = getString(R.string.calories_burned, calories)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "handleMessage: Stopped", e)
                    }
                }
                MESSAGE_DEVICE_NAME -> {
                    // save the connected device's name
                    connectedDeviceName = msg.data.getString(DEVICE_NAME)!!
                    Toast.makeText(this@HeartDisplay, "Connected to "
                            + connectedDeviceName, Toast.LENGTH_SHORT).show()
                }
                MESSAGE_TOAST -> {
                    // Display a Toast
                    Toast.makeText(this@HeartDisplay, msg.data.getString(TOAST),
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    /**
     * Initialize defined attributes
     */
    private fun setupApp() {
        Log.d(TAG, "setupApp()")

        // Timer
        txtTimer = findViewById(R.id.StopWatch)
        btnTimer = findViewById(R.id.btnStart)
        btnReset = findViewById(R.id.btnReset)
        heartImage = findViewById(R.id.imgHeart)

        btnTimer.setOnClickListener {
            if(btnTimer.text == "Start"){
                startTime = SystemClock.uptimeMillis()
                timeHandler.postDelayed(runnable, 0)
                btnReset.isEnabled = false
                btnTimer.text = getString(R.string.pause)

                startCalc = true
                beginExercise = System.currentTimeMillis()
            } else if(btnTimer.text == "Pause"){
                timeBuff += millisecondTime
                timeHandler.removeCallbacks(runnable)
                btnReset.isEnabled = true
                btnTimer.text = getString(R.string.start)
            }
        }

        btnReset.setOnClickListener {
            millisecondTime = 0L
            startTime = 0L
            timeBuff = 0L
            updateTime = 0L
            seconds = 0
            minutes = 0
            milliSeconds = 0
            txtTimer.text = getString(R.string.zero_timer)
            startCalc = false
            txtCalories.text = ""
        }

        // Bluetooth
        btUtils = BluetoothUtils(this@HeartDisplay, handler)

        // buffer for outgoing messages
        mOutStringBuffer = StringBuffer()

        // Views
        txtBPM = findViewById(R.id.txtBPM)
        txtCalories = findViewById(R.id.txtCalories)
        btnBlue = findViewById(R.id.btnBlue)
    }


    /**
     * Enable bluetooth if it's disabled
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
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }


    /**
     * Find bluetooth devices and pair with them
     */
    private fun findDevices() {
        if (btUtils.adapter?.isEnabled == true) {
            checkPermission()

            // If there is no bonded device look for devices to pair with
            if (btUtils.adapter?.bondedDevices!!.isEmpty()) {
                // Set filters based on names and supported feature flags (UUIDs).
                // We can use this to only search for specific devices.
                val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
                    //.setNamePattern(Pattern.compile("SportsWatch"))
                    //.addServiceUuid(ParcelUuid(UUID(0x123abcL, -1L)), null)
                    .build()

                // Send a pairing request using the specified filter
                val pairingRequest: AssociationRequest = AssociationRequest.Builder()
                    .addDeviceFilter(deviceFilter)
                    .setSingleDevice(false)  // false: a list of devices, true: only one device
                    .build()

                // When the app tries to pair with a Bluetooth device, show the
                // corresponding dialog box to the user.
                btUtils.manager.associate(pairingRequest,
                    object : CompanionDeviceManager.Callback() {
                        override fun onDeviceFound(chooserLauncher: IntentSender) {
                            // TODO
                            startIntentSenderForResult(chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0)
                        }

                        override fun onFailure(error: CharSequence?) {
                            Log.d(TAG, "Cannot pair with device. <$error>")
                        }
                    }, null)
            }
        }
    }


    /**
     * Connect with paired devices
     */
    private fun connectDevice() {
        checkPermission()

        // Get the list of paired devices
        val pairedDevices: Set<BluetoothDevice>? = btUtils.adapter?.bondedDevices

        // If the list is not empty, try to connect to each device
        if (pairedDevices!!.isNotEmpty()) {
            pairedDevices.forEach { device ->
                val deviceName = device.name
                val deviceHardwareAddress = device.address // MAC address

                Log.d(TAG, "Bonded with $deviceName")

                // Connect with paired device
                Log.d(TAG, "connectDevice: Trying to connect with $deviceName")
                btUtils.connect(device)
            }
        }
    }


    /**
     * TODO
     */
    private fun sendMessage(message: String) {
        // Check that we're actually connected before trying anything
        if (btUtils.getState() != STATE_CONNECTED) {
            Toast.makeText(this@HeartDisplay, R.string.not_connected, Toast.LENGTH_SHORT).show()
            return
        }

        // Check that there's actually something to send
        if (message.isNotEmpty()) {
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
     * Check bluetooth permission status and enable it if's disabled
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ActivityCompat.requestPermissions(
                                this@HeartDisplay,
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                                REQUEST_PERMISSION)
                        }
                    }
                    .setNegativeButton("Cancel") { dialogInterface, _ ->
                        dialogInterface.dismiss()
                    }.create().show()
            } else {
                // First time checking permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ActivityCompat.requestPermissions(this@HeartDisplay,
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_PERMISSION)
                }
            }
        }
        return
    }


    private fun calculateCal(
        age: Int = 0, weight: Float = 0.0F, gender: String = "Male",
        bpm: Float = 0.0F, duration: Long): Float {
        return when (gender) {
            "Female" -> {
                val calories = duration*(0.4472*bpm-0.1263*weight+0.074*age-20.4022)/4.184
                calories.toFloat()
            }
            "Male" -> {
                val calories = duration*(0.6309*bpm-0.1988*weight+0.2017*age-55.0969)/4.184
                calories.toFloat()
            }
            else -> {
                0.0F
            }
        }
    }


    internal fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_CONNECT_DEVICE -> {
                // When DeviceListActivity returns with a device to connect
                if (resultCode == RESULT_OK) {
                    connectDevice()
                }
            }
            REQUEST_ENABLE_BT -> {
                if (requestCode == Activity.RESULT_OK) {
                    setupApp()
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled")
                    Toast.makeText(this@HeartDisplay, R.string.bt_not_enabled_leaving,
                        Toast.LENGTH_SHORT).show()
                    this@HeartDisplay.finish()
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


    private var runnable: Runnable = object : Runnable {
        override fun run() {
            millisecondTime = SystemClock.uptimeMillis() - startTime
            updateTime = timeBuff + millisecondTime
            seconds = (updateTime / 1000).toInt()
            minutes = seconds / 60
            seconds %= 60
            milliSeconds = (updateTime % 1000).toInt()

            val timeFormat: Int = if (minutes > 9) {
                R.string.time1
            } else {
                R.string.time2
            }

            txtTimer.text = getString(timeFormat, minutes.toString(),
                String.format("%02d", seconds),
                String.format("%02d", (milliSeconds/10).toLong()))

            timeHandler.postDelayed(this, 0)
        }
    }


    fun checkMaxBPM(current: Int, max: Int) {
        if(current >= max) {
            heartImage.setImageResource(R.drawable.blackheart)
            txtBPM.setTextColor(Color.RED)

            // Warn the user
            Toast.makeText(applicationContext,"WARNING: Max BPM Reached, " +
                    "slowing down or stopping the exercise is advised."
                , Toast.LENGTH_LONG).show()
        } else {
            heartImage.setImageResource(R.drawable.svg_heart)
            txtBPM.setTextColor(Color.WHITE)
        }
    }
}

