package com.secuxtech.paymentdevicekit;

import com.secuxtech.secuxpaymentdevicendk.SecuXPaymentPeripheral;

/**
 * Created by maochuns.sun@gmail.com on 2020/5/8
 */

public class SecuXBLEDevice extends BLEDevice {

    public String mDevInfo = "";

    public SecuXPaymentPeripheral mPaymentPeripheral = null;
    public byte[] mValidatePeripheralCommand = null;

    public byte[] getValidatePeripheralCommand(){
        return mValidatePeripheralCommand;
    }
}
