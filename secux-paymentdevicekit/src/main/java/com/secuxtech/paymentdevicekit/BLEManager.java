package com.secuxtech.paymentdevicekit;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;

import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;

import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;


import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;
import java.util.UUID;



import static com.secuxtech.paymentdevicekit.BLEManagerCallback.*;
import static java.lang.Thread.sleep;

public class BLEManager {

    public final static String              TAG = "secux-paymentdevicekit";
    private static final String             ServiceUUID =  "BC280001-610E-4C94-A5E2-0F352D4B5256";
    private static final String             TXCharacteristicUUID = "BC280003-610E-4C94-A5E2-0F352D4B5256";
    private static final String             RXCharacteristicUUID = "BC280002-610E-4C94-A5E2-0F352D4B5256";

    private static final Integer            Max_BLE_Package_Size = 20;

    protected static Object                 mWriteDoneLockObject = new Object();
    protected static Object                 mReadDoneLockObject = new Object();
    protected static Object                 mReadDataDoneLockObject = new Object();
    protected static Object                 mConnectDoneLockObject = new Object();
    protected static Object                 mScanDevDoneLockObject = new Object();

    private BluetoothAdapter                mBluetoothAdapter;
    private BluetoothManager                mBluetoothManager;

    protected BluetoothGatt                 mBluetoothGatt = null;
    protected BluetoothGattCharacteristic   mBluetoothRxCharacter = null;
    protected BluetoothGattCharacteristic   mBluetoothTxCharacter = null;

    protected BLEManagerCallback            mBleCallback = null;
    protected ScanCallback                  mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            handleScanResult(callbackType, result);
        }
    };



    private boolean             mRecvLastPacket = false;
    private ArrayList<byte[]>   mRecvDataPacketArray = new ArrayList<>();
    private byte[]              mRecvData = null;

    private byte[]              mLastSendData = null;
    private boolean             mSendRet = false;
    protected boolean           mConnectDone = false;

    public Context              mContext = null;

    protected BluetoothDevice   mDevice = null;

    protected boolean           mBLEScanStart = false;


    BLEManager(){

    }

    public void setBleCallback(BLEManagerCallback callback){
        mBleCallback = callback;
    }

    public void setBLEManager(BluetoothManager bleMgr){
        mBluetoothManager = bleMgr;
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    protected void handleScanResult(int callbackType, ScanResult result){

    }


    public boolean isSupportBle(){
        return mBluetoothAdapter != null;
    }

    public boolean isBleEnabled(){
        return isSupportBle() && mBluetoothAdapter.isEnabled();
    }

    public void openBlueAsyn(){
        if (isSupportBle()) {
            mBluetoothAdapter.enable();
        }
    }


    public void startScan(){
        SecuXPaymentDeviceKitLogHandler.Log("Start ble scan");

        //mBleDevArrList.clear();
        //mBluetoothScanner.startScan(scanCallback);

        mBLEScanStart = true;

        ScanFilter scanFilter = (new ScanFilter.Builder()).setServiceUuid(new ParcelUuid(UUID.fromString(ServiceUUID))).build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);
        android.bluetooth.le.ScanSettings.Builder scanSettingsBuilder = new android.bluetooth.le.ScanSettings.Builder();
        scanSettingsBuilder.setScanMode(1);
//        scanSettingsBuilder.setCallbackType(1);

        ScanSettings scanSettings = scanSettingsBuilder.build();
        //mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, scanSettings, mScanCallback);

    }

    public void stopScan(){
        SecuXPaymentDeviceKitLogHandler.Log("Stop ble scan");

        mBLEScanStart = false;
        //mBluetoothScanner.stopScan(mScanCallback);
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
    }

    BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
        }

        @Override
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyRead(gatt, txPhy, rxPhy, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.d(TAG, SystemClock.uptimeMillis() + " onConnectionStateChange: thread "
                    + Thread.currentThread() + " status " + status + "state " + newState);

            if (status != BluetoothGatt.GATT_SUCCESS) {

                gatt.close();

                if (!mConnectDone) {
                    mDevice.connectGatt(mContext, false, mBluetoothGattCallback);
                }

                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Device is connected");

                mBluetoothGatt = gatt;
                if (!mConnectDone) {
                    gatt.discoverServices();
                }

            }else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, SystemClock.uptimeMillis() + " Device is disconnected");
                gatt.close();

                if (mBleCallback != null){
                    mBleCallback.updateConnDevStatus(BlEDEV_Disconnected);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            Log.i(TAG, SystemClock.uptimeMillis() + " onServicesDiscovered " + status);
            if (status != BluetoothGatt.GATT_SUCCESS){
                Log.i(TAG, "Get service failed " + status);
                return;
            }

            /*

            //啟動 notify
            mBluetoothRxCharacter = gatt
                    .getService(UUID.fromString(ServiceUUID))
                    .getCharacteristic(UUID.fromString(RXCharacteristicUUID));
            gatt.setCharacteristicNotification(mBluetoothRxCharacter, true);

            // Write on the config descriptor to be notified when the value changes
            BluetoothGattDescriptor descriptor = mBluetoothRxCharacter.getDescriptor(UUID.fromString(DescriptorUUID));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);


             */


            boolean findService = false;
            List<BluetoothGattService> services = gatt.getServices();
            for (BluetoothGattService gattService : services) {

                final String uuid = gattService.getUuid().toString();
                Log.i(TAG, "Service discovered: " + uuid);

                if (uuid.compareToIgnoreCase(BLEManager.ServiceUUID)!=0){
                    continue;
                }

                findService = true;
                Log.i(TAG, "find service");

                //new ArrayList<HashMap<String, String>>();
                List<BluetoothGattCharacteristic> gattCharacteristics =
                        gattService.getCharacteristics();

                boolean findRx = false, findTx = false;
                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic :
                        gattCharacteristics) {

                    final String charUuid = gattCharacteristic.getUuid().toString();
                    Log.i(TAG, "Characteristic discovered for service: " + charUuid);

                    if (charUuid.compareToIgnoreCase(BLEManager.RXCharacteristicUUID) == 0){
                        Log.i(TAG, "Find Rx");
                        findRx = true;
                        mBluetoothRxCharacter = gattCharacteristic;
                    }else if (charUuid.compareToIgnoreCase(BLEManager.TXCharacteristicUUID)==0){
                        Log.i(TAG, "Find Tx");
                        findTx = true;
                        mBluetoothTxCharacter = gattCharacteristic;
                    }

                    if (findRx && findTx){

                        mBluetoothTxCharacter.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);

                        gatt.setCharacteristicNotification(mBluetoothRxCharacter,true);

                        List<BluetoothGattDescriptor> descriptorList = mBluetoothRxCharacter.getDescriptors();
                        if(descriptorList != null && descriptorList.size() > 0) {
                            for(BluetoothGattDescriptor descriptor : descriptorList) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);

                                //descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                                //mBluetoothGatt.writeDescriptor(descriptor);
                            }
                        }

                        break;
                    }

                }

                if (!findRx || !findTx){
                    Log.i(TAG, "Find tx/rx characteristic failed");
                    gatt.getServices();
                }
            }

            if (!findService){
                Log.i(TAG, "Find service failed");
                gatt.getServices();
            }

        }

        //呼叫mBluetoothGatt.readCharacteristic(characteristic)讀取資料回撥，在這裡面接收資料
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);


            if (status != BluetoothGatt.GATT_SUCCESS){
                /*
                if (mBleCallback!=null){
                    mBleCallback.updateConnDevStatus(BlEDEV_ReadFailed);
                }

                synchronized (mReadDoneLockObject) {
                    mReadDoneLockObject.notify();
                }

                 */
                return;
            }


            mRecvData = characteristic.getValue();
            if (mRecvData != null){
                for (byte b: mRecvData){
                    Log.d(TAG, String.format("%x", b));
                }
            }

            synchronized (mReadDoneLockObject){
                mReadDoneLockObject.notify();
            }

        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status != BluetoothGatt.GATT_SUCCESS){
                Log.i(TAG, "Write failed" + status);

                if (mLastSendData!=null) {
                    Log.i(TAG, "Resend last data package");
                    mBluetoothTxCharacter.setValue(mLastSendData);
                    mBluetoothGatt.writeCharacteristic(mBluetoothTxCharacter);
                }

                return;
            }

            synchronized (mWriteDoneLockObject) {
                mSendRet = true;
                mWriteDoneLockObject.notify();
            }

            Log.i(TAG, SystemClock.uptimeMillis() + " writ done");

        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            synchronized (mReadDoneLockObject) {
                Log.i(TAG, SystemClock.uptimeMillis()  + " onCharacteristicChanged");


                mRecvData = characteristic.getValue();
                mRecvDataPacketArray.add(mRecvData);

                SecuXPaymentUtility.logByteArrayHexValue(mRecvData);

                if (PaymentPacketHandler.isLastPacket(mRecvData)) {
                    Log.i(TAG, "Last packet");
                    mReadDoneLockObject.notify();
                    mRecvLastPacket = true;
                }

                synchronized (mReadDataDoneLockObject){
                    mReadDataDoneLockObject.notify();
                }
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {//descriptor讀
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {//descriptor寫
            super.onDescriptorWrite(gatt, descriptor, status);

            if (mBleCallback!=null)
                mBleCallback.updateConnDevStatus(BlEDEV_ConnDone);

            synchronized (mConnectDoneLockObject) {
                Log.i(TAG, SystemClock.uptimeMillis() + " onDescriptorWrite");
                mConnectDoneLockObject.notify();
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        /*
        //呼叫mBluetoothGatt.readRemoteRssi()時的回撥，rssi即訊號強度
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {//讀Rssi
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }


         */
    };

    /*
    public void connectWithDevice(String devName, Context context){
        //this.stopScan();
        for(int i=0; i<mBleDevArrList.size(); i++){
            BLEDevice devItem = mBleDevArrList.get(i);

            String name = devItem.device.getName();
            if (name.compareTo(devName) == 0){
                this.mBluetoothGatt = devItem.device.connectGatt(context, true, mBluetoothGattCallback);
                break;
            }
        }
    }

     */

    public void disconnectWithDevice(){
        if (this.mBluetoothGatt != null){
            Log.i(TAG, "disconnectWithDevice");
            this.mBluetoothGatt.disconnect();
        }
    }


    public boolean sendData(String str){
        this.mRecvData = null;
        return sendData(str.getBytes());
    }

    public boolean sendData(byte[] data){

        mSendRet = false;
        this.mRecvData = null;
        this.mLastSendData = data;

        if (this.mBluetoothGatt != null){ // && this.mBluetoothTxCharacter!=null){

            BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(ServiceUUID));
            if (service == null){
                return mSendRet;
            }

            mBluetoothTxCharacter = service.getCharacteristic(UUID.fromString(TXCharacteristicUUID));

            Log.i(TAG, "send data ");
            SecuXPaymentUtility.logByteArrayHexValue(data);

            synchronized (mWriteDoneLockObject) {

                int sendStartOffset = 0;
                do {
                    int sendEndOffset = sendStartOffset + Max_BLE_Package_Size;
                    if (sendEndOffset > data.length){
                        sendEndOffset = data.length - 1;
                    }
                    byte[] sendData = Arrays.copyOfRange(data, sendStartOffset, sendEndOffset);

                    Log.i(TAG, "send packet " + sendStartOffset);
                    SecuXPaymentUtility.logByteArrayHexValue(sendData);

                    this.mBluetoothTxCharacter.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    this.mBluetoothTxCharacter.setValue(sendData);
                    mBluetoothGatt.writeCharacteristic(this.mBluetoothTxCharacter);

                    /*
                    try {
                        sleep(10);
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                     */

                    try {
                        mWriteDoneLockObject.wait(5000);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    sendStartOffset += Max_BLE_Package_Size;

                }while (sendStartOffset < data.length);
            }
        }
        return mSendRet;
    }

    public boolean sendPacket(byte[] packet){
        return sendData(packet);
    }

    public byte[] sendCmdRecvData(String cmd){
        return sendCmdRecvData(cmd.getBytes());
    }

    public byte[] sendCmdRecvData(byte[] data){

        Log.i(TAG, SystemClock.uptimeMillis()  +  " sendCmdRecvData ");
        SecuXPaymentUtility.logByteArrayHexValue(data);

        this.mLastSendData = data;
        this.mRecvData = null;

        if (this.mBluetoothGatt != null){ // && this.mBluetoothTxCharacter!=null){

            BluetoothGattService service = mBluetoothGatt.getService(UUID.fromString(ServiceUUID));
            if (service == null){
                return mRecvData;
            }

            mBluetoothTxCharacter = service.getCharacteristic(UUID.fromString(TXCharacteristicUUID));

            synchronized (mWriteDoneLockObject){
                //this.mBluetoothTxCharacter.setValue(cmd);
                /*
                try {
                    Thread.sleep(300);
                } catch (Exception ex) {
                    Log.e(TAG, "Command Delay Error", ex);
                }

                 */
                //mBluetoothGatt.writeCharacteristic(this.mBluetoothTxCharacter);


                int sendStartOffset = 0;
                do {
                    int sendEndOffset = sendStartOffset + Max_BLE_Package_Size;
                    if (sendEndOffset > data.length){
                        sendEndOffset = data.length;
                    }
                    byte[] sendData = Arrays.copyOfRange(data, sendStartOffset, sendEndOffset);

                    Log.i(TAG, "send data");
                    SecuXPaymentUtility.logByteArrayHexValue(sendData);

                    this.mBluetoothTxCharacter.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                    this.mBluetoothTxCharacter.setValue(sendData);
                    mBluetoothGatt.writeCharacteristic(this.mBluetoothTxCharacter);

                    try {
                        mWriteDoneLockObject.wait(5000);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    sendStartOffset += Max_BLE_Package_Size;
                }while (sendStartOffset < data.length);

            }

            //BluetoothGattCharacteristic blechar = mBluetoothGatt.getService(UUID.fromString(BLEManager.ServiceUUID)).getCharacteristic(UUID.fromString(BLEManager.RXCharacteristicUUID));
            //boolean ret = mBluetoothGatt.readCharacteristic(this.mBluetoothRxCharacter);

            Log.i(TAG, SystemClock.uptimeMillis() + " wait for reply");

            synchronized (mReadDataDoneLockObject){
                if (mRecvData==null) {
                    try {
                        mReadDataDoneLockObject.wait(5000);

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            Log.i(TAG, SystemClock.uptimeMillis() + " got reply");
        }
        return mRecvData;
    }

    public byte[] sendPacketRecvReply(byte[] packet){
        this.mRecvLastPacket = false;
        this.mRecvData = null;
        this.mRecvDataPacketArray.clear();

        if (!sendData(packet)){
            return null;
        }

        Log.i(TAG, SystemClock.uptimeMillis() + " wait for reply");

        synchronized (mReadDoneLockObject){

            if (!this.mRecvLastPacket) {
                try {
                    mReadDoneLockObject.wait(5000);

                } catch (InterruptedException e) {
                    e.printStackTrace();

                }
            }
        }

        Log.i(TAG, SystemClock.uptimeMillis() + " got reply " + mRecvDataPacketArray.size());

        if (mRecvDataPacketArray.size()==0)
            return null;

        if (mRecvDataPacketArray.size() == 1)
            return mRecvDataPacketArray.get(0);

        int replyLen = 0;
        for(int i=0; i<mRecvDataPacketArray.size(); i++){
            byte[] reply = mRecvDataPacketArray.get(i);
            replyLen += reply.length;
        }

        Log.i(TAG, "reply len = " + replyLen);

        byte[] packetReply = new byte[replyLen];
        int pos = 0;
        for(int i=0; i<mRecvDataPacketArray.size(); i++){
            byte[] reply = mRecvDataPacketArray.get(i);
            System.arraycopy(reply, 0, packetReply, pos, reply.length);
            pos += reply.length;
        }

        return packetReply;
    }
}
