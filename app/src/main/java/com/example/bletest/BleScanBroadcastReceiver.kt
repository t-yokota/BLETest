package com.example.bletest

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

private const val PENDING_INTENT_REQUEST_CODE = 3

class BleScanBroadcastReceiver: BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        // Error code
        val error = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (error != -1) {
            Log.i("ble_test", "BLE Scan error : $error")
            return
        }

        val scanResults: ArrayList<ScanResult> = intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)!!
        for (scanResult in scanResults) {
            scanResult.device.name?.let {
                // Callback type
                val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1)
                Log.i("ble_test", "Callback type : $callbackType")
                // Scan results
                Log.i("ble_Test", "Scan result : $it")
                // GATT connect
                if (it == "Feather nRF52840 Express") {
                    stopBleScan(context)
                    Log.i("ble_test", "ScanCallback: Connecting to \"$it\", address: ${scanResult.device.address}")
                    scanResult.device.connectGatt(context.applicationContext, false, gattCallback)
                }
            }
        }
    }
}

class MainApplication : Application() {
    init {
        instance = this
    }
    companion object {
        private var instance: MainApplication? = null
        fun applicationContext() : Context {
            return instance!!.applicationContext
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun stopBleScan(context: Context) {
    Log.i("ble_test", "stopped scan.")
    val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    bluetoothAdapter.bluetoothLeScanner.stopScan(getPendingIntent(context))
}

@SuppressLint("UnspecifiedImmutableFlag")
private fun getPendingIntent(context: Context): PendingIntent {
    return PendingIntent.getBroadcast(
        context,
        PENDING_INTENT_REQUEST_CODE,
        Intent(context.applicationContext, BleScanBroadcastReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/* Connecting to a BLE device */ /* Discovering services */
val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val deviceAddress = gatt.device.address
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("ble_test", "GattCallback: Successfully connected to $deviceAddress")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("ble_test", "GattCallback: Successfully disconnected from $deviceAddress")
                gatt.close()
            }
        } else {
            Log.w("ble_test", "GattCallback: Error $status encountered for $deviceAddress! Disconnecting..."
            )
//            gatt.close()
            gatt.connect()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        with(gatt) {
            Log.i("ble_test", "GattCallback: Discovered ${services.size} services for ${device.address}")
            printGattTable()
            listenToBondStateChanges(MainApplication.applicationContext())
//            val bluetoothAdapter: BluetoothAdapter by lazy {
//                val bluetoothManager = MainApplication.applicationContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//                bluetoothManager.adapter
//            }
//            bluetoothAdapter.getRemoteDevice(device.address).createBond()
        }
    }
}

private fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Log.i("ble_test", "printGattTable: No service and characteristic available, call discoverServices() first?")
        return
    }
    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--",
            prefix = "|--"
        ) { it.uuid.toString() }
        Log.i("ble_test", "printGattTable: \nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
        )
    }
}

/* Bonding with a BLE device */
fun listenToBondStateChanges(context: Context) {
    context.applicationContext.registerReceiver(
        broadcastReceiver,
        IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    )
}

private val broadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        with(intent) {
            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val previousBondState = getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                val bondState = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                val bondTransition = "${previousBondState.toBondStateDescription()} to " + bondState.toBondStateDescription()
                Log.i("ble_test", "Bond state change: ${device?.address} bond state changed | $bondTransition")
            }
        }
    }

    private fun Int.toBondStateDescription() = when(this) {
        BluetoothDevice.BOND_BONDED -> "BONDED"
        BluetoothDevice.BOND_BONDING -> "BONDING"
        BluetoothDevice.BOND_NONE -> "NOT BONDED"
        else -> "ERROR: $this"
    }
}