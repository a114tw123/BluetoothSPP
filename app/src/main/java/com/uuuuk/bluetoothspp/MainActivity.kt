package com.uuuuk.bluetoothspp

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
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
    lateinit var sppManager: BluetoothSPPManager//宣告並延遲初始化sppManager
    var socketStream: SocketStream?=null//宣告socketStream並初始化為null
    lateinit var chatAdapter:ChatAdapter
    var chatList= arrayListOf<ChatModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //權限請求
        val permissionList=arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        if(ContextCompat.checkSelfPermission(this, permissionList[0]) != PackageManager.PERMISSION_GRANTED||
            ContextCompat.checkSelfPermission(this, permissionList[1]) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, permissionList,1)
        }
//      sppManager初始化，並設定事件監聽器
        sppManager= BluetoothSPPManager(this)
        sppManager.setDeviceImp(ibluetoothDevice)
        sppManager.setStateImp(ibluetoothState)
//      顯示已綁定的裝置
        sppManager.boundDeviceShow=true
//      藍牙開關switch=藍芽開啟狀態
        sw_blt.isChecked=sppManager.getState()
//      藍牙開關switch點擊事件，開啟或關閉藍牙
        sw_blt.setOnClickListener {
            if (sw_blt.isChecked){
                sppManager.enable()
            }else{
                sppManager.disable()
            }
        }
//      等待連線開關switch點擊事件，開啟後讓手機可被其他設備主動連線
        sw_connect.setOnClickListener{
            if (sw_connect.isChecked){
                sppManager.waitClientConnection()
            }else{
                sppManager.interruptClientConnection()
            }
        }
//      連線按鈕點擊事件，若已連線則斷線，否則判斷是否開啟藍芽，若開啟則進行藍芽掃描5秒
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
//      發送按鈕點擊事件
        btn_send.setOnClickListener {
            if (sppManager.isConnecting){
//              如果已連線，暫存輸入欄文字並清空輸入欄
                val msg=et_send.text.toString()
                et_send.setText("")
                if (msg!=""){
//                  如果輸入內容不為空，發送資料，並將訊息加入聊天list並刷新畫面
                    socketStream?.send(msg)
                    chatList.add(ChatModel(msg,true))
                    chatAdapter.notifyDataSetChanged()
                }
            }else{
                showToast("尚未連線")
            }

        }
//      初始化chatAdapter
        chatAdapter= ChatAdapter(chatList)
        rec_chat.adapter=chatAdapter
        rec_chat.layoutManager = LinearLayoutManager(this)
    }
//  藍牙設備監聽器
    private val ibluetoothDevice=object :IBluetoothDevice{
//      掃描藍牙裝置事件，每次掃描到藍牙設備就會觸發
        override fun onFound(device: BluetoothDevice) {
            Log.d(Host+Found,"${device.name},${device.address}")
        }
//      連線成功事件，連線成功時觸發
        override fun onConnectionSuccessful(socket: BluetoothSocket) {
            Log.d(Host+SocketCreated, socket.remoteDevice.name)
            //初始化socketStream並設定socket監聽器
            this@MainActivity.socketStream= SocketStream(socket)
            socketStream!!.setStreamImp(iSocketStream)
            //更新畫面
            runOnUiThread{
                tv_title.text=socket.remoteDevice.name.toString()
                btn_connect.text="斷線"
            }

        }
//      連線失敗事件，連線失敗時觸發
        override fun onConnectionFailed(e: Exception) {
            Log.d(Host+SocketFailed,e.toString())

        }
    }
    //藍牙狀態監聽器
    private val ibluetoothState=object :IBluetoothState {
        //藍牙狀態變化事件，藍牙被開啟或關閉時觸發
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
        //連線狀態變化事件，連線狀態變化時觸發
        override fun onConnectStateChange(State: Int, device: BluetoothDevice) {
            Log.d(Host + ConnectStateChange, "now:$State,deviceName:${device.name}")
        }
        //掃描狀態變化事件，開始或停止掃描時觸發
        override fun onDiscoverStateChange(isDiscovering: Boolean) {
            Log.d(Host + Discover, isDiscovering.toString())
            if (!isDiscovering){
                //當停止掃描時，建立一個裝置列表
                val deviceList=ArrayList<BluetoothDevice>()
                deviceList.addAll(sppManager.getDeviceSet())
                val bltList=ArrayList<String>()
                for (i in deviceList){
                    bltList.add("${i.name},${i.address}")
                }
                //用dialog顯示裝置列表，點擊後連線該裝置
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("選擇藍芽設備")
                    .setItems(bltList.toTypedArray()){ _, i ->
                        sppManager.connectDevice(deviceList[i])
                    }
                    .show()
            }
        }
        //等待連線狀態變化事件，開始或停止等待連線時觸發
        override fun onWaitingStateChange(isWaiting: Boolean) {
            Log.d(Host + WaitingConnection, isWaiting.toString())

            runOnUiThread {
                sw_connect.isChecked=isWaiting
            }
        }
    }
    //藍牙通訊監聽器
    val iSocketStream=object : ISocketStream {
        //接收資料事件
        override fun onReceived(data: ByteArray) {
            Log.d(Host+Received,data.toString(Charset.defaultCharset()))
            //將收到的資料轉換成字串，並將訊息加入聊天list並刷新畫面
            val msg=data.toString(Charset.defaultCharset())
            chatList.add(ChatModel(msg,false))
            runOnUiThread {
                chatAdapter.notifyDataSetChanged()
            }

        }
        //錯誤事件
        override fun onError(e: Exception) {
            Log.d(Host+SocketError,e.toString())
            //發生錯誤刷新畫面
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
//    下面都是鍵盤相關程式
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
