package com.secuxtech.paymentdeviceexample;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.secuxtech.paymentdevicekit.SecuXPaymentUtility;

import org.json.JSONArray;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by maochuns.sun@gmail.com on 2020/4/28
 */
public class BaseActivity extends AppCompatActivity {

    protected static final String TAG = "P20P22 Test Tool";
    protected Context mContext = this;

    private JSONArray mDevInfoJsonArr = new JSONArray();

    String mTerminalID = "gkn3p0ec"; //b4f2xvql"; //
    String mPaymentKey = "asz2gorm5bxh5nc5ecjjsqqstgnlsxsj"; // "xsi2moow3t2afl5up12tdtppjwa1kcwx"; //

    public void showProgress(String info){
        CommonProgressDialog.showProgressDialog(mContext, info);

    }

    public void showProgressInMain(String info){
        final String msgInfo = info;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CommonProgressDialog.showProgressDialog(mContext, msgInfo);
            }
        });
    }

    public void hideProgressInMain(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CommonProgressDialog.dismiss();
            }
        });
    }

    public void showMessage(String msg){
        Toast toast = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER,0,0);
        toast.show();
    }

    public void showMessageInMain(final String msg){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER,0,0);
                toast.show();
            }
        });
    }

    protected void showAlert(String msg){
        AlertDialog alertDialog = new AlertDialog.Builder(mContext).create();
        alertDialog.setTitle("Error");
        alertDialog.setMessage(msg);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    protected void showAlertInMain(final String msg){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showAlert(msg);
            }
        });
    }

    public void checkBluetoothSetting(){
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            Toast toast = Toast.makeText(mContext, "The phone DOES NOT support bluetooth! APP will terminate!", Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
            finish();
            return;
        } else if (!mBluetoothAdapter.isEnabled()) {
            // Bluetooth is not enabled :)
            Toast toast = Toast.makeText(mContext, "Please turn on Bluetooth!", Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
        }
    }

    public void saveSettings(){
        SharedPreferences sharedPreferences = mContext.getApplicationContext().getSharedPreferences("SecuXEvPay", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("DeviceInfo", mDevInfoJsonArr.toString());
        editor.apply();
    }

    public void loadSettings() {
        SharedPreferences settings = mContext.getApplicationContext().getSharedPreferences("SecuXEvPay", MODE_PRIVATE);
        String info = settings.getString("DeviceInfo", "");
        try {
            mDevInfoJsonArr = new JSONArray(info);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //For testing payment data
    public byte[] getEncryptMobilePaymentCommand(String terminalId, String amount, String ivKey, String cryptKey) {
        return getEncryptMobilePaymentCommand(terminalId, amount, "DCT:SPC", ivKey, cryptKey);
    }

    public byte[] getEncryptMobilePaymentCommand(String terminalId, String amount, String currency, String ivKey, String cryptKey){

        String plainTransaction = getMobilePaymentCommand(terminalId, amount, currency);
        Log.d(TAG,"getEncryptMobilePaymentCommand() " + plainTransaction);

        // AES 256 crypt
        byte[] encrytedTransactionData = null;
        try {
            byte[] ivKeyData = ivKey.getBytes();
            //byte[] ivKeyData = SecuXUtility.hexStringToData(ivKey);
            Log.i(TAG, SecuXPaymentUtility.dataToHexString(ivKeyData));
            encrytedTransactionData = encrypt(ivKeyData, cryptKey.getBytes(), plainTransaction.getBytes());
        }catch (Exception ex) {
            encrytedTransactionData = null;
        }

        return encrytedTransactionData;
    }


    public byte[] encrypt(byte[] ivBytes, byte[] keyBytes, byte[] textBytes)
            throws java.io.UnsupportedEncodingException,
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException,
            IllegalBlockSizeException,
            BadPaddingException {

        AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
        SecretKeySpec newKey = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = null;
        cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, newKey, ivSpec);
        return cipher.doFinal(textBytes);
    }

    public String getMobilePaymentCommand(String terminalId, String amount, String amountCurrency) {
        Log.d(TAG,"======= getMobilePaymentCommand() " + amount + " " + amountCurrency + " =======");
        Calendar c = Calendar.getInstance();
        SimpleDateFormat format1 = new SimpleDateFormat("yyyyMMddHHmmss");
        String transactionTime = format1.format(c.getTime());
        String transactionID = "T123456789012345";
        String amountString = amount; //convertAmountToFourDigits(amount);

        String plainTransaction = transactionTime + "," + transactionID + "," + terminalId + "," + amountString+","+amountCurrency;

        Log.d(TAG,"plainTransaction:"+plainTransaction);
        return plainTransaction;
    }


    private String convertAmountToFourDigits(String amount) {
        int length = String.valueOf(amount).length();

        String amountStr = amount+"";
        int Remaining = 8-length;
        for(int i=0;i<Remaining;i++){
            amountStr = " "+amountStr;
        }

//        if (length == 1) {
//            return "   " + amount;
//        }
//
//        if (length == 2) {
//            return "  " + amount;
//        }
//
//        if (length == 3) {
//            return " " + amount;
//        }

        // length is 8
        Log.d(TAG,"convertAmountToFourDigits: "+amountStr);
        Log.d(TAG,"convertAmountToFourDigits: "+amountStr.length());
        return amountStr;
    }
}
