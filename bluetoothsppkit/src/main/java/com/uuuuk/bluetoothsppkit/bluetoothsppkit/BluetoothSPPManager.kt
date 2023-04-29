package com.uuuuk.bluetoothsppkit.bluetoothsppkit

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import com.uuuuk.bluetoothsppkit.IBluetoothDevice
import com.uuuuk.bluetoothsppkit.IBluetoothState
import java.io.IOException
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class BluetoothSPPManager(context: Context) {
    lateinit var mAdapter: BluetoothAdapter
    private var mContext = context
    private var mIBluetoothState: IBluetoothState? = null
    private var mIBluetoothDevice: IBluetoothDevice? = null
    private var mSocket: BluetoothSocket? = null
    private var mServerThread: ServerThread? = null
    private var mClientThread: ClientThread? = null
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    var boundDeviceShow = false
    var isWaitingConnection: Boolean
        get() = mServerThread != null
        private set(value) {}
    var isConnecting: Boolean
        get() = mSocket?.isConnected == true
        private set(value) {}
    var stopNameList = ArrayList<String>()
    private val deviceSet = hashSetOf<BluetoothDevice>()

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        mIBluetoothDevice?.onFound(device)
                        deviceSet.add(device)
                        if (stopNameList.size > 0) {
                            for (i in stopNameList) {
                                if (i == device.name) {
                                    mAdapter.cancelDiscovery()
                                }
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    mIBluetoothState?.onDiscoverStateChange(false)
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    mIBluetoothState?.onDiscoverStateChange(true)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    mIBluetoothState?.onAdapterStateChange(intent.extras?.get(BluetoothAdapter.EXTRA_STATE) as Int)
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    mIBluetoothState?.onConnectStateChange(
                        intent.extras?.get(BluetoothAdapter.EXTRA_CONNECTION_STATE) as Int,
                        intent.extras?.get(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice
                    )
                }
            }
        }
    }

    init {
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND) //添加蓝牙广播的Action
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        mContext.registerReceiver(receiver, intentFilter)//注册广播接收者
        mAdapter = BluetoothAdapter.getDefaultAdapter()
    }

    fun setDeviceImp(mIBluetoothDevice: IBluetoothDevice) {
        this.mIBluetoothDevice = mIBluetoothDevice
    }

    fun setStateImp(mIBluetoothState: IBluetoothState) {
        this.mIBluetoothState = mIBluetoothState
    }

    fun removeDeviceImp() {
        this.mIBluetoothDevice = null
    }

    fun removeStateImp() {
        this.mIBluetoothState = null
    }

    fun getDeviceSet(): HashSet<BluetoothDevice> {
        if (boundDeviceShow) {
            deviceSet.addAll(mAdapter.bondedDevices)
        }
        return deviceSet
    }

    fun enable(): Boolean {
        return mAdapter.enable()
    }

    fun disable(): Boolean {
        return mAdapter.disable()
    }

    fun getState(): Boolean {
        return when (mAdapter.state) {
            BluetoothAdapter.STATE_OFF -> {
                false
            }
            BluetoothAdapter.STATE_TURNING_OFF -> {
                false
            }
            BluetoothAdapter.STATE_ON -> {
                true
            }
            BluetoothAdapter.STATE_TURNING_ON -> {
                true
            }
            else -> false
        }
    }

    fun startSearch(): Boolean {
        deviceSet.clear()
        if (boundDeviceShow) {
            deviceSet.addAll(mAdapter.bondedDevices)
        }
        if (mAdapter.isDiscovering) {
            mAdapter.cancelDiscovery()
            return false
        }
        stopNameList.clear()
        return mAdapter.startDiscovery()
    }

    fun startSearch(milliSecond: Long): Boolean {
        deviceSet.clear()
        if (boundDeviceShow) {
            deviceSet.addAll(mAdapter.bondedDevices)
        }
        if (mAdapter.isDiscovering) {
            mAdapter.cancelDiscovery()
            return false
        }
        Handler(Looper.getMainLooper()).postDelayed({
            mAdapter.cancelDiscovery()
        }, milliSecond)
        stopNameList.clear()
        return mAdapter.startDiscovery()
    }

    fun startSearch(stopName: ArrayList<String>): Boolean {
        deviceSet.clear()
        if (boundDeviceShow) {
            deviceSet.addAll(mAdapter.bondedDevices)
        }
        if (mAdapter.isDiscovering) {
            mAdapter.cancelDiscovery()
            return false
        }
        stopNameList = stopName
        return mAdapter.startDiscovery()
    }

    fun stopSearch(): Boolean {
        return mAdapter.cancelDiscovery()
    }

    fun connectDevice(device: BluetoothDevice) {
        mClientThread = ClientThread(device)
        mClientThread!!.start()
    }

    fun connectDevice(deviceMac: String) {
        val device = mAdapter.getRemoteDevice(deviceMac)
        mClientThread = ClientThread(device)
        mClientThread!!.start()
    }

    fun connectDevice(deviceMac: ByteArray) {
        val device = mAdapter.getRemoteDevice(deviceMac)
        mClientThread = ClientThread(device)
        mClientThread!!.start()
    }

    fun disconnect() {
        if (mSocket != null) {
            mSocket?.close()
            mSocket = null
            mIBluetoothDevice?.onConnectionFailed(Exception("disconnect"))
        }
    }

    fun waitClientConnection() {
        mServerThread = ServerThread()
        mServerThread!!.timeOut = 0
        mServerThread!!.start()

    }

    fun waitClientConnection(milliSecond: Int) {
        mServerThread = ServerThread()
        mServerThread!!.timeOut = milliSecond
        mServerThread!!.start()
    }

    fun interruptClientConnection() {
        mServerThread?.serverSocket?.close()
        isWaitingConnection = false
        mIBluetoothState?.onWaitingStateChange(isWaitingConnection)
    }

    inner class ServerThread : Thread() {
        var timeOut = 0
        var serverSocket: BluetoothServerSocket? = null
        override fun run() {
            isWaitingConnection = true
            mIBluetoothState?.onWaitingStateChange(isWaitingConnection)
            serverSocket = mAdapter.listenUsingRfcommWithServiceRecord("", SPP_UUID)
            if (timeOut > 0) {
                try {
                    val socket = serverSocket!!.accept(timeOut)
                    mSocket = socket
                    mIBluetoothDevice?.onConnectionSuccessful(socket)
                } catch (e: IOException) {
                    mIBluetoothDevice?.onConnectionFailed(e)
                }
            } else {
                try {
                    val socket = serverSocket!!.accept()
                    mSocket = socket
                    mIBluetoothDevice?.onConnectionSuccessful(socket)
                } catch (e: IOException) {
                    mIBluetoothDevice?.onConnectionFailed(e)
                }
            }
            serverSocket?.close()
            mServerThread = null
            mIBluetoothState?.onWaitingStateChange(isWaitingConnection)
        }
    }

    inner class ClientThread(private val device: BluetoothDevice) : Thread() {
        override fun run() {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                mSocket = socket
                mIBluetoothDevice?.onConnectionSuccessful(socket)
            } catch (e: Exception) {
                mIBluetoothDevice?.onConnectionFailed(e)
                mClientThread = null
            }
        }
    }


}