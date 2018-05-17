package li_ke.bluetoothdemo

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import li_ke.bluetoothdemo.MainActivity.Companion.TAG
import java.util.*

class BluetoothService : Service() {
    /**蓝牙设备适配器*/
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var device: BluetoothDevice? = null
    private var foundBluetoothDeviceReceiver: FoundBluetoothDeviceReceiver? = null
    private var socket: BluetoothSocket? = null

    private var bluetoothListUpdateCallback: ((BluetoothDevice) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? {
        return BluetoothBinder(this)
    }

    override fun onCreate() {
        super.onCreate()
        //注册蓝牙事件广播 : 若查找到蓝牙设备,会以广播的方式传来设备信息
        foundBluetoothDeviceReceiver = foundBluetoothDeviceReceiver ?: FoundBluetoothDeviceReceiver()
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)//每搜索一个蓝牙设备,发一个该广播
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)//蓝牙设备搜索完后,发该广播
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)//蓝牙设备配对状态改变
        intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)//蓝牙扫描模式改变
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)//蓝牙状态改变
        registerReceiver(foundBluetoothDeviceReceiver, intentFilter)
    }

    override fun onDestroy() {
        super.onDestroy()

        socket?.close()
        bluetoothListUpdateCallback = null
        unregisterReceiver(foundBluetoothDeviceReceiver)
        foundBluetoothDeviceReceiver = null
    }

    /**蓝牙是否可用*/
    fun bluetoothUsable(): Boolean = bluetoothAdapter != null

    /**蓝牙是否已开启*/
    fun bluetoothEnable(): Boolean = bluetoothAdapter?.enable() == true

    /**搜索蓝牙设备*/
    fun discoveryDevice() {
        if (bluetoothAdapter?.isDiscovering == true)//是否正在搜索蓝牙设备
            bluetoothAdapter.cancelDiscovery()//取消搜索蓝牙设备
        bluetoothAdapter?.startDiscovery()//开始搜索蓝牙设备
    }

    /**已配对设备*/
    fun bondedDevices(): MutableSet<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

    /**配对蓝牙设备*///PS - 很多教程贴中并没有提出连接前需要手动配对，可能是默认配对的
    fun bond(address: String): Boolean {
        device = bluetoothAdapter?.getRemoteDevice(address)//获得远程设备

        if (device == null) return false

        //检测蓝牙地址有效
        if (!BluetoothAdapter.checkBluetoothAddress(device?.address)) {
            Log.i(TAG, "$address 蓝牙设备地址无效")
            return false
        }

        device?.createBond()//异步,由广播通知Bond结果
        Log.w(TAG, "开始配对")
        return true

        //配对的概念: 首次连接蓝牙设备需要先配对，一旦配对成功，该设备的信息就会被保存，以后连接时无需再次配对，所以已配对的设备不一定是能连接的。
//        if (device.bondState == BluetoothDevice.BOND_NONE)
//            device.createBond()//开始配对 - 异步的,通过广播接受绑定结果。会直接返回(即将开始or出错了)
    }

    /**连接蓝牙设备*/
    fun connect(callback: (() -> Unit)? = null) {
        //UUID的概念：两个蓝牙设备配对需要同一个UUID。
        //00001101-0000-1000-8000-00805F9B34FB —— 将蓝牙模拟成串口的服务
        //00001104-0000-1000-8000-00805F9B34FB —— 信息同步的服务
        //00001106-0000-1000-8000-00805F9B34FB —— 文件传输的服务
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")//UUID的默认值是固定的
        socket = device?.createRfcommSocketToServiceRecord(uuid)

        //蓝牙连接
        if (socket?.isConnected == false) {
            Log.i(TAG, "正在连接……")
            socket?.connect()//这是耗时操作
            Log.i(TAG, "已连接")
            callback?.invoke()
        }
    }

    fun addBluetoothListUpdateListener(bluetoothListUpdateCallback: (BluetoothDevice) -> Unit) {
        this.bluetoothListUpdateCallback = bluetoothListUpdateCallback
    }

    /**向蓝牙发送数据*/
    fun send(str: String) {
        if (socket?.isConnected == false)
            socket?.connect()
        socket?.outputStream?.write(str.toByteArray())
        socket?.outputStream?.flush()
        Log.i(TAG, "已发送: $str")
    }

    //发现蓝牙设备
    inner class FoundBluetoothDeviceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> { // 发现蓝牙设备
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    bluetoothListUpdateCallback?.invoke(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {// 蓝牙设备搜索结束
                    Log.i(TAG, "蓝牙搜索结束")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {// 蓝牙配对状态改变
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    when (device?.bondState) {
                        BluetoothDevice.BOND_NONE -> Log.i(TAG, "取消配对")
                        BluetoothDevice.BOND_BONDING -> Log.i(TAG, "正在配对")
                        BluetoothDevice.BOND_BONDED -> Log.i(TAG, "完成配对")
                    }
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {// 蓝牙扫描模式改变

                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {// 蓝牙状态改变

                }
            }
        }
    }
}