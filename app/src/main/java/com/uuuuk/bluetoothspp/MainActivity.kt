package com.uuuuk.bluetoothspp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.text.*
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.uuuuk.bluetoothsppkit.*
import com.uuuuk.bluetoothsppkit.bluetoothsppkit.BluetoothSPPManager
import com.uuuuk.bluetoothsppkit.bluetoothsppkit.SocketStream
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception
import java.nio.charset.Charset


class MainActivity : AppCompatActivity(){
    lateinit var sppManager: BluetoothSPPManager
    var socketStream: SocketStream?=null
    lateinit var chatAdapter:ChatAdapter
    var chatList= arrayListOf<ChatModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val permissonList=arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if(ContextCompat.checkSelfPermission(this, permissonList[0]) != PackageManager.PERMISSION_GRANTED||
            ContextCompat.checkSelfPermission(this, permissonList[1]) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, permissonList,1)
        }
        sppManager= BluetoothSPPManager(this)
        sppManager.setDeviceImp(ibluetoothDevice)
        sppManager.setStateImp(ibluetoothState)
        sppManager.boundDeviceShow=true
        sw_blt.isChecked=sppManager.getState()

        sw_blt.setOnClickListener {
            if (sw_blt.isChecked){
                sppManager.enable()
            }else{
                sppManager.disable()
            }
        }
        sw_connect.setOnClickListener{
            if (sw_connect.isChecked){
                sppManager.waitClientConnection()
            }else{
                sppManager.interruptClientConnection()
            }
        }
        btn_connect.setOnClickListener {
            if(sppManager.isConnecting){
                sppManager.disconnect()
                btn_connect.text="連線"
            }else{
                if (sppManager.getState()) {
                    showToast("藍牙掃描中")
                    sppManager.startSearch(5000)
                }else{
                    showToast("藍牙沒開")
                }
            }

        }
        btn_send.setOnClickListener {
            if (sppManager.isConnecting){
                val msg=et_send.text.toString()
                et_send.setText("")
                if (msg!=""){
                    socketStream?.send(msg)
                    chatList.add(ChatModel(msg,true))
                    chatAdapter.notifyDataSetChanged()
                }
            }else{
                showToast("尚未連線")
            }

        }

        chatAdapter= ChatAdapter(chatList)
        rec_chat.adapter=chatAdapter
        rec_chat.layoutManager = LinearLayoutManager(this)
    }
    private val ibluetoothDevice=object :IBluetoothDevice{

        override fun onFound(device: BluetoothDevice) {
            Log.d(Host+Found,"${device.name},${device.address}")
        }

        override fun onConnectionSuccessful(socket: BluetoothSocket) {
            Log.d(Host+SocketCreated, socket.remoteDevice.name)
            this@MainActivity.socketStream= SocketStream(socket)
            socketStream!!.setStreamImp(iSocketStream)
            runOnUiThread{
                tv_title.text=socket.remoteDevice.name.toString()
                btn_connect.text="斷線"
            }

        }

        override fun onConnectionFailed(e: Exception) {
            Log.d(Host+SocketFailed,e.toString())

        }
    }

    private val ibluetoothState=object :IBluetoothState {
        override fun onAdapterStateChange(State: Int) {
            Log.d(Host + AdapterStateChange, "now:$State")
            when (State) {
                10 -> {
                    sw_blt.isChecked = false
                }
                12 -> {
                    sw_blt.isChecked = true
                }
            }
        }

        override fun onConnectStateChange(State: Int, device: BluetoothDevice) {
            Log.d(Host + ConnectStateChange, "now:$State,deviceName:${device.name}")
        }

        override fun onDiscoverStateChange(isDiscovering: Boolean) {
            Log.d(Host + Discover, isDiscovering.toString())
            if (!isDiscovering){
                val deviceList=ArrayList<BluetoothDevice>()
                deviceList.addAll(sppManager.getDeviceSet())
                val bltList=ArrayList<String>()
                for (i in deviceList){
                    bltList.add("${i.name},${i.address}")
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("選擇藍芽設備")
                    .setItems(bltList.toTypedArray()){ _, i ->
                        sppManager.connectDevice(deviceList[i])
                    }
                    .show()
            }
        }

        override fun onWaitingStateChange(isWaiting: Boolean) {
            Log.d(Host + WaitingConnection, isWaiting.toString())

            runOnUiThread {
                sw_connect.isChecked=isWaiting
            }
        }
    }
    val iSocketStream=object : ISocketStream {
        override fun onReceived(data: ByteArray) {
            Log.d(Host+Received,data.toString(Charset.defaultCharset()))
            val msg=data.toString(Charset.defaultCharset())
            chatList.add(ChatModel(msg,false))
            runOnUiThread {
                chatAdapter.notifyDataSetChanged()
            }

        }

        override fun onError(e: Exception) {
            Log.d(Host+SocketError,e.toString())

            runOnUiThread {
                chatList.clear()
                chatAdapter.notifyDataSetChanged()
                tv_title.text="未連線"
                btn_connect.text="連線"
            }

        }
    }
    private fun showToast(str:String){
        Toast.makeText(this@MainActivity, str,Toast.LENGTH_SHORT).show()
    }
    companion object{
        const val Host="SPP:"
        const val Discover="bluetoothDiscover"
        const val AdapterStateChange="AdapterStateChange"
        const val ConnectStateChange="ConnectStateChange"
        const val SocketCreated="SocketCreated"
        const val SocketFailed="SocketFailed"
        const val SocketError="SocketError"
        const val Found="Found"
        const val WaitingConnection="WaitingConnection"
        const val Received="Received"
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ENTER) { /*隱藏軟鍵盤*/
            hideKeyboard(currentFocus?.windowToken)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (isShouldHideKeyboard(v, ev)) {
                hideKeyboard(v!!.windowToken)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideKeyboard(token: IBinder?) {
        if (token != null) {
            val im = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    private fun isShouldHideKeyboard(v: View?, event: MotionEvent): Boolean {
        if (v != null && v is EditText) {
            val l = intArrayOf(0, 0)
            v.getLocationInWindow(l)
            val left = l[0]
            val top = l[1]
            val bottom = top + v.getHeight()
            val right = left + v.getWidth()
            return !(event.x > left && event.x < right && event.y > top && event.y < bottom)
        }
        return false
    }


}
