package com.uuuuk.bluetoothsppkit.bluetoothsppkit

import android.bluetooth.BluetoothSocket
import com.uuuuk.bluetoothsppkit.ISocketStream
import java.io.InputStream
import java.lang.Exception
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList


class SocketStream(private val socket: BluetoothSocket) {
    private var mInput: InputStream?=null
    private var mOutput: OutputStream?=null
    private var mISocketStream: ISocketStream?=null
    private var mReadThread: ReadThread?=null
    private var mWriteThread: WriteThread?=null
    inner class ReadThread: Thread() {
        override fun run() {
            super.run()
            try {
                val buf = ByteArray(1024)
                while (socket.isConnected){
                    var len = mInput!!.read(buf)
                    while (len!=-1) {
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
    inner class WriteThread: Thread() {

        private val lock= ReentrantLock()
        private val condition =lock.newCondition()
        private var isBusy=false
        private val dataBuffer=ArrayList<ByteArray>()

        fun addToBuffer(data:ByteArray){
            dataBuffer.add(data)
            if (!isBusy){
                try {
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
                    lock.lock()
                    condition.await()
                    isBusy = true
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
    fun setStreamImp(mISocketStream: ISocketStream){
        this.mISocketStream=mISocketStream
    }
    fun removeStreamImp(){
        this.mISocketStream=null
    }
    
    fun send(data:ByteArray){
        mWriteThread?.addToBuffer(data)
    }
    fun send(msg:String, charset: Charset = Charset.defaultCharset()){
        mWriteThread?.addToBuffer(msg.toByteArray(charset))
    }

}