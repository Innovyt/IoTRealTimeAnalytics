package innovyt.com.driverfit.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.PersistentCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.ObdResetCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.enums.ObdProtocols;
import com.github.pires.obd.exceptions.NoDataException;
import com.github.pires.obd.exceptions.UnsupportedCommandException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import innovyt.com.driverfit.io.BluetoothManager;
import innovyt.com.driverfit.utils.ObdConfig;

import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static com.google.android.gms.wearable.DataMap.TAG;

public class Obd2Service extends Service {
    private Object object = new Object();
    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private boolean serviceStarted = false;
    private BluetoothSocket socket;
    private InputStream reader;
    private OutputStream writer;
    private List<OnBluetoothStateChangeListener> listeners = new ArrayList<>();
    private List<ObdDeviceDataListener> obdDeviceDataListeners = new ArrayList<>();
    private BluetoothDevice mDevice = null;
    private BluetoothReceiver bluetoothReceiver = new BluetoothReceiver();
    public Obd2Service() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter(ACTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the communication channel to the service.
        return new LocalBinder();
    }

    synchronized public boolean  startDeviceCommunication(BluetoothDevice device) {
        synchronized (object) {
            mDevice = device;
            if (!serviceStarted) {
                try {
                    socket = BluetoothManager.connect(device);
                    reader = socket.getInputStream();
                    writer = socket.getOutputStream();
                    serviceStarted = true;
                    ObdResetCommand resetCommand = new ObdResetCommand();
                    try {
                        resetCommand.run(reader, writer);
                        Thread.sleep(5000);
                        EchoOffCommand echoOffCommand = new EchoOffCommand();
                        echoOffCommand.setResponseTimeDelay(new Long(100));
                        echoOffCommand.run(reader, writer);
                        EchoOffCommand echoOffCommand2 = new EchoOffCommand();
                        echoOffCommand2.setResponseTimeDelay(new Long(100));
                        echoOffCommand2.run(reader, writer);
                        LineFeedOffCommand lineFeedOffCommand = new LineFeedOffCommand();
                        lineFeedOffCommand.setResponseTimeDelay(new Long(100));
                        lineFeedOffCommand.run(reader, writer);
                        TimeoutCommand timeoutCommand = new TimeoutCommand(62);
                        timeoutCommand.run(reader, writer);
                        SelectProtocolCommand protocolCommand = new SelectProtocolCommand(ObdProtocols.ISO_15765_4_CAN);
                        protocolCommand.setResponseTimeDelay(new Long(100));
                        protocolCommand.run(reader, writer);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    HandlerThread thread = new HandlerThread("ServiceStartArguments",
                            Process.THREAD_PRIORITY_BACKGROUND);
                    thread.start();
                    // Get the HandlerThread's Looper and use it for our Handler
                    mServiceLooper = thread.getLooper();
                    mServiceHandler = new ServiceHandler(mServiceLooper);
                    mServiceHandler.sendEmptyMessage(1);
                    for (OnBluetoothStateChangeListener listener : listeners) {
                        listener.onStateChanged(State.CONNECTED);
                    }
                } catch (IOException e) {
                    serviceStarted = false;
                    e.printStackTrace();
                }

            }
        }
        return serviceStarted;
    }

    public void requestObdDeviceDataUpdates(ObdDeviceDataListener listener) {
        obdDeviceDataListeners.add(listener);
    }

    public void removeObdDeviceDataUpdates(ObdDeviceDataListener listener) {
        obdDeviceDataListeners.remove(listener);
    }

    public void requestBluetoothStateChangeUpdates(OnBluetoothStateChangeListener listener) {
        listeners.add(listener);
    }

    public void removeBluetoothStateChangeUpdates(OnBluetoothStateChangeListener listener) {
        listeners.remove(listener);
    }

    public boolean isServiceStarted() {
        return serviceStarted;
    }


     public void stopService(boolean removeListeners) {
        synchronized (object) {
            if (socket != null) {
                try {
                    serviceStarted = false;
                    if(removeListeners) {
                        obdDeviceDataListeners.clear();
                        listeners.clear();
                        if(bluetoothReceiver!=null)
                            unregisterReceiver(bluetoothReceiver);
                    }
                    mServiceLooper.quit();
                    for (OnBluetoothStateChangeListener listener : listeners) {
                        listener.onStateChanged(State.DISCONNECTED);
                    }
                    socket.getInputStream().close();
                    socket.getOutputStream().close();
                    socket.close();
                    socket = null;
                } catch (IOException e) {
                    Log.e(TAG, "Failed to close Bluetooth socket. -> " + e.getMessage());
                }
            }
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            boolean disconnected = false;
            Map<String, String> obdValues = new HashMap<>();
            // Normally we would do some work here, like download a file.
            // For our sample, we just sleep for 5 seconds.
            ObdCommand command = null;
            ArrayList<ObdCommand> commands = ObdConfig.getCommands();

            for (int i = 0; i < commands.size(); i++) {
                try {
                    command = commands.get(i);
                    command.setResponseTimeDelay(new Long(100));
                    command.run(reader, writer);
                    obdValues.put(command.getName(), command.getFormattedResult());
                } catch (InterruptedException e) {
                    disconnected = true;
                    Thread.currentThread().interrupt();
                } catch (UnsupportedCommandException u) {
                    disconnected = true;
                    Log.d(TAG, "Command not supported. -> " + u.getMessage());
                } catch (IOException io) {
                    disconnected = true;
                    Log.e(TAG, "IO error. -> " + io.getMessage());
                } catch (NoDataException e) {
                    Log.e(TAG, "NODATA returned for command. -> " + command.getName() + e.getMessage());
                } catch (Exception e) {
                    disconnected = true;
                    Log.e(TAG, "Failed to run command. -> " + command.getName() + e.getMessage());
                }
                if (disconnected == true) {
                    PersistentCommand.reset();
                    stopService(false);
                    for (OnBluetoothStateChangeListener listener : listeners) {
                        listener.onStateChanged(State.DISCONNECTED);
                    }
                    break;
                }
            }
            if (disconnected == false) {
                for (ObdDeviceDataListener listener : obdDeviceDataListeners) {
                    listener.onDataChanged(obdValues);
                }
                mServiceHandler.sendEmptyMessageDelayed(1, 2000);
            }


        }
    }


    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public Obd2Service getService() {
            // Return this instance of Obd2Service so clients can call public methods
            return Obd2Service.this;
        }
    }

    public class BluetoothReceiver extends BroadcastReceiver {
        public BluetoothReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: This method is called when the BroadcastReceiver is receiving
            // an Intent broadcast.
            int state = (int) intent.getExtras().get(BluetoothAdapter.EXTRA_STATE);
            if (state == BluetoothAdapter.STATE_OFF) {

            }
            if(state == BluetoothAdapter.STATE_ON)
            {
                startDeviceCommunication(mDevice);
            }
        }
    }

    public enum State {
        CONNECTED(1),
        DISCONNECTED(2);

        public int state;

        State(int state) {
            this.state = state;
        }

        public int getState() {
            return state;
        }

        public void setState(int state) {
            this.state = state;
        }
    }

    public interface OnBluetoothStateChangeListener {
        public void onStateChanged(State state);
    }

    public interface ObdDeviceDataListener {
        public void onDataChanged(Map<String, String> sensorValues);
    }
}
