package com.secuxtech.paymentdevicekit;

import android.util.Log;

public class SecuXPaymentDeviceKitLogHandler {

    static SecuXPaymentDeviceKitLogHandlerCallback mLogCallback = null;

    static public void Log(String msg){

        if (msg!=null && msg.length()>0){
            Log.i("SecuXPaymentKit", msg);

            if (mLogCallback != null) {
                mLogCallback.logFromSecuXPaymentDeviceKit(msg);
            }
        }
    }

    static public void setCallback(SecuXPaymentDeviceKitLogHandlerCallback callback){
        mLogCallback = callback;
    }
}
