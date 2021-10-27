package com.example.bletest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.bletest.databinding.ActivityMainBinding

private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2
private const val PENDING_INTENT_REQUEST_CODE = 3

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { // view ->
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
            if (isScanning) {
                stopBleScan()
            } else {
                startBleScan()
            }
        }

        val bondedDevices = bluetoothAdapter.bondedDevices
        if (bondedDevices.size > 0) {
            Log.i("ble_test", "This device is already bonded to ...")
            for (device in bondedDevices) {
                Log.i("ble_test", " - ${device.name}, address: ${device.address}")
//                if(device.name == "Feather nRF52840 Express"){
//                    startBleScanUsingPendingIntent()
//                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

//--------------------------------------------------------------------------------------------------
    /* Based on "The Ultimate Guide to Android Bluetooth Low Energy" */
    /* https://punchthrough.com/android-ble-guide/ */

    /* Making sure that Bluetooth is enabled */
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != RESULT_OK) {
                    Log.i("ble_test","BLE is not enabled.")
                } else {
                    Log.i("ble_test","BLE is enabled.")
                }
            }
        }
    }

    /* Requesting location permission from the user */
    val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        Log.i("ble_test","Fine location permission is required.")
        requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_REQUEST_CODE)
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    Log.i("ble_test","Denied adding permission.")
                } else {
                    Log.i("ble_test","Applied adding permission.")
                }
            }
        }
    }

    /* Performing a BLE scan */
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
        .build()

    private var isScanning = false
        set(value) {
            field = value
            if (value) {
                findViewById<TextView>(R.id.scanStateText).setText(R.string.Scanning)
            } else {
                findViewById<TextView>(R.id.scanStateText).setText(R.string.Stopped)
            }
        }

    private var filters: List<ScanFilter> = listOf(
        ScanFilter.Builder()
            .setDeviceName("Feather nRF52840 Express")
            .build()
    )

    private fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        else {
            Log.i("ble_test", "scanning...")
            bleScanner.startScan(filters, scanSettings, scanCallback)
            isScanning = true
        }
    }

    private fun stopBleScan() {
        Log.i("ble_test", "stopped scan.")
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result.device) {
                Log.i("ble_test", "ScanCallback: Name: ${name ?: "Unnamed"}, address: $address")
                if (isScanning) {
                    stopBleScan()
                }
                Log.i("ble_test", "ScanCallback: Connecting to \"$name\", address: $address")
                connectGatt(applicationContext, false, gattCallback)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startBleScanUsingPendingIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        else {
            Log.i("ble_test", "scanning using PendingIntent ...")
            bleScanner.startScan(null, scanSettings, getPendingIntentForService())
//            bleScanner.startScan(null, scanSettings, getPendingIntent_BroadcastReceiver())
            isScanning = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopBleScanUsingPendingIntent() {
        Log.i("ble_test", "stopped scan.")
        bleScanner.stopScan(getPendingIntentForService())
//        bleScanner.stopScan(getPendingIntent_BroadcastReceiver())
        isScanning = false
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

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getPendingIntentForBroadcastReceiver(): PendingIntent {
        return PendingIntent.getBroadcast(
            this,
            PENDING_INTENT_REQUEST_CODE,
            Intent(this.applicationContext, BleScanBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /* Connecting to a BLE device */
    /* Discovering services */
    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresApi(Build.VERSION_CODES.O)
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
                gatt.close()
//                gatt.connect()
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.i("ble_test", "GattCallback: Discovered ${services.size} services for ${device.address}")
                printGattTable()
                /* bonding */
                listenToBondStateChanges(applicationContext)
                if(bluetoothAdapter.getRemoteDevice(device.address).bondState == BluetoothDevice.BOND_BONDED){
                    startBleScanUsingPendingIntent()
                } else {
                    bluetoothAdapter.getRemoteDevice(device.address).createBond()
                }
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
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onReceive(context: Context, intent: Intent) {
            with(intent) {
                if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val device = getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val previousBondState = getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                    val bondState = getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    val bondTransition = "${previousBondState.toBondStateDescription()} to " + bondState.toBondStateDescription()
                    Log.i("ble_test", "Bond state change: ${device?.address} bond state changed | $bondTransition")
                    findViewById<TextView>(R.id.scanStateText).setText(bondState.toBondStateDescription())
//                    if(bondState.toBondStateDescription() == "BONDED"){
//                        startBleScanUsingPendingIntent()
//                    }
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
}