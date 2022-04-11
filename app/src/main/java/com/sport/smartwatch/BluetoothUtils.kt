package com.sport.smartwatch

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


private const val TAG = "SportsWatch"   // Used for debugging
private const val SELECT_DEVICE_REQUEST_CODE = 0
private const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"

@RequiresApi(Build.VERSION_CODES.O)
class BluetoothUtils(private val context: Context) {
    var adapter: BluetoothAdapter? = null
    var socket: BluetoothSocket? = null
    var manager: CompanionDeviceManager
    var handler = BluetoothHandler()
    val messages: ArrayList<String> = ArrayList()

    init {
        // Define and Initialize the bluetooth adapter
        adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // SDK 31 and higher
            val bluetoothManager =
                context.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } else {
            // Legacy Devices
            BluetoothAdapter.getDefaultAdapter()
        }

        // Used in device pairing
        val deviceManager: CompanionDeviceManager by lazy {
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        }
        manager = deviceManager
    }

    /**
     * Check bluetooth permissions
     */
    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(context as Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1)
            return
        }
    }

    /**
     * Connect with paired devices
     */
    fun connectDevice() {
        checkPermission()

        // Get paired device
        val pairedDevices: Set<BluetoothDevice>? = adapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
            //Toast.makeText(context, "Bound with $deviceName", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Bound with $deviceName")

            // Connect with paired device
            val connectThread = ConnectThread(device)
            connectThread.start()
        }
    }

    /**
     * Connect with paired device
     */
    inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val device = device
        private var mmSocket: BluetoothSocket? = null

        init {
            if (mmSocket == null) {
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
            adapter?.cancelDiscovery()

            try {
                mmSocket?.connect()
                Log.d(TAG, "Connected to device")
            } catch (connectException: IOException) {
                try {
                    mmSocket?.close()
                    Log.d(TAG, "Closed socket")
//                    Log.d(TAG, "Trying fallback")
//
//                    val clazz: Class<*> = mmSocket?.remoteDevice!!.javaClass
//                    val paramTypes = arrayOf<Class<*>>(Integer.TYPE)
//
//                    val m: Method = clazz.getMethod("createRfcommSocket", *paramTypes)
//                    val params = arrayOf<Any>(Integer.valueOf(1))
//
//                    val fallbackSocket = m.invoke(mmSocket?.remoteDevice, params) as BluetoothSocket
//                    fallbackSocket.connect()

                } catch (closeException: IOException) {
                    Log.d(TAG, "Can't close socket")
                }
                return
            }
            socket = mmSocket
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

    /**
     * TODO
     */
    inner class BluetoothHandler() : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            try {
                Log.d(TAG, "Read from input stream")
                when (msg.what) {
                    MESSAGE_READ -> {
                        var message: String = msg.obj as String
                        message = message.replace("\r", "").replace("\n", "")
                        messages.add(message)
                        Log.d(TAG, "Read $message")

                        val txtBPM: TextView =
                            (context as Activity).findViewById(R.id.txtBPM)

                        txtBPM.text = message
                    }

                    MESSAGE_TOAST -> {

                    }

                    MESSAGE_WRITE -> {

                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not read value")
            }
        }
    }

    /**
     * TODO
     */
    inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        override fun run() {
            var numBytes: Int // bytes returned from read()
            var readMessage: StringBuilder = StringBuilder()
            //val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                // Read from the InputStream.
                try {
                    numBytes = mmInStream.read(mmBuffer)
                    val read = String(mmBuffer, 0, numBytes)
                    readMessage.append(read)

                    Log.d(TAG, "TIME: ${readMessage.toString()}")

                    if (read.contains("\n", ignoreCase = true)) {
                        handler.obtainMessage(MESSAGE_READ, numBytes, -1, readMessage.toString())
                            .sendToTarget()
                        readMessage.setLength(0)
                    }
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)

                    val btnBlue: ImageButton =
                        (context as Activity).findViewById(R.id.btnBlue)

                    btnBlue.setImageResource(R.drawable.ic_bluetooth_disabled)

//                    try {
//                        Log.d(TAG, "Trying to reconnect", e)
//                        connectDevice()
//
//                        checkPermission()
//                        socket!!.connect()
//
//                        try {
//                            val connectedThread = ConnectedThread(socket!!)
//                            connectedThread.start()
//                            connectedThread.write("*".toByteArray())
//                            Log.d(TAG, "Wrote to output stream")
//                        } catch (e: IOException) {
//                            Log.d(TAG, "Can't write to output stream")
//                        }
//                    } catch (e: IOException) {
//                        Log.d(TAG, "Can't connect to socket")
//                    }

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

