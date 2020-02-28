package com.anuj.btcom;


import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.Set;

public class Comunicate extends Application {
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";

    public static final String DeviceAdd="64:A2:F9:7E:2B:16";

    private ArrayList<String> chatMessages;
    private ArrayList<String> pairedDeviceslist;
    private BluetoothAdapter bluetoothAdapter;

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private ChatController chatController;
    private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> discoveredDevicesAdapter;

    public String msg,status;

    public void Start() {
        Log.d("Unity","Start Function Called");
        pairedDeviceslist = new ArrayList<>();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            msg = "Bluetooth not available!!";
            return;
        }
        chatMessages = new ArrayList<>();

        if (!bluetoothAdapter.isEnabled()) {
            msg = "Please enable Bluetooth";
            Log.d("Unity","Please enable Bluetooth");
        } else {
            chatController = new ChatController(handler);
            Log.d("Unity","Chat Controller Initiated");
        }
    }

    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatController.STATE_CONNECTED:
                            status= "Connected to: " + connectingDevice.getName();
                            break;
                        case ChatController.STATE_CONNECTING:
                            status="Connecting...";
                            break;
                        case ChatController.STATE_LISTEN:
                        case ChatController.STATE_NONE:
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf, 0, msg.arg1);
                    chatMessages.add(readMessage);
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    break;
                case MESSAGE_TOAST:
                    break;
            }
            return false;
        }
    });

    public void searchDevices() {

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);


        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDeviceslist.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDeviceslist.add("No devices Paired");
        }

    }

    public void sendMessage(String message) {
        if (chatController.getState() != ChatController.STATE_CONNECTED) {
            status="Connection Lost";
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            chatController.write(send);
        }
    }

    public void connectToDevice(String deviceAddress){
        Log.d("Unity","Trying to Connect");
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        try{
            chatController.connect(device);
        }catch (Error e){
            msg=e.toString();
        }

    }

    public void Resume(){
        if (chatController != null) {
            if (chatController.getState() == ChatController.STATE_NONE) {
                chatController.start();
            }
        }
    }

    public void Destroy(){
        if (chatController != null)
            chatController.stop();
    }

    public String getMessages(){
        return  chatMessages.get(chatMessages.size()-1);
    }

    public ArrayList getDevices(){
        return pairedDeviceslist;
    }

    public String getMsg(){
        return msg;
    }

    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveredDevicesAdapter.getCount() == 0) {
                    discoveredDevicesAdapter.add("No Devices Found");
                }
            }
        }
    };



}
