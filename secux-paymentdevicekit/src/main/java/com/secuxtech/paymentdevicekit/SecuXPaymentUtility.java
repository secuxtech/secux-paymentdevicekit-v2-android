package com.secuxtech.paymentdevicekit;

import android.util.Log;

import static com.secuxtech.paymentdevicekit.BLEManager.TAG;

/**
 * Created by maochuns.sun@gmail.com on 2020-03-02
 */
public class SecuXPaymentUtility {
    public static String dataToHexString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        String result = stringBuilder.toString();
        return result;
    }

    public static byte[] hexStringToData(String s) {
        int len = s.length();
        byte[] data = new byte[len/2];

        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }

        return data;
    }

    public static void logByteArrayHexValue(byte[] data){
        Log.i(TAG, "====== Byte array value =====");
        if (data!=null){
            String strMsg = "";
            for (byte b: data){
                strMsg += String.format("0x%x ", b);
            }
            Log.i(TAG,  strMsg);
        }else{
            Log.i(TAG, "Null data");
        }
    }

    public static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    public static String getDefaultValue(String inputString, String vlaue) {
        String returnString = "";
        if (isEmpty(inputString) || "null".equals(inputString)) {
            returnString = vlaue;
        } else {
            returnString = inputString;
        }
        return returnString;
    }

    public static void debug(String message) {
        if (BuildConfig.DEBUG) {
            StackTraceElement call = Thread.currentThread().getStackTrace()[3];
            String className = call.getClassName();
            className = className.substring(className.lastIndexOf('.') + 1);
            Log.d(className + "." + call.getMethodName(), message);

        }
    }

    public static int str2Int(String sValue, int iDefaultValue) {
        int iValue = iDefaultValue;
        try {
            iValue = Integer.parseInt(sValue);
        }
        catch (Exception e) {
            iValue = iDefaultValue;
        }

        return iValue;
    }


    public static int str2Int(String sValue) {
        return str2Int(sValue, 0);
    }
}
