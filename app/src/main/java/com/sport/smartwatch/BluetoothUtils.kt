package com.sport.smartwatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*


private const val TAG = "SportsWatch"   // Debugging Tag

// Constants that indicate the current connection state
const val STATE_NONE = 0                // we're doing nothing
const val STATE_LISTEN = 1              // now listening for incoming connections
const val STATE_CONNECTING = 2          // now initiating an outgoing connection
const val STATE_CONNECTED = 3           // now connected to a remote device

// Constants used when transmitting messages between the service and the UI
const val MESSAGE_READ: Int = 0
const val MESSAGE_WRITE: Int = 1
const val MESSAGE_TOAST: Int = 2
const val MESSAGE_STATE_CHANGE: Int = 3
const val MESSAGE_DEVICE_NAME: Int = 4

// Constants used to connect with device
private const val MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"
const val DEVICE_NAME = "SportsWatch"
const val NAME = "SmartWatch"
const val TOAST = ""

@RequiresApi(Build.VERSION_CODES.O)
class BluetoothUtils(private val context: Context, private val handler: Handler) {
    var adapter: BluetoothAdapter? = null
    var socket: BluetoothSocket? = null
    val mHandler = handler

    var manager: CompanionDeviceManager
    val messages: ArrayList<String> = ArrayList()

    private var acceptThread: AcceptThread? = null
    var connectThread : ConnectThread? = null
    var connectedThread : ConnectedThread? = null

    var mState: Int = 0
    var newState: Int = 0

    /**
     * TODO
     */
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
     * TODO
     */
    @Synchronized
    private fun updateUserInterfaceTitle() {
        Log.d(TAG, "updateUserInterfaceTitle() $newState -> $mState")
        newState = mState

        // Give the new state to the Handler so the UI Activity can update
        handler.obtainMessage(MESSAGE_STATE_CHANGE, newState, -1).sendToTarget()
    }

    /**
     * TODO
     */
    @Synchronized
    fun getState(): Int {
        return mState
    }

    /**
     * TODO
     */
    @Synchronized
    open fun start() {
        Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread!!.start()
        }

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * TODO
     */
    @Synchronized
    open fun stop() {
        Log.d(TAG, "stop")

        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        if (acceptThread != null) {
            acceptThread!!.cancel()
            acceptThread = null
        }

        mState = STATE_NONE
        Log.i(TAG, "stop: STATE_NONE")

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * TODO
     */
    @Synchronized
    open fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connect to: $device")

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread!!.cancel()
                connectThread = null
            }
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Start the thread to connect with the given device
        connectThread = ConnectThread(device)
        connectThread!!.start()

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * TODO
     */
    @Synchronized
    open fun connected(socket: BluetoothSocket?, device: BluetoothDevice) {
        Log.d(TAG, "connected")

        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread!!.cancel()
            connectThread = null
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Cancel the accept thread because we only want to connect to one device
//        if (acceptThread != null) {
//            acceptThread!!.cancel()
//            acceptThread = null
//        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = ConnectedThread(socket!!)
        connectedThread!!.start()

        checkPermission()

        // Send the name of the connected device back to the UI Activity
        val message = handler.obtainMessage(MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(DEVICE_NAME, device.name)
        message.data = bundle
        handler.sendMessage(message)

        // Update UI title
        updateUserInterfaceTitle()
    }

    /**
     * TODO
     */
    open fun write(out: ByteArray?) {
        // Create temporary object
        var r: ConnectedThread

        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState != STATE_CONNECTED) return
            r = connectedThread!!
        }

        // Perform the write unsynchronized
        r.write(out!!)
    }

    /**
     * TODO
     */
    private fun connectionFailure() {
        val message = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(TOAST, "Can't connect to device")
        message.data = bundle
        handler.sendMessage(message)

        mState = STATE_NONE
        Log.i(TAG, "connectionFailure: STATE_NONE")
        
        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        this@BluetoothUtils.start()
    }

    /**
     * TODO
     */
    private fun connectionLost() {
        // Send a failure message back to the Activity
        val message: Message = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()

        bundle.putString(TOAST, "Device connection was lost")
        message.data = bundle
        handler.sendMessage(message)

        mState = STATE_NONE

        // Update UI title
        updateUserInterfaceTitle()

        // Start the service over to restart listening mode
        this@BluetoothUtils.start()
    }

    /**
     * TODO
     */
    @SuppressLint("MissingPermission")
    internal inner class AcceptThread : Thread() {
        private var mmServerSocket: BluetoothServerSocket? = null

        init {
            var tmpServerSocket: BluetoothServerSocket? = null

            try {
                tmpServerSocket = adapter?.listenUsingInsecureRfcommWithServiceRecord(NAME,
                    UUID.fromString(MY_UUID))
            } catch (e: Exception) {
                Log.e(TAG, "AcceptThread: listen failed", e)
            }

            mmServerSocket = tmpServerSocket
            mState = STATE_LISTEN
        }

        override fun run() {
            Log.d(TAG, "run: begin AcceptThread")

            // Keep listening until exception occurs or a socket is returned.
            while (mState != STATE_CONNECTED) {
                val socket: BluetoothSocket? = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    null
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized(this@BluetoothUtils) {
                        when (mState) {
                            STATE_LISTEN, STATE_CONNECTING ->       // Situation normal. Start the connected thread.
                                connected(socket, socket.remoteDevice)
                            STATE_NONE, STATE_CONNECTED ->          // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        fun cancel() {
            try {
                Log.d(TAG, "cancel: 3")
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    /**
     * Connect with paired device
     */
    inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null

        init {
            checkPermission()
            var tmpSocket: BluetoothSocket? = null

            try {
                tmpSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(MY_UUID))
                Log.d(TAG, "Connected to service")
            } catch (e: Exception) {
                Log.d(TAG, "Can't connect to service")
            }
            socket = tmpSocket
            mState = STATE_CONNECTING
        }

        override fun run() {
            Log.i(TAG, "run: Begin ConnectThread")
            checkPermission()

            // Cancel discovery because it otherwise slows down the connection.
            adapter?.cancelDiscovery()

            try {
                socket?.connect()
                Log.d(TAG, "Connected to device")
            } catch (connectException: IOException) {
                try {
                    Log.e(TAG, "ConnectThread run: Closed Socket", connectException)
                    Log.d(TAG, "cancel: 4")
                    socket?.close()
                } catch (closeException: IOException) {
                    Log.e(TAG, "ConnectThread run: Can't close socket", closeException)
                    connectionFailure()
                }
                connectionFailure()
                return
            }

            // Reset the ConnectThread
            synchronized (this@BluetoothUtils) {
                connectThread = null
            }

            // Start the connected thread
            connected(socket, device)
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                Log.d(TAG, "cancel: 5")
                socket?.close()
            } catch (cancelException: IOException) {
                Log.e(TAG, "ConnectThread cancel: Could not close the client socket", cancelException)
            }
        }
    }

    /**
     * TODO
     */
    inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private var mmInStream: InputStream = mmSocket.inputStream
        private var mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

        init {
            Log.d(TAG, "create ConnectedThread")
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = mmSocket!!.inputStream
                tmpOut = mmSocket!!.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "temp sockets not created", e)
            }

            mmInStream = tmpIn!!
            mmOutStream = tmpOut!!
            mState = STATE_CONNECTED

            write("*".toByteArray())
        }

        override fun run() {
            Log.d(TAG, "begin ConnectedThread")
            var numBytes: Int // bytes returned from read()
            val mmBuffer = ByteArray(1024) // mmBuffer store for the stream
            val readMessage: StringBuilder = StringBuilder()

            // Keep listening to the InputStream until an exception occurs.
            while (mState == STATE_CONNECTED) {
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
                    connectionLost()
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
                Log.d(TAG, "cancel: 1")
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    /**
     * Check bluetooth permissions
     */
    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(context as Activity,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }
    }

    /**
     * Connect with paired devices
     */
    fun connectDevice() {
        checkPermission()

        // Get paired devices
        val pairedDevices: Set<BluetoothDevice>? = adapter?.bondedDevices    // paired devices

        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address

            Log.d(TAG, "Bonded with $deviceName")

            // Connect with paired device
            Log.d(TAG, "connectDevice: Trying to connect with $deviceName")
            connect(device)
        }
        return
    }
}

