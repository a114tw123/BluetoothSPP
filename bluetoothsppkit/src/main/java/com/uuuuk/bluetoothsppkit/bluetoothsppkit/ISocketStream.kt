package com.uuuuk.bluetoothsppkit

import java.lang.Exception

interface ISocketStream {
    fun onReceived(data:ByteArray)
    fun onError(e:Exception)
}