package com.secuxtech.secuxpaymentdevicendk;

/**
 * Created by maochuns.sun@gmail.com on 2020/4/20
 */
public class SecuXPaymentDevJni {

    static {
        System.loadLibrary("secux-payment-lib");
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public native SecuXPaymentPeripheral createPaymentPeripheralObjectFromNative(byte[] data);
    public native byte[] getValidatePeripheralCommand(SecuXPaymentPeripheral peripheralObject);
    public native byte[] getValidatePeripheralCommandV1(SecuXPaymentPeripheral peripheralObject, int timeout);
}
