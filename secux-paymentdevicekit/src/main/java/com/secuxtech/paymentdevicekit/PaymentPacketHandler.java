package com.secuxtech.paymentdevicekit;

import android.util.Log;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;

import static com.secuxtech.paymentdevicekit.BLEManager.TAG;

/**
 * Created by maochuns.sun@gmail.com on 2020/4/17
 */
public class PaymentPacketHandler {
    public static final Integer PACKET_PAYLOAD_SIZE = 19;
    public static final Integer PACKET_SIZE = PACKET_PAYLOAD_SIZE + 1;
    //public static final Integer PROT_CMD_MAX_SIZE = PACKET_PAYLOAD_SIZE * 12;

    static final byte FIRST_PACKET = (byte)0x40;
    static final byte LAST_PACKET = (byte)0x80;
    static final int FIRST_SEQ = 0;
    static final int LAST_SEQ = 63;

    public static final String Command_Append_Type_Invalid = "Invalid";
    public static final String Command_Append_Type_0 = "0";
    public static final String Command_Append_Type_1 = "1";
    public static final String Command_Append_Type_2 = "2";
    public static final String Command_Append_Type_4 = "4";
    public static final String Command_Append_Type_D = "D";
    public static final String Command_Append_Type_T = "T";

    @StringDef({Command_Append_Type_Invalid, Command_Append_Type_0, Command_Append_Type_1, Command_Append_Type_2,
            Command_Append_Type_4, Command_Append_Type_D, Command_Append_Type_T})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CommandAppendType {}

    public static byte[] generateCommandPacket(byte[] cmdData){
        int numOfPacket = cmdData.length / PACKET_PAYLOAD_SIZE;
        if (cmdData.length % PACKET_PAYLOAD_SIZE != 0){
            numOfPacket += 1;
        }
        byte[] packetData = new byte[numOfPacket*PACKET_SIZE];

        byte[] aPacket = new byte[PACKET_SIZE];
        int seq = FIRST_SEQ;
        int cmdDataIdx = 0;
        aPacket[0] = FIRST_PACKET;
        int cmdDataLen = cmdData.length;
        while(cmdDataLen > 0){
            int payloadLen = (cmdDataLen > PACKET_PAYLOAD_SIZE) ? PACKET_PAYLOAD_SIZE : cmdDataLen;
            System.arraycopy(cmdData, cmdDataIdx*PACKET_PAYLOAD_SIZE, aPacket, 1, payloadLen);

            cmdDataLen -= payloadLen;

            if (cmdDataLen == 0){
                aPacket[0] |= (LAST_PACKET | payloadLen);
            }else{
                aPacket[0] |= seq;
            }

            Log.i(TAG, "" + aPacket[0] + " " + cmdDataIdx);
            System.arraycopy(aPacket, 0, packetData, cmdDataIdx*PACKET_SIZE, PACKET_SIZE);

            aPacket[0] = 0;
            if (++seq > LAST_SEQ){
                seq = FIRST_SEQ + 1;
            }

            cmdDataIdx += 1;
        }

        return packetData;
    }

    public static byte[] getPacketCmdData(byte[] packetData){

        ArrayList<byte[]> packetArray = new ArrayList<>();

        int pos = 0;
        int seq = FIRST_SEQ;
        int packetDataLen = 0;
        boolean validPacketFlag = true;
        boolean lastPacketFlag = false;

        while (!lastPacketFlag && validPacketFlag){
            byte[] aPacket = new byte[PACKET_SIZE];
            int copyLen = PACKET_SIZE;
            if (packetData.length - pos <PACKET_SIZE){
                copyLen = packetData.length - pos;
            }


            System.arraycopy(packetData, pos, aPacket, 0, copyLen);
            Log.i(TAG, "packet " + seq + " data");
            SecuXPaymentUtility.logByteArrayHexValue(aPacket);

            int id = aPacket[0];
            if (seq == FIRST_SEQ && (id&0x40)!=0x40){
                Log.e(TAG, "Invalid first packet");
                packetDataLen += PACKET_PAYLOAD_SIZE;
                validPacketFlag = false;
            }else if ((id & 0x80) == 0x80){
                lastPacketFlag = true;
                int len = id & 0x1F;
                packetDataLen += len;

            }else{
                packetDataLen += PACKET_PAYLOAD_SIZE;
                int packetNo = id & 0x1F;
                if (packetNo != seq) {
                    Log.e(TAG, "Invalid packet, sequence no not match");
                    validPacketFlag = false;
                }
            }

            packetArray.add(aPacket);

            pos += PACKET_SIZE;
            if ( ++seq > LAST_SEQ){
                seq = FIRST_SEQ;
            }


            if (pos > packetData.length && !lastPacketFlag){
                Log.e(TAG, "Invalid packet, no last packet");
                validPacketFlag = false;
            }else if (packetDataLen > packetData.length){
                Log.e(TAG, "Invalid packet length");
                validPacketFlag = false;
            }
        }

        if (!validPacketFlag){
            return null;
        }

        byte[] cmdData = new byte[packetDataLen];
        int copyPos = 0;
        for (int i=0; i< packetArray.size(); i++){
            byte[] packet = packetArray.get(i);

            int copyLen = PACKET_PAYLOAD_SIZE;
            copyPos = i * PACKET_PAYLOAD_SIZE;
            if (i == packetArray.size()-1){
                copyLen = packetDataLen - copyPos;
            }

            System.arraycopy(packet, 1, cmdData, copyPos, copyLen);

        }
        return cmdData;
    }

    public static boolean isLastPacket(byte[] packet){
        return (packet[0] & 0x80) == 0x80;
    }

    public static @CommandAppendType String getCommandType(int cmdID){
        if (cmdID < 0xE0){
            int bitFlag = (cmdID & 0x03);
            if (bitFlag == 0x00){
                return Command_Append_Type_0;
            }else if (bitFlag == 0x01){
                return Command_Append_Type_1;
            }else if (bitFlag == 0x02){
                return Command_Append_Type_2;
            }else if (bitFlag == 0x03){
                return Command_Append_Type_4;
            }
        }else if (cmdID < 0xEF){
            return Command_Append_Type_D;
        }else if (cmdID < 0xFF){
            return Command_Append_Type_T;
        }

        return Command_Append_Type_Invalid;
    }


    //--------------------------------------------------------------------------------------------
    // utility functions
    //--------------------------------------------------------------------------------------------

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }


    //--------------------------------------------------------------------------------------------
    // test functions
    //--------------------------------------------------------------------------------------------

    public static boolean testCmdPacket(){
        int testPacketNo = 10;
        int lastPackLen = 15;
        int cmdDataLen = testPacketNo*PACKET_PAYLOAD_SIZE + lastPackLen;
        byte[] cmdData = new byte[cmdDataLen];
        for(int i=0; i<lastPackLen; i++){
            cmdData[cmdDataLen - i - 1] = (byte)(i+1);
        }

        for (int i=0; i<testPacketNo; i++){
            byte[] aPacket = new byte[PACKET_PAYLOAD_SIZE];
            for (int j=0; j<PACKET_PAYLOAD_SIZE; j++){
                aPacket[j] = (byte)(j+i);
            }
            System.arraycopy(aPacket, 0, cmdData, i*PACKET_PAYLOAD_SIZE, PACKET_PAYLOAD_SIZE);
        }

        Log.i(TAG, cmdData.toString());

        byte[] packet1 = generateCommandPacket(cmdData);
        String packet1Hex = bytesToHex(packet1);
        Log.i(TAG, packet1Hex);

        byte[] oriCmdData = getPacketCmdData(packet1);
        String oriCmdDataHex = bytesToHex(oriCmdData);
        Log.i(TAG, oriCmdDataHex);

        if (Arrays.equals(cmdData, oriCmdData)){
            Log.i(TAG, "successful");
            return true;
        }else{
            Log.i(TAG, "failed");
            return false;
        }
    }
}
