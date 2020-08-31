package com.secuxtech.paymentdevicekit;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;


import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.neovisionaries.bluetooth.ble.advertising.ServiceData;
import com.secuxtech.secuxpaymentdevicendk.SecuXPaymentDevJni;
import com.secuxtech.secuxpaymentdevicendk.SecuXPaymentPeripheral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by maochuns.sun@gmail.com on 2020-02-26
 */
public class SecuXBLEManager extends BLEManager{

    private static SecuXBLEManager instance = null;

    public static SecuXBLEManager getInstance(){
        if (instance == null){
            instance = new SecuXBLEManager();
        }

        return instance;
    }

    private SecuXBLEManager(){

    }

    private String  mDeviceID = "";
    private int     mScanRSSI = -90;
    private int     mConnectionTimeout = 30;  //seconds

    protected ArrayList<SecuXBLEDevice> mPaymentDevArrList = new ArrayList<SecuXBLEDevice>();

    private SecuXPaymentDevJni mDevNdk = new SecuXPaymentDevJni();

    @Override
    public void startScan() {
        SecuXPaymentDeviceKitLogHandler.Log("SecuXBLEManager Start scan");
        mPaymentDevArrList.clear();
        super.startScan();
    }

    public boolean doesBLEScanStart(){
        return mBLEScanStart;
    }

    public SecuXBLEDevice scanForTheDevice(String devID, int scanTimeout, int rssi, int connectionTimeout){
        SecuXPaymentDeviceKitLogHandler.Log("scanForTheDevice dev=" + devID + " timeout=" + scanTimeout);
        SecuXBLEDevice paymentDev = null;
        synchronized (mScanDevDoneLockObject) {
            mDeviceID = devID;
            mDevice = null;
            mScanRSSI = rssi;
            mConnectionTimeout = connectionTimeout;

            startScan();
            try{
                mScanDevDoneLockObject.wait(scanTimeout*1000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
            stopScan();

            for (SecuXBLEDevice dev : mPaymentDevArrList){
                if (dev.deviceID.compareToIgnoreCase(devID)==0){
                    Log.i(TAG, "Find the device!");
                    paymentDev = dev;
                    break;
                }
            }

            if (paymentDev==null){
                String devScanned = "";
                for (SecuXBLEDevice dev : mPaymentDevArrList){
                    devScanned += ","+dev.deviceID;
                }
                SecuXPaymentDeviceKitLogHandler.Log("scanForTheDevice failed. " + devScanned);
            }
        }
        return paymentDev;
    }

    public SecuXBLEDevice findTheDevice(String devID, int scanTimeout, int rssi, int connectionTimeout){
        SecuXPaymentDeviceKitLogHandler.Log("findTheDevice dev=" + devID + " timeout=" + scanTimeout);
        SecuXBLEDevice paymentDev = null;
        //synchronized (mScanDevDoneLockObject) {
        //    mPaymentDevArrList.clear();
        //}
            mDeviceID = devID;
            mDevice = null;
            mScanRSSI = rssi;
            mConnectionTimeout = connectionTimeout;

            Boolean findDev = false;
            for(int i=0; i<scanTimeout*1000; i+=100){
                for (SecuXBLEDevice dev : mPaymentDevArrList){
                    if (dev.deviceID.compareToIgnoreCase(devID)==0){
                        Log.i(TAG, "Find the device!");
                        paymentDev = dev;
                        findDev = true;
                        break;
                    }
                }

                if (findDev){
                    break;
                }else{
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            /*
            try{
                mScanDevDoneLockObject.wait(scanTimeout*1000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
            */

        if (paymentDev==null){
            String devScanned = "";
            for (SecuXBLEDevice dev : mPaymentDevArrList){
                devScanned += ","+dev.deviceID;
            }
            SecuXPaymentDeviceKitLogHandler.Log("scanForTheDevice failed. " + devScanned);
        }

        return paymentDev;
    }


    public boolean connectWithDevice(BluetoothDevice device, int connectTimeout){
        if (mContext!=null){

            synchronized (mConnectDoneLockObject) {
                Log.i(TAG, SystemClock.uptimeMillis() + " ConnectWithDevice");
                mDevice = device;
                mConnectDone = false;
                mBluetoothRxCharacter = null;
                mBluetoothTxCharacter = null;

                this.mBluetoothGatt = device.connectGatt(mContext, false, mBluetoothGattCallback);

                try{
                    mConnectDoneLockObject.wait(connectTimeout*1000);
                }catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
        }

        mConnectDone = true;
        return (mBluetoothTxCharacter!=null && mBluetoothRxCharacter!=null);
    }


    @Override
    protected void handleScanResult(int callbackType, ScanResult result){
        int rssi = result.getRssi();
        if (rssi < mScanRSSI)
            return;

        ScanRecord scanRecord = result.getScanRecord();
        byte[] scanResult = result.getScanRecord().getBytes();

        BluetoothDevice device = result.getDevice();
        SecuXBLEDevice newPaymentDev = new SecuXBLEDevice();
        newPaymentDev.Rssi = rssi;
        newPaymentDev.device = device;


        boolean bFindDev = false;
        if (device!=null && scanResult!=null && scanResult.length>0) { // && device.getName()!=null && device.getName().length()!=0){
            Log.i(TAG, "device " + scanRecord.getDeviceName());

            for (SecuXBLEDevice devItem : mPaymentDevArrList) {
                //System.out.println(cars.get(i));
                if (devItem.device.equals(device)) {
                    bFindDev = true;

                    if (devItem.Rssi != rssi) {
                        devItem.Rssi = rssi;
                        if (mBleCallback != null) {
                            mBleCallback.updateBLEDeviceRssi(devItem);
                        }
                    }

                    break;
                }
            }

            if (!bFindDev) {

                Log.i(TAG, "new device " + mPaymentDevArrList.size() + " " + device.getName() + " " + device.getAddress());

                List<ADStructure> structures = ADPayloadParser.getInstance().parse(scanResult);
                for (ADStructure adStructure : structures) {
                    Log.d(TAG, "adStructure: " + adStructure);
                    //PaymentUtil.debug("adStructure: " + adStructure);

                    if (adStructure instanceof ServiceData) {
                        ServiceData serviceData = (ServiceData) adStructure;
                        //String text_1 = serviceData.toString();
                        byte[] raw = serviceData.getData();
                        if (raw.length < 3) {
                            Log.i(TAG, "Invalid service data!");
                            continue;
                        }
                        byte[] advertisedData = Arrays.copyOfRange(raw, 2, raw.length);
                        //scannedDevice.setAdvertisedData(advertisedData);

                        if (advertisedData != null && advertisedData.length > 0) {
                            String strMsg = "";
                            for (byte b : advertisedData) {
                                strMsg += String.format("%02x ", b);
                            }
                            Log.i(TAG, "advertisedData " + strMsg);

                            //com.secux.payment.cpp.MyNDK mNdk = new com.secux.payment.cpp.MyNDK();
                            //PaymentPeripheral paymentPeripheral = mNdk.createPaymentPeripheralObjectFromNative(advertisedData);
                            //String fwVer = paymentPeripheral.getFirmwareVersion();
                            //String uid = paymentPeripheral.getUniqueId();

                            SecuXPaymentPeripheral paymentPeripheral = mDevNdk.createPaymentPeripheralObjectFromNative(advertisedData);
                            String fwVer = paymentPeripheral.getFirmwareVersion();
                            String uid = paymentPeripheral.getUniqueId();
                            Log.i(TAG, "Dev UUID=" + uid + " FW ver=" + fwVer);

                            String[] verItem = fwVer.split("\\.");
                            if (Integer.parseInt(verItem[0]) > 1) {
                                Log.i(TAG, "Version " + verItem[0] + " using new protocol");
                                newPaymentDev.mValidatePeripheralCommand = mDevNdk.getValidatePeripheralCommand(paymentPeripheral);
                            } else {
                                //return;
                                Log.i(TAG, "Version " + verItem[0] + " using old protocol, conntimeout=" + mConnectionTimeout);
                                paymentPeripheral.isOldVersion = true;
                                newPaymentDev.mValidatePeripheralCommand = mDevNdk.getValidatePeripheralCommandV1(paymentPeripheral, mConnectionTimeout);
                            }
                            newPaymentDev.mPaymentPeripheral = paymentPeripheral;
                            newPaymentDev.deviceID = uid;


                            strMsg = "";
                            for (byte b : newPaymentDev.mValidatePeripheralCommand) {
                                strMsg += String.format("%x ", b);
                            }
                            Log.d(TAG, "mValidatePeripheralCommand " + strMsg);

                            mPaymentDevArrList.add(newPaymentDev);

                            if (uid.compareToIgnoreCase(mDeviceID) == 0) {
                                //mValidatePeripheralCommand = mNdk.getValidatePeripheralCommand(mConnectionTimeout, paymentPeripheral);


                                synchronized (mScanDevDoneLockObject) {
                                    mDevice = device;
                                    mScanDevDoneLockObject.notify();
                                }
                            }
                            break;
                        }
                    }
                }

                if (mBleCallback != null && !bFindDev) {
                    mBleCallback.newBLEDevice(newPaymentDev);
                }


            }
        }

    }

    /*
    mScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {

                int rssi = result.getRssi();
                if (rssi < mScanRSSI)
                    return;

                ScanRecord scanRecord = result.getScanRecord();
                byte[] scanResult = result.getScanRecord().getBytes();

                BluetoothDevice device = result.getDevice();
                SecuXBLEDevice newPaymentDev = new SecuXBLEDevice();
                newPaymentDev.Rssi = rssi;
                newPaymentDev.device = device;


                boolean bFindDev = false;
                if (device!=null && scanResult!=null && scanResult.length>0){ // && device.getName()!=null && device.getName().length()!=0){
                    Log.i(TAG, "device " + scanRecord.getDeviceName() );

                    for (SecuXBLEDevice devItem : mPaymentDevArrList) {
                        //System.out.println(cars.get(i));
                        if (devItem.device.equals(device)){
                            bFindDev = true;

                            if (devItem.Rssi != rssi){
                                devItem.Rssi = rssi;
                                if (mBleCallback!=null){
                                    mBleCallback.updateBLEDeviceRssi(devItem);
                                }
                            }

                            break;
                        }
                    }

                    if (!bFindDev) {

                        Log.i(TAG, "new device " + mPaymentDevArrList.size() + " " + device.getName() + " " + device.getAddress());

                        List<ADStructure> structures = ADPayloadParser.getInstance().parse(scanResult);
                        for (ADStructure adStructure : structures) {
                            Log.d(TAG,"adStructure: " + adStructure);
                            //PaymentUtil.debug("adStructure: " + adStructure);

                            if (adStructure instanceof ServiceData) {
                                ServiceData serviceData = (ServiceData) adStructure;
                                //String text_1 = serviceData.toString();
                                byte[] raw = serviceData.getData();
                                if (raw.length < 3){
                                    Log.i(TAG, "Invalid service data!");
                                    continue;
                                }
                                byte[] advertisedData = Arrays.copyOfRange(raw, 2, raw.length);
                                //scannedDevice.setAdvertisedData(advertisedData);

                                if( advertisedData != null && advertisedData.length > 0 ) {
                                    String strMsg = "";
                                    for (byte b: advertisedData){
                                        strMsg += String.format("%02x ", b);
                                    }
                                    Log.i(TAG, "advertisedData " + strMsg);

                                    //com.secux.payment.cpp.MyNDK mNdk = new com.secux.payment.cpp.MyNDK();
                                    //PaymentPeripheral paymentPeripheral = mNdk.createPaymentPeripheralObjectFromNative(advertisedData);
                                    //String fwVer = paymentPeripheral.getFirmwareVersion();
                                    //String uid = paymentPeripheral.getUniqueId();

                                    SecuXPaymentPeripheral paymentPeripheral = mDevNdk.createPaymentPeripheralObjectFromNative(advertisedData);
                                    String fwVer = paymentPeripheral.getFirmwareVersion();
                                    String uid = paymentPeripheral.getUniqueId();
                                    Log.i(TAG, "Dev UUID=" + uid + " FW ver=" + fwVer);

                                    String[] verItem = fwVer.split("\\.");
                                    if (Integer.parseInt(verItem[0]) > 1){
                                        Log.i(TAG, "Version " + verItem[0] + " using new protocol");
                                        newPaymentDev.mValidatePeripheralCommand = mDevNdk.getValidatePeripheralCommand(paymentPeripheral);
                                    }else{
                                        Log.i(TAG, "Version " + verItem[0] + " using old protocol, conntimeout=" + mConnectionTimeout);
                                        paymentPeripheral.isOldVersion = true;
                                        newPaymentDev.mValidatePeripheralCommand = mDevNdk.getValidatePeripheralCommandV1(paymentPeripheral, mConnectionTimeout);
                                    }
                                    newPaymentDev.mPaymentPeripheral = paymentPeripheral;
                                    newPaymentDev.deviceID = uid;


                                    strMsg = "";
                                    for (byte b: newPaymentDev.mValidatePeripheralCommand){
                                        strMsg += String.format("%x ", b);
                                    }
                                    Log.d(TAG, "mValidatePeripheralCommand " + strMsg);

                                    mPaymentDevArrList.add(newPaymentDev);

                                    if (uid.compareToIgnoreCase(mDeviceID)==0){
                                        //mValidatePeripheralCommand = mNdk.getValidatePeripheralCommand(mConnectionTimeout, paymentPeripheral);


                                        synchronized (mScanDevDoneLockObject) {
                                            mDevice = device;
                                            mScanDevDoneLockObject.notify();
                                        }
                                    }
                                    break;
                                }
                            }
                        }

                        if (mBleCallback != null && !bFindDev){
                            mBleCallback.newBLEDevice(newPaymentDev);
                        }


                    }
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);

            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "scan error " + errorCode);

                stopScan();

                try {
                    Thread.sleep(100); //1000為1秒
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                startScan();
            }
        };
    }
    */

}
