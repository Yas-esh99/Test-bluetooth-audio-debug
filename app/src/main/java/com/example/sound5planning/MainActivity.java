
package com.example.sound5planning;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.sound5planning.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private final String espAdd = "A0:B7:65:0F:67:1E";
    private UUID ESP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private ExecutorService executorService; // Executor for background tasks

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        executorService = Executors.newSingleThreadExecutor(); // Initialize executor

        checkPermission();

        binding.btnTurnOn.setOnClickListener(v -> {
            if (checkBluetoothSupport()) {
                seText("Bluetooth is supported");
                if (checkBluetoothEnable()) {
                    seText("Bluetooth is already enabled");
                } else {
                    seText("Enabling Bluetooth");
                    enableBluetooth();
                }
            } else {
                seText("Bluetooth not supported");
            }
        });

        binding.btnConnect.setOnClickListener(v -> {
            if (checkBluetoothEnable()) {
                BluetoothDevice device = getEspDevice();
                if (checkEspDevice(device)) {
                    seText("Device found");
                    if (checkEspBonded()) {
                        seText("ESP is bonded");
                        if (checkEspConnected(bluetoothSocket)) {
                            seText("ESP is already connected");
                        } else {
                            connectEsp(device);
                        }
                    } else {
                        seText("Bonding ESP...");
                        bondEsp(device);
                    }
                } else {
                    seText("Device Not Found");
                }
            } else {
                seText("Bluetooth is not enabled");
            }
        });

        binding.btnDisconnect.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View arg0) {
                if(checkEspConnected(bluetoothSocket)) {
                	disconnectEsp(bluetoothSocket);
                } else {
                	seText("Esp is already disconneted");
                }
            }
            
        });

        binding.btnGetData.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View arg0) {
                if(checkEspConnected(bluetoothSocket)) {
                	startAudioStream(bluetoothSocket);
                } else {
                	seText("First connect Esp");
                }
            }
            
        });
    }

    private void checkPermission() {
        String[] permissions = {
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private boolean checkBluetoothSupport() {
        return bluetoothAdapter != null;
    }

    private boolean checkBluetoothEnable() {
        return bluetoothAdapter.isEnabled();
    }

    private void enableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    private BluetoothDevice getEspDevice() {
        return bluetoothAdapter.getRemoteDevice(espAdd);
    }

    private boolean checkEspDevice(BluetoothDevice device) {
        return device != null;
    }

    private boolean checkEspBonded() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getAddress().equals(espAdd)) {
                return true;
            }
        }
        return false;
    }

    private void bondEsp(BluetoothDevice device) {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bondReceiver, filter);
        device.createBond();
        seText("Bonding...");
    }

    private boolean checkEspConnected(BluetoothSocket socket) {
        return socket != null && socket.isConnected();
    }

    private void connectEsp(BluetoothDevice device) {
        try {
            if (device == null) {
                seText("Device is null");
                return;
            }
            bluetoothSocket = device.createRfcommSocketToServiceRecord(ESP_UUID);
            bluetoothSocket.connect();
            seText("ESP connected");
        } catch (IOException e) {
            seText("Can't connect to ESP");
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    private void disconnectEsp(BluetoothSocket socket) {
        if (socket != null && socket.isConnected()) {
            try {
                socket.close();
                seText("Device disconnected");
            } catch (IOException e) {
                seText("Failed to disconnect");
                Log.e(TAG, "Failed to disconnect", e);
            }
        } else {
            seText("Device not connected");
        }
    }

    private void startAudioStream(BluetoothSocket bluetoothSocket) {
    executorService.submit(() -> {
        try {
            // Define audio settings
            final int SAMPLE_RATE = 16000; // 16 kHz
            final int BUFFER_SIZE = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            // Create AudioAttributes
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA) // Usage as media playback
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // Content as music
                    .build();

            // Initialize AudioTrack with AudioAttributes
            AudioTrack audioTrack = new AudioTrack(
                    audioAttributes,
                    new AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build(),
                    BUFFER_SIZE,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );

            // Check if AudioTrack is initialized successfully
            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                Log.d(TAG, "AudioTrack initialized successfully");
                audioTrack.play();
            } else {
                Log.e(TAG, "AudioTrack initialization failed");
                return; // Exit if initialization fails
            }

            // Prepare to read from Bluetooth and play audio
            byte[] packet = new byte[BUFFER_SIZE]; // Use the correct buffer size
            InputStream inputStream = bluetoothSocket.getInputStream();
            Log.d(TAG, "Starting to read audio data from Bluetooth");

            while (true) {
                int bytesRead = inputStream.read(packet); // Read a packet of data

                if (bytesRead > 0) { // Ensure data is being read
                    Log.d(TAG, "Received " + bytesRead + " bytes");

                    // Write packet data to AudioTrack for playback
                    int writtenBytes = audioTrack.write(packet, 0, bytesRead);
                    if (writtenBytes != bytesRead) {
                        Log.w(TAG, "Mismatch in written bytes: expected " + bytesRead + ", wrote " + writtenBytes);
                    }
                } else {
                    Log.w(TAG, "No data received or end of stream reached.");
                    Thread.sleep(10); // Sleep for a short time to prevent high CPU usage
                }
            }
        } catch (IOException | InterruptedException e) {
            Log.e(TAG, "IOException occurred during Bluetooth audio streaming", e);
            e.printStackTrace();
        }
    });
}
    private void seText(String s) {
        binding.textView.setText(s);
    }

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                seText("Bluetooth enabled");
            } else {
                seText("Error enabling Bluetooth");
            }
        }
    );

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if (state == BluetoothDevice.BOND_BONDED) {
                    seText("Device bonded");
                } else if (state == BluetoothDevice.BOND_NONE) {
                    seText("Error in bonding");
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
        unregisterReceiver(bondReceiver);
        executorService.shutdown();
    }
}