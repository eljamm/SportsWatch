package com.sport.smartwatch

import android.Manifest
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.*
import android.provider.SyncStateContract
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


private const val TAG = "SportsWatch"   // Used for debugging
private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val ARDUINO = "18:E4:40:00:06"  // Not used
private const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {
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
        setContentView(R.layout.activity_main)

        // Declare heart display activity intent
        val heartDisplay = Intent(this, HeartDisplay::class.java)

        val btn = findViewById<Button>(R.id.btnSCAN)
        btn.setOnClickListener{
            // Enable bluetooth if it's disabled
            enableBluetooth()

            // Find nearby devices
            findDevices()
        }

        val btnSend = findViewById<Button>(R.id.btnSend)
        btnSend.setOnClickListener {
            if(bluetoothSocket != null) {
                try {
                    val connectedThread = ConnectedThread(bluetoothSocket!!)
                    connectedThread.start()
                    connectedThread.write("Z".toByteArray())
//                    bluetoothSocket?.outputStream?.write(("Z" + "\r\n").toByteArray())
                    Log.d(TAG, "Wrote to output stream")
                } catch (e: IOException) {
                    Log.d(TAG, "Can't write to output stream")
                }
            }
            if (messages.isNotEmpty()) {
                val txtScan = findViewById<TextView>(R.id.txtClickScan)
                txtScan.text = messages.last()
            }
        }
    }

    /**
     * Check bluetooth status and ask the user to enable it if it's disabled
     */
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
            }
        }
    }

    /**
     * Find bluetooth devices and pair with them then start a connection
     */
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

    /**
     * Connect with paired devices
     */
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
        }
    }

    /**
     * Check bluetooth permissions
     */
    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(BLUETOOTH_CONNECT), 1)
            return
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

                        if (ActivityCompat.checkSelfPermission(this, BLUETOOTH_CONNECT
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

    /**
     * Connect with paired device
     */
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

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }

                // Send the obtained bytes to the UI activity.
                val readMsg = handler.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer)
                readMsg.sendToTarget()
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

    inner class BluetoothHandler(): Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            // TODO: WORK IN PROGRESS
//            if (messages.size < 20) {
//                messages.add(msg.obj.toString())
//            } else {
//                messages.removeAt(0)
//                messages.add(msg.obj.toString())
//            }
        }
    }
}