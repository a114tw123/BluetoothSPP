package com.uuuuk.bluetoothsppkit

import android.bluetooth.BluetoothDevice

interface IBluetoothState {
    fun onAdapterStateChange(State:Int)
    fun onConnectStateChange(State: Int,device: BluetoothDevice)
    fun onDiscoverStateChange(isDiscovering:Boolean)
    fun onWaitingStateChange(isWaiting:Boolean)
}