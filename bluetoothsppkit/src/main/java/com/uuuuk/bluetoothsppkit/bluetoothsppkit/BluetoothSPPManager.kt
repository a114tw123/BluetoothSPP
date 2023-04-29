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
//此物件負責管理藍牙狀態與設備連線
class BluetoothSPPManager(context: Context) {
    lateinit var mAdapter: BluetoothAdapter
    private var mContext = context
    private var mIBluetoothState: IBluetoothState? = null
    private var mIBluetoothDevice: IBluetoothDevice? = null
    private var mSocket: BluetoothSocket? = null
    private var mServerThread: ServerThread? = null
    private var mClientThread: ClientThread? = null
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    //是否顯示已綁定裝置
    var boundDeviceShow = false
    var isWaitingConnection: Boolean
        get() = mServerThread != null
        private set(value) {}
    var isConnecting: Boolean
        get() = mSocket?.isConnected == true
        private set(value) {}
    var stopNameList = ArrayList<String>()
    private val deviceSet = hashSetOf<BluetoothDevice>()
//廣播接收器
    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                //發現設備廣播，每次掃描到設備就儲存起來，如果符合特定設備名稱即提早停止掃描
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
                //掃描結束廣播，觸發掃描狀態變化事件
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    mIBluetoothState?.onDiscoverStateChange(false)
                }
                //掃描開始廣播，觸發掃描狀態變化事件
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    mIBluetoothState?.onDiscoverStateChange(true)
                }
                //藍牙狀態變化廣播，觸發藍牙狀態變化事件
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    mIBluetoothState?.onAdapterStateChange(intent.extras?.get(BluetoothAdapter.EXTRA_STATE) as Int)
                }
                //連線狀態變化廣播，觸發連線狀態變化事件
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    mIBluetoothState?.onConnectStateChange(
                        intent.extras?.get(BluetoothAdapter.EXTRA_CONNECTION_STATE) as Int,
                        intent.extras?.get(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice
                    )
                }
            }
        }
    }
    //初始化
    init {
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND) //添加蓝牙广播的Action
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        mContext.registerReceiver(receiver, intentFilter)//注册广播接收者
        mAdapter = BluetoothAdapter.getDefaultAdapter()
    }
    //設定設備監聽器
    fun setDeviceImp(mIBluetoothDevice: IBluetoothDevice) {
        this.mIBluetoothDevice = mIBluetoothDevice
    }
    //設定狀態監聽器
    fun setStateImp(mIBluetoothState: IBluetoothState) {
        this.mIBluetoothState = mIBluetoothState
    }
    //移除設備監聽器
    fun removeDeviceImp() {
        this.mIBluetoothDevice = null
    }
    //移除狀態監聽器
    fun removeStateImp() {
        this.mIBluetoothState = null
    }
    //取得設備清單
    fun getDeviceSet(): HashSet<BluetoothDevice> {
        if (boundDeviceShow) {
            deviceSet.addAll(mAdapter.bondedDevices)
        }
        return deviceSet
    }
    //開啟藍牙
    fun enable(): Boolean {
        return mAdapter.enable()
    }
    //關閉藍牙
    fun disable(): Boolean {
        return mAdapter.disable()
    }
    //取得目前藍牙狀態
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
    //開始掃描
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
    //開始掃描，到設定時間後停止掃描
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
    //開始掃描，掃描到特定裝置名稱後停止掃描
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
    //停止掃描
    fun stopSearch(): Boolean {
        return mAdapter.cancelDiscovery()
    }
    //連線設備
    fun connectDevice(device: BluetoothDevice) {
        mClientThread = ClientThread(device)
        mClientThread!!.start()
    }
    //以mac碼連線設備
    fun connectDevice(deviceMac: String) {
        val device = mAdapter.getRemoteDevice(deviceMac)
        mClientThread = ClientThread(device)
        mClientThread!!.start()
    }
    //以mac碼連線設備
    fun connectDevice(deviceMac: ByteArray) {
        val device = mAdapter.getRemoteDevice(deviceMac)
        mClientThread = ClientThread(device)
        mClientThread!!.start()
    }
    //關閉連線
    fun disconnect() {
        if (mSocket != null) {
            mSocket?.close()
            mSocket = null
            mIBluetoothDevice?.onConnectionFailed(Exception("disconnect"))
        }
    }
    //開啟等待連線
    fun waitClientConnection() {
        mServerThread = ServerThread()
        mServerThread!!.timeOut = 0
        mServerThread!!.start()

    }
    //開啟等待連線，到設定時間後停止等待連線
    fun waitClientConnection(milliSecond: Int) {
        mServerThread = ServerThread()
        mServerThread!!.timeOut = milliSecond
        mServerThread!!.start()
    }
    //停止等待連線
    fun interruptClientConnection() {
        mServerThread?.serverSocket?.close()
        isWaitingConnection = false
        mIBluetoothState?.onWaitingStateChange(isWaitingConnection)
    }
    //客戶端線程，等待其他裝置連線時使用，用來建立socket
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
    //客戶端線程，當主動連線其他裝置時使用，用來建立socket
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