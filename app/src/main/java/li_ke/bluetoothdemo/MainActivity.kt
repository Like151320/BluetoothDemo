package li_ke.bluetoothdemo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*

/**
 * 理论上可行，未实际测试过
 */
class MainActivity : AppCompatActivity() {

    private val listViewAdapter: ArrayAdapter<String> by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1) }
    private val conn = BluetoothServiceConnection()
    private val service get() = conn.binder?.service

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //蓝牙操作 Service
        bindService(Intent(this, BluetoothService::class.java), conn, Context.BIND_AUTO_CREATE)
        initView()
    }

    private fun initView() {
        listView.adapter = listViewAdapter

        //搜索蓝牙设备,若发现设备会发出广播
        btn_searchBluetooth.setOnClickListener {
            listViewAdapter.clear()
            service?.discoveryDevice()
        }

        //查看已配对蓝牙设备
        btn_bondedBluetooth.setOnClickListener {
            listViewAdapter.clear()
            //绑定的蓝牙设备
            service?.bondedDevices()?.forEach { device ->
                listViewAdapter.add(deviceInfo(device))
            }
        }

        //配对蓝牙设备
        listView.setOnItemClickListener { parent, view, position, id ->
            val deviceInfo = listViewAdapter.getItem(position)

            //配对
            if (service?.bond(deviceAddress(deviceInfo)!!) == true)
                service?.connect { toast("已连接") }//连接
        }

        //向蓝牙发送数据
        btn_send.setOnClickListener {
            service?.send(et_sendContent.text.toString())
        }
    }

    private fun deviceAddress(deviceInfo: String): String? = "\n\\S*".toRegex().find(deviceInfo)?.value?.removeRange(0, 1)
    private fun deviceInfo(device: BluetoothDevice?): String = "${device?.name}\n${device?.address}"
    private fun toast(string: String, duration: Int = Toast.LENGTH_SHORT) {
        Log.w(TAG, "toast: $string")
        runOnUiThread {
            Toast.makeText(this, string, duration).show()
        }
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
        conn.binder = null
        unbindService(conn)
    }

    inner class BluetoothServiceConnection : ServiceConnection {

        var binder: BluetoothBinder? = null
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Service 连接")
            binder = service as BluetoothBinder

            binder?.service?.addBluetoothListUpdateListener { device ->
                val deviceInfo = deviceInfo(device)
                runOnUiThread {
                    //防重复展示
                    if (!(0 until listViewAdapter.count).any { return@any listViewAdapter.getItem(it) == deviceInfo }) {
                        listViewAdapter.add(deviceInfo)
                        listViewAdapter.notifyDataSetChanged()
                    }
                }
            }

            //没蓝牙
            if (binder?.service?.bluetoothUsable() == false) {
                toast("此设备没蓝牙")
            }

            //没开蓝牙
            if (binder?.service?.bluetoothEnable() == false) {
//            bluetoothAdapter.enable()//静默开启蓝牙
                //弹窗提示开启蓝牙
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)//设置蓝牙可见性，最多300秒
                startActivityForResult(intent, ENABLE_BLUETOOTH_REQUEST_CODE)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "Service 断开")
        }
    }

    companion object {
        const val TAG = "Li_ke"
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 100
    }
}

class BluetoothBinder(val service: BluetoothService) : Binder() {
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