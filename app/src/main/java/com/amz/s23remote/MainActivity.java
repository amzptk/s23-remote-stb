package com.amz.s23remote;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * S23 Remote STB — the Samsung S23 acts as a Bluetooth HID keyboard for a
 * generic Android TV Box (STB).
 *
 * How it works:
 * 1. The phone registers itself as a HID keyboard with the Bluetooth stack
 *    ({@link BluetoothHidDevice#registerApp}) using a standard keyboard SDP
 *    record (see {@link #HID_DESCRIPTOR}).
 * 2. The STB is put in pairing / accessory-discovery mode
 *    (STB Settings &gt; Remotes &amp; Accessories &gt; Add accessory).
 * 3. This app scans for / lists Bluetooth devices, the user picks the STB
 *    and taps Connect — {@link BluetoothHidDevice#connect(BluetoothDevice)}.
 * 4. Every remote button sends a HID key-down report followed by a key-up
 *    (all-zero) report, exactly like a physical Bluetooth keyboard.
 *
 * Limitations (see README.md): HID-device support varies by phone and by
 * STB. Some phones / STBs refuse the HID profile, and Bluetooth cannot
 * power a fully-off STB back on.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1;

    // HID usage codes (USB HID Usage Tables, Keyboard/Keypad page 0x07).
    // Logical max in the descriptor is 0xE7 so the media usages below fit.
    private static final byte KEY_ENTER = 0x28;      // OK
    private static final byte KEY_ESCAPE = 0x29;     // Back
    private static final byte KEY_HOME = 0x4A;       // Home
    private static final byte KEY_MENU = 0x65;       // Application / context menu
    private static final byte KEY_LEFT = 0x50;
    private static final byte KEY_RIGHT = 0x4F;
    private static final byte KEY_DOWN = 0x51;
    private static final byte KEY_UP = 0x52;
    private static final byte KEY_MUTE = 0x7F;
    private static final byte KEY_VOL_UP = (byte) 0x80;
    private static final byte KEY_VOL_DOWN = (byte) 0x81;
    private static final byte KEY_PLAY_PAUSE = (byte) 0xCD;

    /**
     * Standard HID keyboard report descriptor (63 bytes). One input report:
     * byte 0 = modifier keys, byte 1 = reserved, bytes 2-7 = up to 6
     * simultaneous key usages. One output report: LED states.
     */
    private static final byte[] HID_DESCRIPTOR = new byte[]{
            0x05, 0x01,             // Usage Page (Generic Desktop)
            0x09, 0x06,             // Usage (Keyboard)
            (byte) 0xA1, 0x01,      // Collection (Application)
            0x05, 0x07,             //   Usage Page (Key Codes)
            0x19, (byte) 0xE0,     //   Usage Minimum (224)
            0x29, (byte) 0xE7,     //   Usage Maximum (231)
            0x15, 0x00,             //   Logical Minimum (0)
            0x25, 0x01,             //   Logical Maximum (1)
            0x75, 0x01,             //   Report Size (1)
            (byte) 0x95, 0x08,      //   Report Count (8)
            (byte) 0x81, 0x02,      //   Input (Data, Variable, Absolute) — modifiers
            (byte) 0x95, 0x01,      //   Report Count (1)
            0x75, 0x08,             //   Report Size (8)
            (byte) 0x81, 0x03,      //   Input (Constant) — reserved byte
            (byte) 0x95, 0x05,      //   Report Count (5)
            0x75, 0x01,             //   Report Size (1)
            0x05, 0x08,             //   Usage Page (LEDs)
            0x19, 0x01,             //   Usage Minimum (1)
            0x29, 0x05,             //   Usage Maximum (5)
            (byte) 0x91, 0x02,      //   Output (Data, Variable, Absolute) — LEDs
            (byte) 0x95, 0x01,      //   Report Count (1)
            0x75, 0x03,             //   Report Size (3)
            (byte) 0x91, 0x03,      //   Output (Constant)
            (byte) 0x95, 0x06,      //   Report Count (6)
            0x75, 0x08,             //   Report Size (8)
            0x15, 0x00,             //   Logical Minimum (0)
            0x25, (byte) 0xE7,     //   Logical Maximum (231)
            0x05, 0x07,             //   Usage Page (Key Codes)
            0x19, 0x00,             //   Usage Minimum (0)
            0x29, (byte) 0xE7,     //   Usage Maximum (231)
            (byte) 0x81, 0x00,      //   Input (Data, Array) — pressed keys
            (byte) 0xC0             // End Collection
    };

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothHidDevice hidDevice;
    private BluetoothDevice connectedDevice;
    private BluetoothDevice selectedDevice;

    private TextView statusText;
    private ListView deviceListView;
    private EditText inputText;

    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private final Map<String, BluetoothDevice> devicesByLabel = new HashMap<>();
    private ArrayAdapter<String> deviceAdapter;

    private final BluetoothHidDevice.Callback hidCallback =
            new BluetoothHidDevice.Callback() {
                @Override
                public void onAppStatusChanged(BluetoothDevice pluggedDevice,
                                               boolean registered) {
                    runOnUiThread(() -> setStatus(registered
                            ? "HID terdaftar — siap connect ke STB"
                            : "HID belum terdaftar"));
                }

                @Override
                public void onConnectionStateChanged(BluetoothDevice device, int state) {
                    runOnUiThread(() -> {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            connectedDevice = device;
                            setStatus("Terhubung ke: " + deviceName(device));
                            Toast.makeText(MainActivity.this,
                                    "Terhubung ke " + deviceName(device),
                                    Toast.LENGTH_SHORT).show();
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            if (device.equals(connectedDevice)) {
                                connectedDevice = null;
                            }
                            setStatus("Terputus — pilih STB lalu Connect");
                        }
                    });
                }
            };

    private final BluetoothProfile.ServiceListener hidServiceListener =
            new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile != BluetoothProfile.HID_DEVICE) return;
                    hidDevice = (BluetoothHidDevice) proxy;
                    registerHidApp();
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null;
                        connectedDevice = null;
                    }
                }
            };

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE);
                if (device != null) addDevice(device);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setStatus(foundDevices.isEmpty()
                        ? "Scan selesai — STB tidak ditemukan. Pastikan STB dalam mode pairing."
                        : "Scan selesai — pilih STB lalu Connect");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        deviceListView = findViewById(R.id.deviceList);
        inputText = findViewById(R.id.inputText);

        deviceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        deviceListView.setAdapter(deviceAdapter);
        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            String label = deviceAdapter.getItem(position);
            selectedDevice = devicesByLabel.get(label);
            if (selectedDevice != null) {
                setStatus("Dipilih: " + deviceName(selectedDevice)
                        + " — tekan Connect");
            }
        });

        findViewById(R.id.scanButton).setOnClickListener(v -> startScan());
        findViewById(R.id.connectButton).setOnClickListener(v -> connectSelected());
        findViewById(R.id.disconnectButton).setOnClickListener(v -> disconnect());
        findViewById(R.id.sendTextButton).setOnClickListener(v -> sendText());

        bindKey(R.id.btnUp, KEY_UP, (byte) 0x00);
        bindKey(R.id.btnDown, KEY_DOWN, (byte) 0x00);
        bindKey(R.id.btnLeft, KEY_LEFT, (byte) 0x00);
        bindKey(R.id.btnRight, KEY_RIGHT, (byte) 0x00);
        bindKey(R.id.btnOk, KEY_ENTER, (byte) 0x00);
        bindKey(R.id.btnBack, KEY_ESCAPE, (byte) 0x00);
        bindKey(R.id.btnHome, KEY_HOME, (byte) 0x00);
        bindKey(R.id.btnMenu, KEY_MENU, (byte) 0x00);
        bindKey(R.id.btnVolUp, KEY_VOL_UP, (byte) 0x00);
        bindKey(R.id.btnVolDown, KEY_VOL_DOWN, (byte) 0x00);
        bindKey(R.id.btnMute, KEY_MUTE, (byte) 0x00);
        bindKey(R.id.btnPlayPause, KEY_PLAY_PAUSE, (byte) 0x00);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(discoveryReceiver, filter);

        BluetoothManager manager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager != null ? manager.getAdapter() : null;

        if (bluetoothAdapter == null) {
            setStatus("HP ini tidak punya Bluetooth");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setStatus("Nyalakan Bluetooth dulu, lalu buka lagi aplikasi ini");
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }

        if (!ensurePermissions()) {
            return; // onRequestPermissionsResult() continues the setup
        }
        setupHid();
        loadBondedDevices();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver was never registered.
        }
        if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
            stopDiscovery();
        }
        if (hidDevice != null) {
            hidDevice.unregisterApp();
        }
        if (bluetoothAdapter != null) {
            bluetoothAdapter.closeProfileProxy(
                    BluetoothProfile.HID_DEVICE, hidDevice);
        }
    }

    // ----- Permissions (Android 12+ / API 31+) -----

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean ensurePermissions() {
        List<String> missing = new ArrayList<>();
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (missing.isEmpty()) return true;
        ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), REQUEST_PERMISSIONS);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_PERMISSIONS) return;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                setStatus("Izin Bluetooth / lokasi ditolak — aplikasi tidak bisa scan STB");
                return;
            }
        }
        setupHid();
        loadBondedDevices();
    }

    // ----- HID registration -----

    private void setupHid() {
        if (bluetoothAdapter == null) return;
        if (!bluetoothAdapter.getProfileProxy(this, hidServiceListener,
                BluetoothProfile.HID_DEVICE)) {
            setStatus("HP ini tidak mendukung profil Bluetooth HID Device");
        }
    }

    private void registerHidApp() {
        if (hidDevice == null) return;
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                "S23 Remote",
                "Keyboard remote for Android TV Box",
                "Amz",
                BluetoothHidDevice.SUBCLASS1_KEYBOARD, // 0x40
                HID_DESCRIPTOR);
        BluetoothHidDeviceAppQosSettings qos = new BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800, 9, 0, 11250,
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800, 9, 0, 11250);
        if (!hidDevice.registerApp(sdp, null, qos, hidCallback)) {
            setStatus("Gagal mendaftarkan HID — coba restart Bluetooth");
        }
    }

    // ----- Discovery / connection -----

    private void loadBondedDevices() {
        if (bluetoothAdapter == null || !hasScanPermission()) return;
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        if (bonded != null) {
            for (BluetoothDevice device : bonded) addDevice(device);
        }
    }

    private void startScan() {
        if (bluetoothAdapter == null || !hasScanPermission()) {
            ensurePermissions();
            return;
        }
        if (bluetoothAdapter.isDiscovering()) stopDiscovery();
        foundDevices.clear();
        devicesByLabel.clear();
        deviceAdapter.clear();
        loadBondedDevices();
        if (bluetoothAdapter.startDiscovery()) {
            setStatus("Mencari STB… pastikan STB dalam mode Add accessory");
        } else {
            setStatus("Gagal mulai scan — coba matikan lalu nyalakan Bluetooth");
        }
    }

    private void addDevice(BluetoothDevice device) {
        if (foundDevices.contains(device)) return;
        foundDevices.add(device);
        String label = deviceName(device) + "\n" + device.getAddress();
        devicesByLabel.put(label, device);
        deviceAdapter.add(label);
        deviceAdapter.notifyDataSetChanged();
    }

    private void connectSelected() {
        if (hidDevice == null) {
            setStatus("HID belum siap — tunggu sebentar lalu coba lagi");
            return;
        }
        if (selectedDevice == null) {
            Toast.makeText(this, "Pilih STB dulu dari daftar",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasConnectPermission()) {
            ensurePermissions();
            return;
        }
        if (hidDevice.connect(selectedDevice)) {
            setStatus("Menghubungkan ke " + deviceName(selectedDevice) + "…");
        } else {
            setStatus("Gagal connect — pastikan STB masih dalam mode pairing");
        }
    }

    private void disconnect() {
        if (hidDevice != null && connectedDevice != null && hasConnectPermission()) {
            hidDevice.disconnect(connectedDevice);
        }
        connectedDevice = null;
        setStatus("Terputus — pilih STB lalu Connect");
    }

    // ----- Sending keys -----

    private void bindKey(int buttonId, byte usage, byte modifier) {
        Button button = findViewById(buttonId);
        button.setOnClickListener(v -> sendKey(usage, modifier));
    }

    /**
     * Sends one HID keyboard report: key-down, then an all-zero key-up.
     * Report layout (8 bytes): [modifier, reserved, key1..key6].
     */
    private void sendKey(byte usage, byte modifier) {
        if (hidDevice == null || connectedDevice == null) {
            Toast.makeText(this, "Belum terhubung ke STB",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        byte[] down = new byte[8];
        down[0] = modifier;
        down[2] = usage;
        byte[] up = new byte[8]; // all zero = no key pressed
        if (!hidDevice.sendReport(connectedDevice, 0, down)) {
            setStatus("Gagal mengirim tombol — periksa koneksi ke STB");
            return;
        }
        hidDevice.sendReport(connectedDevice, 0, up);
    }

    /** Types text by sending one HID key per character (a-z, 0-9, space). */
    private void sendText() {
        String text = inputText.getText().toString();
        if (text.isEmpty()) return;
        for (char c : text.toCharArray()) {
            byte[] key = hidUsageForChar(c);
            if (key == null) continue;
            sendKey(key[0], key[1]);
            try {
                Thread.sleep(40);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        inputText.setText("");
    }

    /**
     * Maps a character to {usage, modifier}. Returns null for unsupported
     * characters. Letters a-z are usages 0x04-0x1D; digits 1-9 are
     * 0x1E-0x26, 0 is 0x27; space is 0x2C; modifier 0x02 = left Shift.
     */
    private byte[] hidUsageForChar(char c) {
        if (c >= 'a' && c <= 'z') return new byte[]{(byte) (0x04 + (c - 'a')), 0x00};
        if (c >= 'A' && c <= 'Z') return new byte[]{(byte) (0x04 + (c - 'A')), 0x02};
        if (c >= '1' && c <= '9') return new byte[]{(byte) (0x1E + (c - '1')), 0x00};
        if (c == '0') return new byte[]{0x27, 0x00};
        if (c == ' ') return new byte[]{0x2C, 0x00};
        if (c == '.') return new byte[]{0x37, 0x00};
        if (c == '-') return new byte[]{0x2D, 0x00};
        if (c == '_') return new byte[]{0x2D, 0x02};
        return null;
    }

    // ----- Helpers -----

    private String deviceName(BluetoothDevice device) {
        if (!hasConnectPermission()) return device.getAddress();
        String name = device.getName();
        return name != null ? name : device.getAddress();
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void stopDiscovery() {
        if (bluetoothAdapter != null && hasScanPermission()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private void setStatus(String message) {
        statusText.setText("Status: " + message);
    }
}
