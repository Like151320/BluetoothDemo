package li_ke.bluetoothdemo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

/**
 * 理论上可行，未实际测试过
 */
class MainActivity : AppCompatActivity() {
    /**蓝牙设备适配器*/
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var device: BluetoothDevice? = null
    private val listViewAdapter: ArrayAdapter<String> by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) }
    private var foundBluetoothDeviceReceiver: FoundBluetoothDeviceReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initView()
        initBluetooth()
    }

    /**搜索蓝牙设备*/
    private fun discoveryDevice() {
        if (bluetoothAdapter?.isDiscovering == true)//是否正在搜索蓝牙设备
            bluetoothAdapter.cancelDiscovery()//取消搜索蓝牙设备
        bluetoothAdapter?.startDiscovery()//开始搜索蓝牙设备
    }

    /**配对蓝牙设备*///PS - 很多教程贴中并没有提出连接前需要手动配对，可能是默认配对的
    private fun bond(device: BluetoothDevice) {
        //配对的概念: 首次连接蓝牙设备需要先配对，一旦配对成功，该设备的信息就会被保存，以后连接时无需再次配对，所以已配对的设备不一定是能连接的。
//        if (device.bondState == BluetoothDevice.BOND_NONE)
//            device.createBond()//开始配对 - 异步的,通过广播接受绑定结果。会直接返回(即将开始or出错了)
    }

    /**连接蓝牙设备*/
    private fun connect(device: BluetoothDevice) {
        //UUID的概念：两个蓝牙设备配对需要同一个UUID。
        //00001101-0000-1000-8000-00805F9B34FB —— 将蓝牙模拟成串口的服务
        //00001104-0000-1000-8000-00805F9B34FB —— 信息同步的服务
        //00001106-0000-1000-8000-00805F9B34FB —— 文件传输的服务
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")//UUID的默认值是固定的
        val socket = device.createRfcommSocketToServiceRecord(uuid)
        device.createBond()

        //蓝牙传输
        socket.connect()
        socket.outputStream.write("蓝牙发送的信息".toByteArray())
    }

    private fun initView() {
        listView.adapter = listViewAdapter

        //搜索蓝牙设备,若发现设备会发出广播
        btn_searchBluetooth.setOnClickListener {
            listViewAdapter.clear()
            discoveryDevice()
        }

        //查看已配对蓝牙设备
        btn_bondedBluetooth.setOnClickListener {
            listViewAdapter.clear()
            //绑定的蓝牙设备
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                listViewAdapter.add(deviceInfo(device))
            }
        }

        //配对蓝牙设备
        listView.setOnItemClickListener { parent, view, position, id ->
            val deviceInfo = listViewAdapter.getItem(position)
            device = bluetoothAdapter?.getRemoteDevice(deviceAddress(deviceInfo))//获得远程设备

            if (device == null) return@setOnItemClickListener

            //检测蓝牙地址有效
            if (!BluetoothAdapter.checkBluetoothAddress(device?.address)) {
                toast("$deviceInfo 蓝牙设备地址无效")
                return@setOnItemClickListener
            }

            //配对
            bond(device!!)

            //连接
            connect(device!!)
        }
    }

    private fun initBluetooth() {
        //没蓝牙
        if (bluetoothAdapter == null) {
            toast("此设备没有蓝牙")
            return
        }

        //注册蓝牙事件广播 : 若查找到蓝牙设备,会以广播的方式传来设备信息
        foundBluetoothDeviceReceiver = foundBluetoothDeviceReceiver ?: FoundBluetoothDeviceReceiver()
        val intentFilter = IntentFilter(BluetoothDevice.ACTION_FOUND)//每搜索一个蓝牙设备,发一个该广播
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)//蓝牙设备搜索完后,发该广播
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)//蓝牙设备配对状态改变
        intentFilter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)//蓝牙扫描模式改变
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)//蓝牙状态改变
        registerReceiver(foundBluetoothDeviceReceiver, intentFilter)

        //没开蓝牙
        if (!bluetoothAdapter.enable()) {
//            bluetoothAdapter.enable()//静默开启蓝牙
            //弹窗提示开启蓝牙
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)//设置蓝牙可见性，最多300秒
            startActivityForResult(intent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    //发现蓝牙设备
    inner class FoundBluetoothDeviceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> { // 发现蓝牙设备
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    val deviceInfo = deviceInfo(device)
                    //防重复展示
                    if (!(0 until listViewAdapter.count).any { return@any listViewAdapter.getItem(it) == deviceInfo }) {
                        listViewAdapter.add(deviceInfo)
                        listViewAdapter.notifyDataSetChanged()
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {// 蓝牙设备搜索结束
                    toast("蓝牙搜索结束")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {// 蓝牙配对状态改变
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                    when (device?.bondState) {
                        BluetoothDevice.BOND_NONE -> toast("取消配对")
                        BluetoothDevice.BOND_BONDING -> toast("正在配对")
                        BluetoothDevice.BOND_BONDED -> toast("完成配对")
                    }
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {// 蓝牙扫描模式改变

                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {// 蓝牙状态改变

                }
            }
        }
    }

    private fun deviceAddress(deviceInfo: String): String? = "\n\\S*".toRegex().find(deviceInfo)?.value?.removeRange(0, 1)
    private fun deviceInfo(device: BluetoothDevice?): String = "${device?.name}\n${device?.address}"
    private fun toast(string: String) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //蓝牙开启结果
        if (requestCode == ENABLE_BLUETOOTH_REQUEST_CODE && resultCode == RESULT_OK) {
            toast("蓝牙开启成功")
        } else
            toast("蓝牙开启失败")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(foundBluetoothDeviceReceiver)
        foundBluetoothDeviceReceiver = null
    }

    companion object {
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 100
    }
}


/**某帖子上有提到，但并没有用到*/
object ClsUtils {

    /**
     * 与设备配对 参考源码：platform/packages/apps/Settings.git
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    @Throws(Exception::class)
    fun createBond(btClass: Class<*>, btDevice: BluetoothDevice): Boolean {

        val createBondMethod = btClass.getMethod("createBond")
        val returnValue = createBondMethod.invoke(btDevice) as Boolean
        return returnValue

    }

    /**
     * 与设备解除配对 参考源码：platform/packages/apps/Settings.git
     * /Settings/src/com/android/settings/bluetooth/CachedBluetoothDevice.java
     */
    @Throws(Exception::class)
    fun removeBond(btClass: Class<*>, btDevice: BluetoothDevice): Boolean {

        val removeBondMethod = btClass.getMethod("removeBond")
        val returnValue = removeBondMethod.invoke(btDevice) as Boolean
        return returnValue

    }

    @Throws(Exception::class)
    fun setPin(btClass: Class<*>, btDevice: BluetoothDevice,
               str: String): Boolean {

        try {

            val removeBondMethod = btClass.getDeclaredMethod("setPin",
                    *arrayOf<Class<*>>(ByteArray::class.java))
            val returnValue = removeBondMethod.invoke(btDevice,
                    arrayOf<Any>(str.toByteArray())) as Boolean
            Log.e("returnValue", "" + returnValue)

        } catch (e: SecurityException) {

            // throw new RuntimeException(e.getMessage());
            e.printStackTrace()

        } catch (e: IllegalArgumentException) {

            // throw new RuntimeException(e.getMessage());
            e.printStackTrace()

        } catch (e: Exception) {

            // TODO Auto-generated catch block
            e.printStackTrace()

        }

        return true


    }

    @Throws(Exception::class)
    fun setPassKey(btClass: Class<*>, btDevice: BluetoothDevice,
                   str: String): Boolean {

        try {

            val removeBondMethod = btClass.getDeclaredMethod("setPasskey",
                    *arrayOf<Class<*>>(ByteArray::class.java))
            val returnValue = removeBondMethod.invoke(btDevice,
                    arrayOf<Any>(str.toByteArray())) as Boolean
            Log.e("returnValue", "" + returnValue)

        } catch (e: SecurityException) {

            // throw new RuntimeException(e.getMessage());
            e.printStackTrace()

        } catch (e: IllegalArgumentException) {

            // throw new RuntimeException(e.getMessage());
            e.printStackTrace()

        } catch (e: Exception) {

            // TODO Auto-generated catch block
            e.printStackTrace()

        }

        return true


    }

    // 取消用户输入


    @Throws(Exception::class)
    fun cancelPairingUserInput(btClass: Class<*>,
                               device: BluetoothDevice): Boolean {

        val createBondMethod = btClass.getMethod("cancelPairingUserInput")
        // cancelBondProcess()
        val returnValue = createBondMethod.invoke(device) as Boolean
        return returnValue

    }

    // 取消配对


    @Throws(Exception::class)
    fun cancelBondProcess(btClass: Class<*>,
                          device: BluetoothDevice): Boolean {

        val createBondMethod = btClass.getMethod("cancelBondProcess")
        val returnValue = createBondMethod.invoke(device) as Boolean
        return returnValue

    }

    /**
     *
     * @param clsShow
     */
    fun printAllInform(clsShow: Class<*>) {

        try {

            // 取得所有方法
            val hideMethod = clsShow.methods
            var i = 0
            while (i < hideMethod.size) {

                Log.e("method name", hideMethod[i].name + ";and the i is:"
                        + i)
                i++

            }
            // 取得所有常量
            val allFields = clsShow.fields
            i = 0
            while (i < allFields.size) {

                Log.e("Field name", allFields[i].name)
                i++

            }

        } catch (e: SecurityException) {

            // throw new RuntimeException(e.getMessage());
            e.printStackTrace()

        } catch (e: IllegalArgumentException) {

            // throw new RuntimeException(e.getMessage());
            e.printStackTrace()

        } catch (e: Exception) {

            // TODO Auto-generated catch block
            e.printStackTrace()

        }

    }

    fun isWantedMac(macStr: String?): Boolean {
        return macStr == "55:44:33:22:11:00"
    }

}