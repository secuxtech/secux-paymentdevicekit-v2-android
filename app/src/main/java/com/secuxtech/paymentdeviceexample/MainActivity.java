package com.secuxtech.paymentdeviceexample;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.secuxtech.paymentdevicekit.*;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


import static com.secuxtech.paymentdevicekit.PaymentPeripheralManager.SecuX_Peripheral_Operation_OK;
import static java.lang.Thread.sleep;


public class MainActivity extends BaseActivity implements PopupMenu.OnMenuItemClickListener {

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private Context mContext = this;
    private PaymentDevListAdapter mAdapter = null;

    Timer mStopScanTimer = new Timer();


    public ArrayList<BLEDevice> mPaymentDevList = new ArrayList<>();

    private boolean mTestRun = true;
    private Button mRescanBtn = null;
    private LinearLayout mMenuBtn = null;


    private PaymentNonceInputDialog mNonceInputDlg = new PaymentNonceInputDialog();

    private BLEDevice       mCurrentSelectDevice = null;
    private NfcAdapter      mNfcAdapter;
    private PendingIntent   mPendingIntent = null;
    private Integer         mTestCount = 0;
    private Boolean         mProcessIntentFlag = false;
    private Boolean         mPressureTestFlag = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //loadSettings();

        mRescanBtn = findViewById(R.id.button_rescan);

        mMenuBtn = findViewById(R.id.llayout_menu_button);

        if (mPressureTestFlag) {
            mRescanBtn.setVisibility(View.INVISIBLE);
            mMenuBtn.setVisibility(View.INVISIBLE);
        }

        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.colorTitle)); // Navigation bar the soft bottom of some phones like nexus and some Samsung note series
        getWindow().setStatusBarColor(ContextCompat.getColor(this,R.color.colorBlack));
//        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.colorTitle)));


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
            }
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            SecuXBLEManager.getInstance().setBleCallback(mBLECallback);

            SecuXBLEManager.getInstance().setBLEManager((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE));
            if (!SecuXBLEManager.getInstance().isBleEnabled()){
                Log.i("BaseActivity", "BLE is not enabled!!");

                //SecuXBLEManager.getInstance().openBlueAsyn();
                Toast.makeText(this, "Please turn on the bluetooth!!", Toast.LENGTH_SHORT).show();
                //finish();
            }else{
                //BLEManager.getInstance().setBleCallback(bleCallback);
            }

        }else{
            Toast.makeText(this, "The phone DOES NOT support BLE!", Toast.LENGTH_SHORT).show();
            finish();
        }

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (null == mNfcAdapter) {
            Toast toast = Toast.makeText(mContext, "No NFC support!", Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER,0,0);
            toast.show();
            //finish();
            //return;
        }else {

            if (!mNfcAdapter.isEnabled()) {
                Toast toast = Toast.makeText(mContext, "Please turn on NFC!", Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                //finish();
                //return;
            }
        }

        /*
        //PaymentPeripheralManagerEx peripheralManager = new PaymentPeripheralManagerEx();
        //peripheralManager.testCommands();

        new Thread(new Runnable() {
            @Override
            public void run() {
                SecuXTerminalServerRequestHandler terminalSvrHdr = new SecuXTerminalServerRequestHandler();
                terminalSvrHdr.testAPIs();
            }
        }).start();

         */

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView_payment_devices);
        recyclerView.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
    }


    @Override
    public void onResume() {
        super.onResume();

        if (mPressureTestFlag) {
            //SecuXBLEManager.getInstance().mPressureTestMode = true;
            SecuXBLEManager.getInstance().startScan();
        }

        if (mPendingIntent == null) {
            mPendingIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        }

        if (mNfcAdapter != null) {
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
        }

    }

    @Override
    public void onPause() {
        super.onPause();

        //SecuXBLEManager.getInstance().stopScan();
        if (mNfcAdapter != null) {
            mNfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            processIntent(intent);
        }
    }

    public byte[] readBytes(InputStream inputStream) throws IOException {
        // this dynamically extends to take the bytes you read
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }

        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }



    private void processIntent(final Intent intent) {
        Log.i(TAG, "processIntent");
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag==null){
            Log.i(TAG, "Empty tag process abort!");
            return;
        }

        if (mCurrentSelectDevice == null){
            showMessage("Please select the device for testing");
            return;
        }

        if (mProcessIntentFlag){
            return;
        }

        //mProcessNFCTag = true;
        Ndef ndef = Ndef.get(tag);

        Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (rawMessages != null) {
            NdefMessage[] messages = new NdefMessage[rawMessages.length];
            for (int i = 0; i < rawMessages.length; i++) {
                messages[i] = (NdefMessage) rawMessages[i];
                //NdefRecord[] record = messages[i].getRecords();

                for (final NdefRecord record : messages[i].getRecords()) {
                    byte[] payload = record.getPayload();
                    String textEncoding = ((payload[0] & 0200) == 0) ? "UTF-8" : "UTF-16";
                    int languageCodeLength = payload[0] & 0077;

                    try {
                        String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
                        String text = new String(payload, languageCodeLength + 1,
                                payload.length - languageCodeLength - 1, textEncoding);

                        //{"amount":"11.5", "coinType":"DCT", "token":"SPC","deviceIDhash":"04793D374185C2167A420D250FFF93F05156350C"}

                        if (text.contains("{") && text.contains("}")) {
                            //showProgressInMain("Parsing...");
                            Log.i(TAG, "NFC info:" + text);
                            mProcessIntentFlag = true;
                            try{
                                JSONObject jsonInfo = new JSONObject(text);
                                final String amount = jsonInfo.getString("amount");
                                final String currency = jsonInfo.getString("coinType");

                                if (amount.length() > 0 || currency.length() > 0) {

                                    mTestCount += 1;
                                    showProgress("Pay " + mTestCount);
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            payToDevice(mCurrentSelectDevice.deviceID, amount, currency);
                                            mProcessIntentFlag = false;
                                        }
                                    }).start();
                                }else{
                                    mProcessIntentFlag = false;
                                    showMessage("Invalid payment info.");
                                }

                            }catch (Exception e){
                                mProcessIntentFlag = false;
                            }
                            return;
                        }
                    }catch (Exception e){
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        }


    }





    public void onRescanButtonClick(View v){
        mStopScanTimer = new Timer();
        mStopScanTimer.schedule(new StopScanTimerTask(), 3000);

        mPaymentDevList.clear();
        if (mAdapter!=null) {
            mAdapter.clearDeviceList();
            mAdapter.notifyDataSetChanged();
        }

        mRescanBtn.setEnabled(false);

        mMenuBtn.setVisibility(View.INVISIBLE);

        showProgress("scan...");
        SecuXBLEManager.getInstance().startScan();
    }

    public boolean payToDevice(List<PaymentDevice> devList, String amount){

        boolean payToDevRet = true;

        PaymentPeripheralManager peripheralManager = new PaymentPeripheralManager(mContext, 10, -80, 30);
        for (PaymentDevice dev : devList) {
            if (!dev.deviceSelected) {
                //Log.i(TAG, "Test " + dev.paymentDev.deviceID);
                continue;
            }

            final String devID = dev.paymentDev.deviceID;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonProgressDialog.setProgressSubTip(devID);
                }
            });

            if (!payToDevice(devID, amount, "DCT:SPC")){
                showAlertInMain("Pay to device " + devID + " failed!");
                payToDevRet = false;
                break;
            }

        }
        return payToDevRet;
    }

    public boolean payToDevice(String devID, String amount, String currency) {
        Log.i(TAG, "payToDevice " + amount + " " + currency);

        PaymentPeripheralManager peripheralManager = new PaymentPeripheralManager(mContext, 20, -80, 30);

        //final String devID = mCurrentSelectDevice.deviceID;

        //peripheralManager.doGetIVKey(mContext, 10000, "4ab10000726b", -80, 10000);
        Pair<Integer, String> getIVKeyret = peripheralManager.doGetIVKey(devID);
        String ivKey = "";
        if (getIVKeyret.first == SecuX_Peripheral_Operation_OK) {
            ivKey = getIVKeyret.second;
            Log.i(TAG, "ivKey=" + ivKey);
        } else {
            Log.i(TAG, "Get ivKey failed");

            showAlertInMain("Payment failed! Get ivKey failed " + getIVKeyret.second);
            return false;
        }

        return payToDevice(peripheralManager, devID, amount, currency, ivKey);
    }

    public boolean payToDevice(PaymentPeripheralManager peripheralManager, String devID, String amount, String currency, String ivKey ){
        boolean payToDevRet = false;
        if (peripheralManager.isOldFWVersion()){
            try {
                JSONObject ioCtrlParamJson = new JSONObject("{\"uart\":\"0\",\"gpio1\":\"0\",\"gpio2\":\"0\",\"gpio31\":\"0\",\"gpio32\":\"0\",\"gpio4\":\"0\",\"gpio4c\":\"0\",\"gpio4cInterval\":\"0\",\"gpio4cCount\":\"0\",\"gpio4dOn\":\"0\",\"gpio4dOff\":\"0\",\"gpio4dInterval\":\"0\",\"runStatus\":\"0\",\"lockStatus\":\"0\"}");

                final MachineIoControlParam machineIoControlParam = new MachineIoControlParam();
                machineIoControlParam.setGpio1(ioCtrlParamJson.getString("gpio1"));
                machineIoControlParam.setGpio2(ioCtrlParamJson.getString("gpio2"));
                machineIoControlParam.setGpio31(ioCtrlParamJson.getString("gpio31"));
                machineIoControlParam.setGpio32(ioCtrlParamJson.getString("gpio32"));
                machineIoControlParam.setGpio4(ioCtrlParamJson.getString("gpio4"));
                machineIoControlParam.setGpio4c(ioCtrlParamJson.getString("gpio4c"));
                machineIoControlParam.setGpio4cCount(ioCtrlParamJson.getString("gpio4cCount"));
                machineIoControlParam.setGpio4cInterval(ioCtrlParamJson.getString("gpio4cInterval"));
                machineIoControlParam.setGpio4dOn(ioCtrlParamJson.getString("gpio4dOn"));
                machineIoControlParam.setGpio4dOff(ioCtrlParamJson.getString("gpio4dOff"));
                machineIoControlParam.setGpio4dInterval(ioCtrlParamJson.getString("gpio4dInterval"));
                machineIoControlParam.setUart(ioCtrlParamJson.getString("uart"));
                machineIoControlParam.setRunStatus(ioCtrlParamJson.getString("runStatus"));
                machineIoControlParam.setLockStatus(ioCtrlParamJson.getString("lockStatus"));

                //String encryptedStr = "91sGnngVALh6Ep3RsEJKhGQEQM2ntJiZxR0cwiQNLT\\/SbZcCVux1WHabIrzqkICsPz3PudpRHnEFwcbiMO7qhA==";
                //final byte[] encryptedData = Base64.decode(encryptedStr, Base64.DEFAULT);
                final byte[] encryptedData = getEncryptMobilePaymentCommand(devID.substring(devID.length() - 8, devID.length()), amount, currency,ivKey, "PA123456789012345678901234567891");


                Pair<Integer, String> ret = peripheralManager.doPaymentVerification(encryptedData, machineIoControlParam);
                if (ret.first != 0) {
                    Log.i(TAG, "Payment failed! " + ret.second);
                } else {
                    Log.i(TAG, "Payment done");
                    showMessageInMain("Payment successful ");
                    payToDevRet = true;
                }
            } catch (Exception e) {
                Log.i(TAG, "Generate io configuration failed!");
            }
        }else {
            try {
                mTerminalID = "3zhkyue5";
                mPaymentKey = "2l03aa4vrguhzt41uxmcy4yl14qqyey0";
                amount = "1";
                currency = "DCT:SPC";
                final byte[] encryptedData = getEncryptMobilePaymentCommand(mTerminalID, amount, currency, ivKey, mPaymentKey);

                Pair<Integer, String> ret = peripheralManager.doPaymentVerification(encryptedData);
                if (ret.first != 0) {
                    Log.i(TAG, "Payment failed! " + ret.second);
                    //showMessageInMain("Payment failed! " + ret.second);
                } else {
                    Log.i(TAG, "Payment done");
                    payToDevRet = true;

                    showMessageInMain("Payment successful ");
                }

            } catch (Exception e) {
                Log.i(TAG, "Generate io configuration failed!");
            }

        }

        hideProgressInMain();

        if (!payToDevRet){
            showAlertInMain("Payment failed!");
        }

        return payToDevRet;
    }

    public void startPaymentTest() {
        //SecuXBLEManager.getInstance().stopScan();

        if (mAdapter == null){
            return;
        }

        boolean devSelected = false;
        for (PaymentDevice dev : mAdapter.getDevices()) {
            if (dev.deviceSelected) {
                devSelected = true;
                break;
            }
        }

        if (!devSelected){
            showMessage("Please select the device for testing");
            return;
        }

        mTestRun = true;
        CommonProgressDialog.showProgressDialog(mContext, "Round 1"); // / " + Setting.getInstance().mTestCount);
        new Thread(new Runnable() {

            @Override
            public void run() {
                int idx = 2;
                boolean runFlag = true;

                List<PaymentDevice> devList = new ArrayList<>();
                devList.addAll(mAdapter.getDevices());

                while(idx < 3){
                    //Log.i(TAG, "Round " + i);


                    if (!payToDevice(devList, String.valueOf(idx))){
                        break;
                    }

                    try {
                        sleep(2000);
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                    if (!runFlag || !mTestRun)
                        break;

                    final int currIdx = idx;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            CommonProgressDialog.setProgressTip("Round " + currIdx); // + " / " + Setting.getInstance().mTestCount);
                        }
                    });

                    idx += 1;

                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        CommonProgressDialog.dismiss();
                    }
                });
            }
        }).start();

    }

    public void onStopTestButtonClick(View v) {
        mTestRun = false;
    }



    private BLEManagerCallback mBLECallback = new BLEManagerCallback() {
        @Override
        public void newBLEDevice(BLEDevice device) {
            super.newBLEDevice(device);

            synchronized (mContext) {
                int lastCount = mPaymentDevList.size();

                if (lastCount == 0) {
                    mPaymentDevList.add(device);
                    RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView_payment_devices);
                    mAdapter = new PaymentDevListAdapter(mContext, mPaymentDevList);
                    mAdapter.mCallback = mDevListCallback;
                    recyclerView.setAdapter(mAdapter);
                } else {

                    for (BLEDevice dev : mPaymentDevList) {
                        if (dev.deviceID.compareToIgnoreCase(device.deviceID) == 0) {
                            return;
                        }
                    }

                    mPaymentDevList.add(device);
                    mAdapter.addNewDevice(device);
                    mAdapter.notifyItemRangeInserted(lastCount, mPaymentDevList.size());
                }
            }
        }

        @Override
        public void updateBLEDeviceRssi(BLEDevice device){

        }

        @Override
        public void updateConnDevStatus(int status){

        }
    };

    public class StopScanTimerTask extends TimerTask
    {
        public void run()
        {
            SecuXBLEManager.getInstance().stopScan();
            mStopScanTimer.cancel();
            hideProgressInMain();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRescanBtn.setEnabled(true);
                    mMenuBtn.setVisibility(View.VISIBLE);
                }
            });

        }
    };

    private PaymentDevListAdapterCallback mDevListCallback = new PaymentDevListAdapterCallback() {
        @Override
        public void onItemTapped(BLEDevice device) {
            mCurrentSelectDevice = device;
            mTestCount = 0;

        }
    };


    public void onMenuButtonClick(View v){

        if (mAdapter!=null && mAdapter.getSelectedDevices().size() == 0){
            showAlert("Please select a device!");
            return;
        }

        Context wrapper = new ContextThemeWrapper(this, R.style.MainPopupMenuStyle);
        PopupMenu popup = new PopupMenu(wrapper, v);

        //PopupMenu popup = new PopupMenu(this, v);

        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu_main, popup.getMenu());
        popup.setOnMenuItemClickListener(this);
        popup.show();
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {

        switch (item.getItemId()) {


            case R.id.menu_item_payment:
                startPaymentTest();
                break;

            case R.id.menu_item_payment_verify:
                mNonceInputDlg.show(mContext);
                break;

        }
        return false;
    }

    public void onInputNonceCancelButtonClick(View v){
        if (mNonceInputDlg.isShown()){
            mNonceInputDlg.dismiss();
        }
    }

    public void onInputNonceOkButtonClick(View v){
        if (mNonceInputDlg.isShown()){
            mNonceInputDlg.dismiss();

            String cmd = mNonceInputDlg.getNonce();
            cmd = cmd.replace(" ", "");
            if (cmd.length() == 0){
                this.showMessage("No nonce data!");
                return;
            }

            final byte[] code = SecuXPaymentUtility.hexStringToData(cmd);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    List<PaymentDevice> devList = mAdapter.getSelectedDevices();
                    PaymentPeripheralManager peripheralManager = new PaymentPeripheralManager(mContext, 10, -70, 30);
                    for (PaymentDevice dev : devList) {

                        Pair<Integer, String> ret = peripheralManager.doGetIVKey(dev.paymentDev.deviceID, code);
                        if (ret.first == SecuX_Peripheral_Operation_OK){
                            if (payToDevice(peripheralManager, dev.paymentDev.deviceID, "20", "DCT:SPC", ret.second)){
                                showMessageInMain("Payment successfully");
                            }else{
                                showAlertInMain("Payment failed!");
                            }
                        }else{
                            showMessageInMain(ret.second);
                        }

                    }
                }
            }).start();
        }
    }

}
