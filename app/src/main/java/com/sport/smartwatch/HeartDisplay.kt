package com.sport.smartwatch

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.*
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.*
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
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList

private const val TAG = "SportsWatch"   // Used for debugging
private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val ARDUINO = "18:E4:40:00:06"  // Not used
private const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"

@RequiresApi(Build.VERSION_CODES.O)
class HeartDisplay : AppCompatActivity() {
    var BPM=findViewById<TextView>(R.id.txtBPM)


    val statbtn=findViewById<Button>(R.id.btnStats)
    val imgbtn = findViewById<ImageButton>(R.id.btn)
    var bluetoothSocket: BluetoothSocket? = null
    var handler = BluetoothHandler()
    private val messages: ArrayList<String> = ArrayList()

    // Define and Initialize the bluetooth adapter
    private val bluetoothAdapter: BluetoothAdapter? = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // SDK 31 and higher
        val bluetoothManager = this.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    } else {
        // Legacy Devices
        BluetoothAdapter.getDefaultAdapter()
    }

    // Used in device pairing
    private val deviceManager: CompanionDeviceManager by lazy {
        getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_display)
        BPM.visibility=View.GONE
        imgbtn.setOnClickListener{
            // Enable bluetooth if it's disabled
            enableBluetooth()

            // Find nearby devices
            findDevices()
            BPM.visibility=View.VISIBLE

        }

        val chart = Chart(findViewById<LineChart>(R.id.chart))
        chart.init()
        chart.update()
        statbtn.setOnClickListener{
            val intent=Intent(this,StatsActivity::class.java)
            var b=BPM.text.toString().toFloat()
            intent.putExtra("bpm",b)
            startActivity(intent)

        }

    }
    private fun enableBluetooth() {
        // Inform the user that the device is unsupported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Sorry, your device doesn't support Bluetooth", Toast.LENGTH_LONG).show()
        }

        // Ask the user to enable bluetooth if it's disabled
        if (bluetoothAdapter?.isEnabled == false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // SDK 31 and higher
                requestMultiplePermissions.launch(arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT))
            }
            else{
                // Legacy Devices
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                requestBluetooth.launch(enableBtIntent)
                //change image to bluetooth static

            }
        }
    }
    private fun connectDevice() {
        checkPermission()

        // Get paired device
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            Toast.makeText(this, "Bound with $deviceName", Toast.LENGTH_LONG).show()

            // Connect with paired device
            val connectThread = ConnectThread(device)
            connectThread.start()
            //change image to bluetooth on
            imgbtn.setImageResource(R.drawable.ic_bluetooth_connected)
        }
    }
    inner class ConnectThread(device: BluetoothDevice) : Thread() {
        var mmSocket: BluetoothSocket? = null

        init {
            if (mmSocket == null)  {
                checkPermission()
                mmSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID))
                Log.d(TAG, "Connected to service")
            } else {
                Log.d(TAG, "Can't connect to service")
            }
        }

        public override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            checkPermission()
            bluetoothAdapter?.cancelDiscovery()

            try {
                mmSocket?.connect()
                Log.d(TAG, "Connected to device")
            } catch (connectException: IOException) {
                try {
                    mmSocket?.close()
                    Log.d(TAG, "Closed socket")
                } catch (closeException: IOException) {
                    Log.d(TAG, "Can't close socket")
                }
                return
            }
            bluetoothSocket = mmSocket
        }
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SELECT_DEVICE_REQUEST_CODE -> when(resultCode) {
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
    private var requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }
    }

    private fun findDevices() {
        if (bluetoothAdapter?.isEnabled == true) {
            checkPermission()

            // If there is no bonded device look for devices to pair with
            if (bluetoothAdapter?.bondedDevices!!.isEmpty()) {
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
                deviceManager.associate(pairingRequest,
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
            if (bluetoothSocket == null) {
                connectDevice()
            } else {
                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
                pairedDevices?.forEach { device ->
                    val deviceName = device.name
                    Toast.makeText(this, "Already connected to $deviceName", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    inner class BluetoothHandler(): Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            try {
                Log.d(TAG, "Read from input stream")
                when(msg.what) {
                    MESSAGE_READ -> {
                        var message: String = msg.obj as String
                        message = message.replace("\r", "").replace("\n", "")
                        messages.add(message)
                        Log.d(TAG, "Read $message")
                    }

                    MESSAGE_TOAST -> {

                    }

                    MESSAGE_WRITE -> {

                    }
                }
            } catch (e: Exception) {
                Log.e(TAG,"Could not read value")
            }
        }
    }
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()
            var readMessage: StringBuilder = StringBuilder()
            val mmBuffer: ByteArray = ByteArray(512) // mmBuffer store for the stream

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                try {
                    numBytes = mmInStream.read(mmBuffer)
                    val read = String(mmBuffer, 0, numBytes)
                    readMessage.append(read)

                    Log.d(TAG, "TIME: ${readMessage.toString()}")

                    if (read.contains("\n", ignoreCase = true)) {
                        handler.obtainMessage(MESSAGE_READ, numBytes, -1, readMessage.toString()).sendToTarget()
                        readMessage.setLength(0)
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)

                // Send a failure message back to the activity.
                val writeErrorMsg = handler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply {
                    putString("toast", "Couldn't send data to the other device")
                }
                writeErrorMsg.data = bundle
                handler.sendMessage(writeErrorMsg)
                return
            }

            // Share the sent message with the UI activity.
            val writtenMsg = handler.obtainMessage(
                MESSAGE_WRITE, -1, -1, mmBuffer)
            writtenMsg.sendToTarget()
        }

        // Call this method from the main activity to shut down the connection.
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }
}

class Chart(private val linechart: LineChart) {
    private val data = ChartData(20)
    private val entries: ArrayList<Entry> = ArrayList()

    private fun setup(entries: ArrayList<Entry>) {
        val lineDataSet = LineDataSet(entries, "")

        lineDataSet.lineWidth = 1.75f
        lineDataSet.circleRadius = 5f
        lineDataSet.circleHoleRadius = 2.5f
        lineDataSet.color = Color.BLUE
        lineDataSet.setCircleColor(Color.BLUE)
        lineDataSet.highLightColor = Color.BLUE
        lineDataSet.setDrawValues(false)

        val lineData = LineData(lineDataSet)

        linechart.data = lineData
        linechart.invalidate()
    }

    fun init() {
        linechart.setNoDataText("Waiting for data")
        linechart.description.isEnabled = false
        linechart.setDrawGridBackground(false)
        linechart.setDrawBorders(false)
        linechart.axisLeft.isEnabled = true
        linechart.axisLeft.spaceTop = 40F
        linechart.axisLeft.spaceBottom = 40F
        linechart.axisRight.isEnabled = false
        linechart.xAxis.isEnabled = false
        linechart.setDrawMarkers(false)
        linechart.legend.isEnabled = false

//        data.init()
        if (data.array.size != 0) {
            for (element in data.array) {
                entries.add(Entry(element.x.toFloat(), element.y.toFloat()))
            }
        }

        setup(entries)
    }

    fun update() {
        data.update()
        entries.removeAll(entries)

        for (element in data.array) {
            entries.add(Entry(element.x.toFloat(), element.y.toFloat()))
        }

        setup(entries)
    }
}

class ChartData(limit: Int = 10) {
    private val maxNumber: Int = limit
    val array: ArrayList<Point> = ArrayList(maxNumber)
    var index: Int = 0

    fun init() {
        repeat(maxNumber) {
            incrementIndex()
            array.addAll(listOf(Point(index,0)))
        }
    }

    fun update() {
        if (index<maxNumber) {
            incrementIndex()
            randomData(1)
        } else {
            index = maxNumber
            for (element in array) { element.x -= 1; }
            array.removeAt(0)
            randomData(1)
        }
    }

    fun addData(number: Int) {
        incrementIndex()
        array.add(Point(index, number))
    }

    fun randomData(times: Int) {
        repeat(times) {
            incrementIndex()
            val number = (70..120).random()
            array.add(Point(index, number))
        }
    }

    private fun incrementIndex() { if (index>maxNumber) { index = 0 } else { index++ } }
}