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
import com.github.mikephil.charting.charts.LineChart
import kotlin.collections.ArrayList
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
    private var firstBPM = false
    private var beginExercice: Long = System.currentTimeMillis()

    // Views
    private lateinit var txtCalories: TextView
    private lateinit var txtBPM: TextView
    private lateinit var btnBlue: Button

    // Time variables
    private var timerStarted=false
    private lateinit var serviceIntent:Intent
    private var time=0.0

    //
    private lateinit var stpwtch: TextView
    private lateinit var StartPausebtn: Button
    private lateinit var Resetbtn: Button

    /**
     * When the activity is created
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_display)
        var stpwtch=findViewById<TextView>(R.id.StopWatch)
        val StartPausebtn=findViewById<Button>(R.id.btnSTARTTIMER)
        val Resetbtn=findViewById<Button>(R.id.btnReset)

        // Initialize essential attributes
        setupApp()

        // Check bluetooth permission
        checkPermission()
        //call time function for stopwatch
        time()

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

            // Connect to paired devices
            connectDevice()
        }

        serviceIntent = Intent(applicationContext,TimerService::class.java)
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
                        btnBlue.text = "Connected"
                        btnBlue.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth_connected,
                            0, 0, 0)
                    }
                    STATE_CONNECTING -> {
                        Log.d(TAG, "handleMessage: STATE_CONNECTING to $connectedDeviceName")
                        btnBlue.text = "Connecting"
                        btnBlue.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth_connecting,
                            0, 0, 0)
                    }
                    STATE_LISTEN, STATE_NONE -> {
                        Log.d(TAG, "handleMessage: STATE_LISTEN, STATE_NONE")
                        btnBlue.text = "Connect"
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

                    txtBPM.text = message

                    if (firstBPM == false) {
                        beginExercice = System.currentTimeMillis()
                        firstBPM = true
                    }
                    val currentTime = System.currentTimeMillis()
                    val duration: Long = currentTime - beginExercice
                    val bpm = message.toFloat()

                    val calories = calculateCal(age, weight, gender, bpm, duration)
                    txtCalories.text = "Calories Burned: $calories"
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

        // Bluetooth
        btUtils = BluetoothUtils(this@HeartDisplay, handler)

        // buffer for outgoing messages
        mOutStringBuffer = StringBuffer()

        // Views
        txtBPM = findViewById(R.id.txtBPM)
        txtCalories = findViewById(R.id.txtCalories)
        btnBlue = findViewById(R.id.btnBlue)

        stpwtch = findViewById(R.id.StopWatch)
        StartPausebtn = findViewById(R.id.btnSTARTTIMER)
        Resetbtn = findViewById(R.id.btnReset)
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

    private fun bpmAverage(): Float {
        var somme = 0.0F
        for (item in bpmList) somme += item

        return (somme / bpmList.size)
    }
    private fun time() {


        StartPausebtn.setOnClickListener{
            startStopTimer()}
        Resetbtn.setOnClickListener{
            resetTimer()
        }

        serviceIntent = Intent(applicationContext, TimerService::class.java)
        registerReceiver(updateTime, IntentFilter(TimerService.TIMER_UPDATED))

    }
    private val updateTime: BroadcastReceiver= object : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
           time = intent.getDoubleExtra(TimerService.TIME_EXTRA,0.0)
           stpwtch.setText(getTimeStringFromDouble(time))



        }
    }

    private fun getTimeStringFromDouble(time: Double): String{

        val resultInt=time.roundToInt()
        val minutes = resultInt % 86400 % 3600 /60
        val seconds = resultInt % 86400 % 3600 %60
        if (resultInt<=99){
            val milliseconds = resultInt % 86400
            return makeTimeString(minutes,seconds,milliseconds)
        }
        else {
            val milliseconds= resultInt % 86400 -100
            return makeTimeString(minutes,seconds,milliseconds)}

    }

    private fun makeTimeString(min: Int, sec: Int, mil: Int): String = String.format("%02d:%02d:%02d", min,sec,mil)





    private fun resetTimer(){
        stopTimer()
        time= 0.0
        stpwtch.setText(getTimeStringFromDouble(time))


    }

    private fun startStopTimer(){
        if(timerStarted)
            stopTimer()
        else
            startTimer()

    }

    private fun startTimer() {
        serviceIntent.putExtra(TimerService.TIME_EXTRA,time)
        startService(serviceIntent)
        timerStarted = true
        StartPausebtn.setText("Pause")

    }

    private fun stopTimer() {

        stopService(serviceIntent)
        timerStarted = false
        StartPausebtn.setText("Start")

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

