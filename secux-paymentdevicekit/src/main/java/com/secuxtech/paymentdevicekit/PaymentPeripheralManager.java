package com.secuxtech.paymentdevicekit;

import android.bluetooth.BluetoothManager;
import android.content.ContentValues;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;


import com.secuxtech.secuxpaymentdevicendk.SecuXPaymentPeripheral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static com.secuxtech.paymentdevicekit.BLEManager.TAG;

/**
 * Created by maochuns.sun@gmail.com on 2020/4/15
 */
public class PaymentPeripheralManager extends PaymentPeripheralManagerV1{


    private PaymentCommandHandler mCmdHdr = new PaymentCommandHandler();


    private Context                 mContext;
    private int                     mScanTimeout;
    private int                     mConnTimeout;
    private int                     mCheckRSSI;

    private SecuXPaymentPeripheral  mPaymentPeripheral;

    public PaymentPeripheralManager(Context context, int scanTimeout, int checkRSSI, final int connectionTimeout){
        mContext = context;
        mScanTimeout = scanTimeout;
        mConnTimeout = connectionTimeout;
        mCheckRSSI = checkRSSI;
    }

    public Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyDevice(byte[] paykey, Context context, int scanTimeout, String connectDeviceId, int checkRSSI, final int connectionTimeout) {
        Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + " identifyDevice");
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Unknown reason"), null);

        SecuXBLEManager.getInstance().mContext = context;
        SecuXBLEManager.getInstance().setBLEManager((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE));

        SecuXBLEDevice paymentDevice;
        if (SecuXBLEManager.getInstance().mBLEScanStart) {
            paymentDevice = SecuXBLEManager.getInstance().findTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);
        } else {
            paymentDevice = SecuXBLEManager.getInstance().scanForTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);
        }
        //Pair<BluetoothDevice, SecuXPaymentPeripheral> devInfo = SecuXBLEManager.getInstance().scanForTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);

        if (paymentDevice == null) {
            Log.i(ContentValues.TAG, "find device failed!");
            ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Can't find device"), null);
            return ret;
        }

        if (!paymentDevice.mPaymentPeripheral.isValidCodeKey(paykey)){
            Log.i(ContentValues.TAG, "Invalid key code!");
            ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Invalid payment QRCode! QRCode is timeout!"), null);
            return ret;
        }

        return identifyDevice(paymentDevice, scanTimeout);
    }

    public Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyDevice(Context context, int scanTimeout, String connectDeviceId, int checkRSSI, final int connectionTimeout) {
        Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + " identifyDevice");
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Unknown reason"), null);

        SecuXBLEManager.getInstance().mContext = context;
        SecuXBLEManager.getInstance().setBLEManager((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE));

        SecuXBLEDevice paymentDevice;
        if (SecuXBLEManager.getInstance().mBLEScanStart) {
            paymentDevice = SecuXBLEManager.getInstance().findTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);
        } else {
            paymentDevice = SecuXBLEManager.getInstance().scanForTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);
        }
        //Pair<BluetoothDevice, SecuXPaymentPeripheral> devInfo = SecuXBLEManager.getInstance().scanForTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);

        if (paymentDevice == null) {
            Log.i(ContentValues.TAG, "find device failed!");
            ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Can't find device"), null);
            return ret;
        }

        return identifyDevice(paymentDevice, scanTimeout);
    }

    public Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyDevice(SecuXBLEDevice paymentDevice, int scanTimeout) {
        Log.i(ContentValues.TAG, String.valueOf(SystemClock.uptimeMillis()) + " find the device");
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Unknown reason"), null);

        if (SecuXBLEManager.getInstance().connectWithDevice(paymentDevice.device, scanTimeout)){
            Log.i(ContentValues.TAG, String.valueOf(SystemClock.uptimeMillis()) + " Connect with the device done!");

            //For old FW version
            byte[] recvData = null;
            if (paymentDevice.mPaymentPeripheral.isOldVersion) {
                byte[] identifyCmdReply = SecuXBLEManager.getInstance().sendCmdRecvData(paymentDevice.getValidatePeripheralCommand());
                if (identifyCmdReply!=null && identifyCmdReply.length > 1) {
                    recvData = Arrays.copyOfRange(identifyCmdReply, 1, identifyCmdReply.length);
                }
            }else {
                Pair<Integer, byte[]> identifyRet = mCmdHdr.sendIdentifyCmd(paymentDevice.getValidatePeripheralCommand());
                recvData = identifyRet.second;
            }

            if (recvData!=null && recvData.length>0) {

                SecuXPaymentPeripheral paymentPeripheral = paymentDevice.mPaymentPeripheral;
                if (paymentPeripheral.isValidPeripheralIvKey(recvData)) {

                    ivKeyData = Arrays.copyOfRange(recvData, 4, recvData.length);
                    /*
                    String ivKey = SecuXUtility.dataToHexString(ivKeyData);
                    String ivKeyNew = new String(ivKeyData);
                    ivKey = ivKey.toUpperCase();
                    */
                    String ivKey;
                    if (paymentPeripheral.isOldVersion) {
                        ivKey = SecuXPaymentUtility.dataToHexString(ivKeyData);
                        ivKey = ivKey.toUpperCase();
                    }else{
                        ivKey = new String(ivKeyData);
                    }
                    Log.i(ContentValues.TAG, "ivkey=" + ivKey + " " + ivKey);

                    mPaymentPeripheral = paymentPeripheral;
                    //mPaymentPeripheral.isOldVersion = devInfo.second.isOldVersion;
                    ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_OK, ivKey), paymentPeripheral);
                }else {
                    Log.d(ContentValues.TAG, "invalid ivkey data");
                    ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Invalid ivKey"), null);
                }
            }else{
                Log.i(ContentValues.TAG, String.valueOf(SystemClock.uptimeMillis())  + " receive data failed!");
                ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Receive data timeout"), null);
            }
        }else{
            Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + "connect with the device failed!");
            ret = new Pair<>(new Pair<>(SecuX_Peripheral_Operation_fail, "Connect with device timeout"), null);
        }

        return ret;
    }


    public SecuXBLEDevice getDevice(Context context, int scanTimeout, String connectDeviceId, int checkRSSI, final int connectionTimeout) {
        Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + " getDevice");

        SecuXBLEManager.getInstance().mContext = context;
        SecuXBLEManager.getInstance().setBLEManager((BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE));

        SecuXBLEDevice paymentDevice;
        if (SecuXBLEManager.getInstance().mBLEScanStart){
            paymentDevice = SecuXBLEManager.getInstance().findTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);
        }else{
            paymentDevice = SecuXBLEManager.getInstance().scanForTheDevice(connectDeviceId, scanTimeout, checkRSSI, connectionTimeout);
        }

        if (paymentDevice==null){
            Log.i(ContentValues.TAG, "find device failed!");
            return null;
        }

        if (paymentDevice.mPaymentPeripheral.isOldVersion) {
            Log.i(ContentValues.TAG, "Doesn't support old version FW");
            return null;
        }

        Log.i(ContentValues.TAG, String.valueOf(SystemClock.uptimeMillis()) + " find the device");
        if (SecuXBLEManager.getInstance().connectWithDevice(paymentDevice.device, scanTimeout)){
            Log.i(ContentValues.TAG, String.valueOf(SystemClock.uptimeMillis()) + " Connect with the device done!");

            String theInfo = mCmdHdr.sendGetDeviceInfoCmd();
            Log.i(ContentValues.TAG, "Device info = " + theInfo);
            paymentDevice.mDevInfo = theInfo;

            requestDisconnect();
            return paymentDevice;

        }else{
            Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + "connect with the device failed!");
        }

        SecuXBLEManager.getInstance().disconnectWithDevice();
        return null;
    }

    public void requestDisconnect(){
        int disconnRet = mCmdHdr.requestDisconnect();
        Log.i(TAG, "Request disconnect ret = " + disconnRet);

        SecuXBLEManager.getInstance().disconnectWithDevice();
    }

    public Pair<Integer, String> doGetIVKey(String connectDeviceId){
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyRet = identifyDevice(mContext, mScanTimeout, connectDeviceId, mCheckRSSI, mConnTimeout);
        if (identifyRet.first.first != SecuX_Peripheral_Operation_OK){
            requestDisconnect();
            return identifyRet.first;
        }

        if (identifyRet.first.first == SecuX_Peripheral_Operation_OK && !identifyRet.second.isActivated()){
            requestDisconnect();
            return new Pair<>(SecuX_Peripheral_Operation_fail, "Inactivated device!");
        }

        if (!identifyRet.second.isOldVersion && !mCmdHdr.sendSetConnTimeoutCmd(mConnTimeout)) {
            Log.i(ContentValues.TAG, "set timeout failed");
            return new Pair<>(SecuX_Peripheral_Operation_fail, "Set connection timeout failed!");
        }

        return identifyRet.first;
    }


    public Pair<Integer, String> doGetIVKey(String connectDeviceId, byte[] nonce){
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyRet = identifyDevice(nonce, mContext, mScanTimeout, connectDeviceId, mCheckRSSI, mConnTimeout);
        if (identifyRet.first.first != SecuX_Peripheral_Operation_OK){
            requestDisconnect();
            return identifyRet.first;
        }

        if (identifyRet.first.first == SecuX_Peripheral_Operation_OK && !identifyRet.second.isActivated()){
            requestDisconnect();
            return new Pair<>(SecuX_Peripheral_Operation_fail, "Inactivated device!");
        }

        if (!identifyRet.second.isOldVersion && !mCmdHdr.sendSetConnTimeoutCmd(mConnTimeout)) {
            Log.i(ContentValues.TAG, "set timeout failed");
            return new Pair<>(SecuX_Peripheral_Operation_fail, "Set connection timeout failed!");
        }

        return identifyRet.first;
    }

    public Pair<Integer, String> doGetIVKey(Context context, int scanTimeout, String connectDeviceId, int checkRSSI, final int connectionTimeout) {
        mContext = context;
        mScanTimeout = scanTimeout;
        mCheckRSSI = checkRSSI;
        mConnTimeout = connectionTimeout;
        return doGetIVKey(connectDeviceId);
    }

    public Pair<Integer, String> doGetIVKey(byte[] nonce, Context context, int scanTimeout, String connectDeviceId, int checkRSSI, final int connectionTimeout) {
        mContext = context;
        mScanTimeout = scanTimeout;
        mCheckRSSI = checkRSSI;
        mConnTimeout = connectionTimeout;
        return doGetIVKey(connectDeviceId, nonce);
    }


    public Pair<Integer, String>  doPaymentVerification(byte[] encryptedTransactionData, final Map<String, String> machineControlParams) {
        if (isOldFWVersion()){
            return super.doPaymentVerification(encryptedTransactionData, machineControlParams);
        }

        return doPaymentVerification(encryptedTransactionData);
    }

    public Pair<Integer, String>  doPaymentVerification(byte[] encryptedTransactionData){
        Log.i(ContentValues.TAG, "doPaymentVerification ");

        Pair<Integer, String> ret = new Pair<>(SecuX_Peripheral_Operation_fail, "Unknown reason");

        boolean bRet = mCmdHdr.sendLocalTimeCmd();

        int nRet = mCmdHdr.sendConfirmTransactionCmd(encryptedTransactionData);
        Log.i(ContentValues.TAG, "doPaymentVerification ret= " + nRet);
        if (nRet == 0) {
            ret = new Pair<>(SecuX_Peripheral_Operation_OK, "");
        } else {
            ret = new Pair<>(SecuX_Peripheral_Operation_fail, "doPaymentVerification failed! ErrorCode = " + nRet);
        }

        requestDisconnect();

        return ret;
    }

    public Boolean isOldFWVersion(){
        return mPaymentPeripheral.isOldVersion;
    }

    public Pair<Integer, Pair<String, String>> getRefundRefillInfo(Context context, String connectDeviceId, byte[] nonce){
        Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + " getRefundRefillInfo device=" + connectDeviceId + " scanTimeout=" + mScanTimeout + " connectionTimeout=" + mConnTimeout + " Rssi=" + mCheckRSSI);
        Pair<Integer, Pair<String, String>> ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("Unknown reason", ""));
        mContext = context;
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyRet = identifyDevice(nonce, mContext, mScanTimeout, connectDeviceId, mCheckRSSI, mConnTimeout);
        if (identifyRet.first.first != SecuX_Peripheral_Operation_OK) {

            ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>(identifyRet.first.second, ""));
            requestDisconnect();

        }else if (!identifyRet.second.isActivated()){

            ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("Inactivated device", ""));
            requestDisconnect();

        }else {
            Pair<Integer, String> nRet = mCmdHdr.requestRefundDataCmd();
            if (nRet.first == 0) {
                ret = new Pair<>(SecuX_Peripheral_Operation_OK, new Pair<>(nRet.second, identifyRet.first.second));
                if (!mCmdHdr.sendSetConnTimeoutCmd(mConnTimeout)) {
                    Log.i(ContentValues.TAG, "set timeout failed");
                    ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("Set connection timeout failed!", ""));
                    requestDisconnect();
                }

            } else {
                ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("cleanDevPaymentRecords failed! ErrorCode = " + nRet.first, ""));
                requestDisconnect();
            }
        }

        return ret;
    }

    public Pair<Integer, Pair<String, String>> getRefundRefillInfo(Context context, String connectDeviceId){
        Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + " getRefundRefillInfo device=" + connectDeviceId + " scanTimeout=" + mScanTimeout + " connectionTimeout=" + mConnTimeout + " Rssi=" + mCheckRSSI);
        Pair<Integer, Pair<String, String>> ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("Unknown reason", ""));
        mContext = context;
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyRet = identifyDevice(mContext, mScanTimeout, connectDeviceId, mCheckRSSI, mConnTimeout);
        if (identifyRet.first.first != SecuX_Peripheral_Operation_OK) {

            ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>(identifyRet.first.second, ""));
            requestDisconnect();

        }else if (!identifyRet.second.isActivated()){

            ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("Inactivated device", ""));
            requestDisconnect();

        }else {
            Pair<Integer, String> nRet = mCmdHdr.requestRefundDataCmd();
            if (nRet.first == 0) {
                ret = new Pair<>(SecuX_Peripheral_Operation_OK, new Pair<>(nRet.second, identifyRet.first.second));
                if (!mCmdHdr.sendSetConnTimeoutCmd(mConnTimeout)) {
                    Log.i(ContentValues.TAG, "set timeout failed");
                    ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("Set connection timeout failed!", ""));
                    requestDisconnect();
                }

            } else {
                ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("cleanDevPaymentRecords failed! ErrorCode = " + nRet.first, ""));
                requestDisconnect();
            }
        }

        return ret;
    }


    //-----------------------------------------------------------------------------------------------------
    // For testing purpose
    //-----------------------------------------------------------------------------------------------------

    public byte[] sendTestDataToDevice(Context context, String connectDeviceId, String cmdHex){
        Log.i(ContentValues.TAG, SystemClock.uptimeMillis() + " sendTestDataToDevice device=" + connectDeviceId + " scanTimeout=" + mScanTimeout + " connectionTimeout=" + mConnTimeout + " Rssi=" + mCheckRSSI);

        if (cmdHex.length() == 0){
            return null;
        }

        Pair<Integer, Pair<String, String>> ret = new Pair<>(SecuX_Peripheral_Operation_fail, new Pair<>("Unknown reason", ""));
        mContext = context;
        Pair<Pair<Integer, String>, SecuXPaymentPeripheral> identifyRet = identifyDevice(mContext, mScanTimeout, connectDeviceId, mCheckRSSI, mConnTimeout);
        if (identifyRet.first.first != SecuX_Peripheral_Operation_OK) {

            requestDisconnect();

        }else{

            byte[] reply = SecuXBLEManager.getInstance().sendCmdRecvData(SecuXPaymentUtility.hexStringToData(cmdHex));
            return reply;
        }

        return null;
    }


}
