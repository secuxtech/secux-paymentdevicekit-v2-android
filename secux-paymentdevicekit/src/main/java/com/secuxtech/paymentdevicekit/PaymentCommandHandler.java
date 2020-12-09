package com.secuxtech.paymentdevicekit;

import android.util.Log;
import android.util.Pair;

//import androidx.annotation.IntDef;
//import androidx.annotation.StringDef;

//import java.lang.annotation.Retention;
//import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import static com.secuxtech.paymentdevicekit.BLEManager.TAG;
import static com.secuxtech.paymentdevicekit.PaymentPacketHandler.Command_Append_Type_D;

/**
 * Created by maochuns.sun@gmail.com on 2020/4/15
 */
public class PaymentCommandHandler {

    private boolean testFlag = false;
    private boolean showCmdLog = true;

    public static final int REPLY_RET_RESULT = 0x06;
    public static final int REPLY_RET_BLOCK = 0xE1;
    public static final int REPLY_RET_TEXT = 0xF1;

    /*
    @IntDef({REPLY_RET_RESULT, REPLY_RET_BLOCK, REPLY_RET_TEXT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DeviceReplyRet {}

     */

    public Pair<Integer, String> poll(){
        int cmdID = 0x14;
        byte[] reply = sendPacketRecvReply(cmdID, null);
        if (reply == null)
            return new Pair<>(-1, "");
        else if (reply[0] == (byte)REPLY_RET_RESULT)
            return new Pair<>((int)reply[2], "");
        else if (reply[0] == (byte)REPLY_RET_TEXT)
            return new Pair<>(0, new String(reply, 1, reply.length-1));

        return new Pair<>(-2, "");
    }

    public Pair<Integer, byte[]> sendIdentifyCmd(byte[] hashData){
        Log.i(TAG, "sendIdentifyCmd");
        int cmdID = 0xE2;
        byte[] reply = sendPacketRecvReply(cmdID, hashData);

        if (reply == null)
            return new Pair<>(-1, null);
        else if (reply.length < 3 || reply[0] != (byte)cmdID)
            return new Pair<>(-2, null);
        else {
            int len = reply[1];
            byte[] keyByte = new byte[len];
            System.arraycopy(reply, 2, keyByte, 0, len);
            return new Pair<>(0, keyByte);
        }
    }

    //Transaction
    public int sendConfirmTransactionCmd(byte[] encData){
        int cmdID = 0xE5;
        byte[] reply = sendPacketRecvReply(cmdID, encData);
        if (reply == null)
            return -1;
        else if (reply.length != 3 || reply[0] != (byte)REPLY_RET_RESULT || reply[1] != (byte)cmdID)
            return -2;
        else
            return reply[2];
    }

    public boolean sendLocalTimeCmd(){
        int cmdID = 0xF6;
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String currentTime = new SimpleDateFormat("HHmmss", Locale.getDefault()).format(new Date());

        String currentDateTime = currentDate + currentTime;
        Log.i(TAG, "sendLocalTimeCmd " + currentDateTime);

        //return sendPacket(cmdID, currentDateTime.getBytes());

        byte[] info = new byte[currentDateTime.length() + 1];
        System.arraycopy(currentDateTime.getBytes(), 0, info, 0, currentDateTime.length());
        info[currentDateTime.length()] = 0;

        return sendPacket(cmdID, info);
    }

    public Pair<Integer, String[]> sendDumpTransactionData(int numOfTransItem){
        int cmdID = 0x05;
        byte[] cmdData = new byte[1];
        cmdData[0] = (byte)numOfTransItem;
        byte[] reply = sendPacketRecvReply(cmdID, cmdData);
        if (reply == null)
            return new Pair<>(-1, null);
        else if (reply[0] != (byte)REPLY_RET_RESULT)
            return new Pair<>(-2, null);
        else
            return new Pair<>(0, null);
    }

    public int sendTransactionCancelCmd(){
        Log.i(TAG, "requestTransactionCancel");
        int cmdID = 0x18;
        byte[] reply = sendPacketRecvReply(cmdID, null);
        if (reply == null)
            return -1;
        else if (reply.length != 3 || reply[0] != (byte)REPLY_RET_RESULT || reply[1] != (byte)cmdID)
            return -2;
        else
            return reply[2];
    }


    public int sendPaymentCodeCmd(byte code1, byte code2){
        Log.i(TAG, "sendPaymentCodeCmd");
        int cmdID = 0x07;
        byte[] cmdData = new byte[4];
        cmdData[0] = code1;
        cmdData[1] = code2;
        cmdData[2] = 0;
        cmdData[3] = 0;
        byte[] reply = sendPacketRecvReply(cmdID, cmdData);
        if (reply == null)
            return -1;
        else if (reply.length != 3 || reply[0] != (byte)REPLY_RET_RESULT || reply[1] != (byte)cmdID)
            return -2;
        else
            return reply[2];
    }

    //System Control

    public String sendGetDeviceInfoCmd(){
        Log.i(TAG, "sendGetDeviceInfoCmd");
        int cmdID = 0x08;
        byte[] reply = sendPacketRecvReply(cmdID, null);
        String devInfo = "";
        if (reply!=null && reply[0] == (byte)REPLY_RET_TEXT) {
            devInfo = new String(reply, 1, reply.length-1);
        }
        return devInfo;
    }


    //Connection Control

    public boolean sendSetConnTimeoutCmd(int timeoutSecond){
        Log.i(TAG, "sendSetConnTimeoutCmd");
        byte[] data = new byte[1];
        data[0] = (byte)timeoutSecond;
        return sendPacket(0x09, data);
    }

    public int requestDisconnect(){
        Log.i(TAG, "requestDisconnect");
        int cmdID = 0x10;
        byte[] reply = sendPacketRecvReply(cmdID, null);
        if (reply == null)
            return -1;
        else if (reply.length != 3 || reply[0] != (byte)REPLY_RET_RESULT || reply[1] != (byte)cmdID)
            return -2;
        else
            return reply[2];
    }

    public boolean recvInvalidPacket(){
        return true;
    }

    public Pair<Integer, String> requestRefundDataCmd(){
        Log.i(TAG, "requestRefundDataCmd");
        int cmdID = 0x20;
        byte[] reply = sendPacketRecvReply(cmdID, null);
        if (reply == null)
            return new Pair<>(-1, "");
        else if (reply.length == 3 && reply[0] == (byte)REPLY_RET_RESULT){
            if (reply[1] == (byte)cmdID)
                return new Pair<>(-2, "");
            else
                return new Pair<>((int)reply[2], "");
        }else if (reply.length > 1 && reply[0] == (byte)REPLY_RET_TEXT) {
            return new Pair<>(0, new String(reply, 1, reply.length-2));
        }

        return new Pair<>(-3, "");
    }

    //--------------------------------------------------------------------------------------------------
    // Packet operations
    //--------------------------------------------------------------------------------------------------

    private byte[] generateCmdPacket(int cmdID, byte[] data){
        int dataLen = 1;
        if (data != null) {
            dataLen += data.length;
        }

        int dataPos = 1;
        int dLen = 0;
        if (PaymentPacketHandler.getCommandType(cmdID) == Command_Append_Type_D){
            dLen = data.length;
            dataLen += 1;
            dataPos += 1;
        }

        byte[] cmdData = new byte[dataLen];
        cmdData[0] = (byte)cmdID;

        if (dLen > 0){
            cmdData[1] = (byte)dLen;
        }

        if (data != null){
            System.arraycopy(data, 0, cmdData, dataPos, data.length);
        }
        byte[] packet = PaymentPacketHandler.generateCommandPacket(cmdData);
        return packet;
    }

    private boolean sendPacket(int cmdID, byte[] data){

        byte[] packet = generateCmdPacket(cmdID, data);

        if (showCmdLog){
            Log.i(TAG,  " sendPacket " + packet.length);
            SecuXPaymentUtility.logByteArrayHexValue(packet);
        }

        if (testFlag){
            return true;
        }

        return SecuXBLEManager.getInstance().sendPacket(packet);
    }

    private byte[] sendPacketRecvReply(int cmdID, byte[] data){

        byte[] packet = generateCmdPacket(cmdID, data);

        if (showCmdLog){
            Log.i(TAG, "sendPacket");
            SecuXPaymentUtility.logByteArrayHexValue(packet);
        }

        if (testFlag){
            byte[] replyResult = {0x06, 0x00, 0x00};
            switch (cmdID){
                case 0xE3:
                case 0x18:
                case 0xE5:
                case 0x04:
                case 0xE6:
                case 0xE7:
                case 0x10:
                    replyResult[1] = (byte)cmdID;
                    break;

                case 0x08:
                    String devInfo = "MODEL:P20, CRYPTO:1,SN:1A20B0000001,FV:0127";
                    byte[] reply = new byte[devInfo.length()+1];
                    reply[0] = (byte)REPLY_RET_TEXT;
                    System.arraycopy(devInfo.getBytes(), 0, reply, 1, devInfo.length());

                    Log.i(TAG, "recvReply");
                    SecuXPaymentUtility.logByteArrayHexValue(reply);

                    return reply;

            }
            Log.i(TAG, "recvReply");
            SecuXPaymentUtility.logByteArrayHexValue(replyResult);
            return replyResult;
        }

        byte[] reply = SecuXBLEManager.getInstance().sendPacketRecvReply(packet);
        if (showCmdLog){
            Log.i(TAG, "recvReply");
            SecuXPaymentUtility.logByteArrayHexValue(reply);
        }

        if (reply != null) {
            byte[] replyCmdData = PaymentPacketHandler.getPacketCmdData(reply);
            if (showCmdLog) {
                Log.i(TAG, "reply data: ");
                SecuXPaymentUtility.logByteArrayHexValue(replyCmdData);
            }

            return replyCmdData;
        }

        return null;
    }

    //--------------------------------------------------------------------------------------------------
    // test functions
    //--------------------------------------------------------------------------------------------------

    public void testCommands(){
        testFlag = true;
        showCmdLog = true;

        Log.i(TAG, "Test setConnTimeout");
        boolean setTimeoutRet = sendSetConnTimeoutCmd(15);
        Log.i(TAG, "setConnTimeout done ret=" + setTimeoutRet);

        Log.i(TAG, "Test getDeviceInfo");
        String info = sendGetDeviceInfoCmd();
        Log.i(TAG, "getDeviceInfo info=" + info);

        Log.i(TAG, "Test requestDisconnect");
        int ret = requestDisconnect();
        Log.i(TAG, "requestDisconnect ret=" + ret);

        testFlag = false;
    }
}
