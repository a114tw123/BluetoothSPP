package com.uuuuk.bluetoothsppkit

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException

interface IBluetoothDevice {
    
    fun onFound(device: BluetoothDevice)
    fun onConnectionSuccessful(socket: BluetoothSocket)
    fun onConnectionFailed(e:Exception)
}