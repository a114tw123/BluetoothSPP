package com.uuuuk.bluetoothsppkit.bluetoothsppkit

import android.bluetooth.BluetoothSocket
import com.uuuuk.bluetoothsppkit.ISocketStream
import java.io.InputStream
import java.lang.Exception
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList

//此物件用來管理藍牙資料的收發
class SocketStream(private val socket: BluetoothSocket) {
    private var mInput: InputStream?=null
    private var mOutput: OutputStream?=null
    private var mISocketStream: ISocketStream?=null
    private var mReadThread: ReadThread?=null
    private var mWriteThread: WriteThread?=null
    //接收資料線程
    inner class ReadThread: Thread() {
        override fun run() {
            super.run()
            try {
                val buf = ByteArray(1024)
                while (socket.isConnected){
                    //當連線時，讀取接收資料暫存器的資料長度，沒資料時會回傳-1
                    var len = mInput!!.read(buf)
                    while (len!=-1) {
                        //建立暫存變數，從0開始讀取資料長度，並觸發接收資料事件，最後將資料長度變數設定為-1
                        val data=ArrayList<Byte>()
                        for (i in 0 until len){
                            data.add(buf[i])
                        }
                        mISocketStream?.onReceived(data.toByteArray())
                        len=-1
                    }
                }
            }catch (e:Exception){
                mISocketStream?.onError(e)
                socket.close()
            }
        }
    }
    //發送資料線程
    inner class WriteThread: Thread() {

        private val lock= ReentrantLock()
        private val condition =lock.newCondition()
        private var isBusy=false
        private val dataBuffer=ArrayList<ByteArray>()

        fun addToBuffer(data:ByteArray){
            //  將要發送的資料寫入dataBuffer
            dataBuffer.add(data)
            if (!isBusy){
                try {
                    //如果目前沒在發送資料，通知線程可發送資料
                    lock.lock()
                    condition.signal()
                }catch (e:Exception) {
                    throw Exception("SocketStream send error")
                }finally {
                    lock.unlock()
                }
               
            }
        }
        override fun run() {
            super.run()
            while (true) {
                try {
                    //連線後等待通知
                    lock.lock()
                    condition.await()
                    //收到通知後，修改狀態為忙碌中
                    isBusy = true
                    //如果dataBuffer內有資料，逐筆將要傳送的資料寫入傳送資料暫存器，並將寫入後的資料存dataBuffer中移除
                    while (dataBuffer.size > 0) {
                        mOutput?.write(dataBuffer[0])
                        mOutput?.flush()
                        dataBuffer.removeAt(0)
                    }
                    isBusy=false
                } catch (e: Exception) {
                    dataBuffer.clear()
                    mISocketStream?.onError(e)
                    socket.close()
                } finally {
                    lock.unlock()
                }
            }
        }
    }
    //初始化
    init {
        try {
            mInput=socket.inputStream
            mOutput=socket.outputStream
            mReadThread=ReadThread()
            mWriteThread=WriteThread()
            mReadThread!!.start()
            mWriteThread!!.start()
            
        }catch (e:Exception){
            mISocketStream?.onError(e)
            socket.close()
        }
            
    }
    //設定藍牙通訊監聽器
    fun setStreamImp(mISocketStream: ISocketStream){
        this.mISocketStream=mISocketStream
    }
    //移除藍牙通訊監聽器
    fun removeStreamImp(){
        this.mISocketStream=null
    }
    //發送訊息
    fun send(data:ByteArray){
        mWriteThread?.addToBuffer(data)
    }
    //發送訊息
    fun send(msg:String, charset: Charset = Charset.defaultCharset()){
        mWriteThread?.addToBuffer(msg.toByteArray(charset))
    }

}