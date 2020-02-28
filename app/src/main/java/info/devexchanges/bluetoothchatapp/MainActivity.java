package info.devexchanges.bluetoothchatapp;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.ObservableArrayList;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;



public class MainActivity extends AppCompatActivity {

    private TextView status;
    private Button btnConnect;
    private ListView listView;
    private Dialog dialog;
    private TextInputLayout inputLayout;
    private ArrayAdapter<String> chatAdapter;
    private ObservableArrayList<String> chatMessages;
    private ArrayList<Boolean> responses;
    private BluetoothAdapter bluetoothAdapter;
    Bitmap b;
    Canvas c;
    ImageView v;
    Paint p;
    int count, i[] = {0}, j[] = {0};

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";
    public static final String pico_tag = "Pico";

    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private ChatController chatController;
    private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> discoveredDevicesAdapter;

    SurfaceView sv;
    LinearLayout lyCanvas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        findViewsByIds();

        //check device support bluetooth or not
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }

        //show bluetooth devices dialog when click connect button
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPrinterPickDialog();
            }
        });

        //set chat adapter
        chatMessages = new ObservableArrayList<>();
        responses = new ArrayList<>();
        chatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatMessages);
        listView.setAdapter(chatAdapter);

        v = findViewById(R.id.imageView);
        b = Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        c = new Canvas(b);
        p = new Paint();
        count = c.save();
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.BLACK);
        c.drawCircle(10,20,10,p);
        v.setImageBitmap(b);
    }


    private Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatController.STATE_CONNECTED:
                            setStatus("Connected to: " + connectingDevice.getName());
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_CONNECTING:
                            setStatus("Connecting...");
                            btnConnect.setEnabled(false);
                            break;
                        case ChatController.STATE_LISTEN:
                        case ChatController.STATE_NONE:
                            setStatus("Not connected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;

                    String writeMessage = new String(writeBuf);
                    chatMessages.add(writeMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    chatMessages.add(readMessage);
                    msgRead(readMessage);
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private void showPrinterPickDialog() {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        //Initializing bluetooth adapters
        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        //locate listviews and attatch the adapters
        ListView listView = dialog.findViewById(R.id.pairedDeviceList);
        ListView listView2 = dialog.findViewById(R.id.discoveredDeviceList);
        listView.setAdapter(pairedDevicesAdapter);
        listView2.setAdapter(discoveredDevicesAdapter);

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryFinishReceiver, filter);


        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryFinishReceiver, filter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesAdapter.add(getString(R.string.none_paired));
        }

        //Handling listview item click event
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }

        });

        listView2.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                bluetoothAdapter.cancelDiscovery();
                String info = ((TextView) view).getText().toString();
                String address = info.substring(info.length() - 17);

                connectToDevice(address);
                dialog.dismiss();
            }
        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void connectToDevice(String deviceAddress) {
        bluetoothAdapter.cancelDiscovery();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        chatController.connect(device);
    }

    private void findViewsByIds() {
        status = findViewById(R.id.status);
        btnConnect = findViewById(R.id.btn_connect);
        listView = findViewById(R.id.list);
        inputLayout = findViewById(R.id.input_layout);
        View btnSend = findViewById(R.id.btn_send);

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //      Random rnd = new Random();
//                    int co = Color.argb(255,rnd.nextInt(256),rnd.nextInt(256),rnd.nextInt(256));
                    sendPoints();
                    inputLayout.getEditText().setText("");

            }
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH:
                if (resultCode == Activity.RESULT_OK) {
                    chatController = new ChatController(this, handler);
                } else {
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void sendMessage(String message) {
        if (chatController.getState() != ChatController.STATE_CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            chatController.write(send);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            chatController = new ChatController(this, handler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (chatController != null) {
            if (chatController.getState() == ChatController.STATE_NONE) {
                chatController.start();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (chatController != null)
            chatController.stop();
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
                    discoveredDevicesAdapter.add(getString(R.string.none_found));
                }
            }
        }
    };
    void sendPoints(){
        i[0] = 1;
        j[0] = 0;
        int val = new Random().nextInt(256);
        int co = Color.rgb(val,val,val);
        sendMessage(Integer.toHexString(co).substring(1)+",l0,"+new Random().nextFloat()*3.0);
    }

    void msgRead(String s){
        int va = new Random().nextInt(256);
        int col = Color.rgb(va,va,va);
        String colo = Integer.toHexString(col).substring(1);
        double size = new Random().nextFloat()*3.0;
        if(s.startsWith(pico_tag) && i[0]<=31){
            if(s.substring(s.indexOf(":")+1).equalsIgnoreCase("Yes")){
                responses.add(true);
                if(i[0]<=15)
                {
                    sendMessage(colo+",l"+ i[0]++ +","+size);
                }
                else
                {
                    sendMessage(colo+",r"+ j[0]++ +","+size);
                    i[0]++;
                }
            }
            else if(s.substring(s.indexOf(":")+1).equalsIgnoreCase("No")){
                responses.add(false);
                if(i[0]<=15)
                    sendMessage(colo+",l"+ i[0]++ +","+size);
                else
                {
                    sendMessage(colo+",r"+ j[0]++ +","+size);
                    i[0]++;
                }
            }

        }
        else if(s.startsWith("x")){
            c.restoreToCount(count);
            c.drawColor(Color.WHITE);
            int x = Integer.parseInt(s.substring(s.indexOf("x")+1,s.indexOf("x")+3));
            int y = Integer.parseInt(s.substring(s.indexOf("y")+1,s.indexOf("y")+3));
            c.drawCircle(x,y,10,p);
            v.setImageBitmap(b);
        }
        else{
            sendMessage("received");
        }
    }

}