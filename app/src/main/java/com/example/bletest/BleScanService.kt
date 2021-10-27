package com.example.bletest

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi

private const val PENDING_INTENT_REQUEST_CODE = 3

class BleScanService: Service() {

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner: BluetoothLeScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val bondedDevices = bluetoothAdapter.bondedDevices

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
        .build()

    override fun onCreate() {
        super.onCreate()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val error = intent?.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (error != -1) {
            Log.i("ble_test", "BLE Scan error : $error")
            return super.onStartCommand(intent, flags, startId)
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
                    stopBleScan()
                    Log.i("ble_test", "ScanCallback: Connecting to \"$it\", address: ${scanResult.device.address}")
                    scanResult.device.connectGatt(applicationContext, false, gattCallback)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopBleScan() {
        Log.i("ble_test", "stopped scan.")
        bleScanner.stopScan(getPendingIntentForService())
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getPendingIntentForService(): PendingIntent {
        return PendingIntent.getService(
            this,
            PENDING_INTENT_REQUEST_CODE,
            Intent(applicationContext, BleScanService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /* Connecting to a BLE device */ /* Discovering services */
    private val gattCallback = object : BluetoothGattCallback() {
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
                Log.w("ble_test", "GattCallback: Error $status encountered for $deviceAddress! Disconnecting...")
//            gatt.close()
                gatt.connect()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.i("ble_test", "GattCallback: Discovered ${services.size} services for ${device.address}")
                printGattTable()
                listenToBondStateChanges(MainApplication.applicationContext())
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
            Log.i("ble_test", "printGattTable: \nService ${service.uuid}\nCharacteristics:\n$characteristicsTable")
        }
    }
}