package innovyt.com.driverfit.Activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.enums.AvailableCommandNames;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.proximate.websocket.ListenerSubscription;
import com.proximate.websocket.Stomp;
import com.proximate.websocket.Subscription;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import innovyt.com.driverfit.R;
import innovyt.com.driverfit.http.client.ApiClient;
import innovyt.com.driverfit.http.client.ApiClientFactory;
import innovyt.com.driverfit.io.LocationProvider;
import innovyt.com.driverfit.io.UDPClient;
import innovyt.com.driverfit.services.Obd2Service;
import innovyt.com.driverfit.utils.Constants;

public class TransitMap extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, GoogleMap.OnMarkerClickListener {
    private GoogleMap mMap;
    private Map<String, Marker> markers = new HashMap<>();
    private PolylineOptions polylineOptions = null;
    private HandlerThread websocketHandlerThread = new HandlerThread("WebsocketHandlet");
    private Handler websocketHandler;
    private ApiClient apiClient = null;

    private Obd2Service mService;
    private boolean mBound = false;
    private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private List<String> devicesStr = new ArrayList<>();
    private ArrayAdapter arrayAdapter = null;
    private AlertDialog.Builder alertDialog = null;
    private String selectedDeviceName = null;
    private TextView speed = null;
    private UDPClient udpClient = new UDPClient("24.3.167.3", "8888");
    protected static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final int REQUEST_ENABLE_BT = 0x2;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private Location mCurrentBestLocation;
    private Object object = new Object();
    private Stomp stompClient = new Stomp(new StompListener(), new WebsocketListener());
    private boolean obdServiceBinded = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiClient = ApiClientFactory.create();
        setContentView(R.layout.activity_transit_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        alertDialog = new AlertDialog.Builder(this);
        arrayAdapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice, devicesStr);
        alertDialog.setSingleChoiceItems(arrayAdapter, -1, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                selectedDeviceName = devicesStr.get(position);
                Intent intent = new Intent(TransitMap.this, Obd2Service.class);
                bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            }
        });
        mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API)
                .addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(AppIndex.API).build();
        createLocationRequest();
        websocketHandlerThread.start();
        websocketHandler = new Handler(websocketHandlerThread.getLooper());
        websocketHandler.post(new WebsocketRunnable());
        mGoogleApiClient.connect();
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else
            updateAdapter();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(Constants.TAG, "Connected to Location Service.");
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(Constants.TAG, "Failed to connect to Location Service.");
    }

     @Override
    protected void onDestroy() {
        if (mService != null && obdServiceBinded) {
            mService.stopService(true);
            this.unbindService(mConnection);
            obdServiceBinded = false;
        }
        devicesStr.clear();
        arrayAdapter.clear();
        if (mGoogleApiClient != null) {
            stopLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
            synchronized (object) {
                mCurrentBestLocation = null;
            }
        }
        websocketHandlerThread.quit();
        super.onDestroy();
    }

    private void updateAdapter() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                devicesStr.add(deviceName);
            }
            alertDialog.setTitle("Select Paired Device.");
            alertDialog.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                updateAdapter();
            }
        }
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                startLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }


    private class WebsocketListener implements Stomp.ListenerWebsocketConnection {

        @Override
        public void onConnected() {
            Log.d(Constants.TAG, "Websocket Connected.");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(TransitMap.this,
                            "Websocket Connected.",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        @Override
        public void onDisconnected() {
            Log.d(Constants.TAG, "Websocket Disconnected.");
            stompSubscribed = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(TransitMap.this,
                            "Websocket Disconnected, Reconnecting Again.",
                            Toast.LENGTH_LONG).show();
                }
            });
            websocketHandler.postDelayed(new WebsocketRunnable(), 5000);
        }
    }

    private class StompListener implements Stomp.ListenerWSNetwork {

        @Override
        public void onState(int state) {
            Log.d(Constants.TAG, "STOMP Status:" + state);
            if (state == Stomp.CONNECTED) {

            } else if (state == Stomp.DECONNECTED_FROM_OTHER || state == Stomp.DECONNECTED_FROM_APP) {

            }
        }

    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            obdServiceBinded = true;
            Obd2Service.LocalBinder binder = (Obd2Service.LocalBinder) service;
            mService = binder.getService();
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            for (final BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                if (deviceName.equals(selectedDeviceName)) {
                    mService.requestBluetoothStateChangeUpdates(new Obd2Service.OnBluetoothStateChangeListener() {
                        @Override
                        public void onStateChanged(Obd2Service.State state) {
                            if (state.equals(Obd2Service.State.DISCONNECTED)) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(TransitMap.this,"Bluetooth Disconnected",Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                            else if (state.equals(Obd2Service.State.CONNECTED)) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(TransitMap.this,"Bluetooth Connected",Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        }
                    });
                    mService.startDeviceCommunication(device);
                    mService.requestObdDeviceDataUpdates(new Obd2Service.ObdDeviceDataListener() {
                        @Override
                        public void onDataChanged(final Map<String, String> sensorValues) {
                            synchronized (object) {
                                if (mCurrentBestLocation == null) {
                                    Log.d(Constants.TAG, "No Location data, Ignore OBD Data.");
                                    return;
                                }
                                String DATEFORMAT = "yyyy-MM-dd HH:mm:ss";
                                SimpleDateFormat sdf = new SimpleDateFormat(DATEFORMAT);
                                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                                final String utcTime = sdf.format(new Date());
                                sensorValues.put("Time", utcTime);

                                //Add Location Values
                                sensorValues.put("Latitude", Double.toString(mCurrentBestLocation.getLatitude()));
                                sensorValues.put("Longitude", Double.toString(mCurrentBestLocation.getLongitude()));
                                sensorValues.put("Altitude", Double.toString(mCurrentBestLocation.getAltitude()));
                            }

                            for (Map.Entry entry : sensorValues.entrySet())
                                Log.d(Constants.TAG, entry.getKey() + ":" + entry.getValue());
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    Gson gson = new Gson();
                                    String sensorValuesJson = gson.toJson(sensorValues);
                                    udpClient.send(sensorValuesJson.getBytes(), sensorValues.size());
                                }
                            });
                        }
                    });
                    break;
                }
            }

            Log.d("DriverStatistics", "OBD Service Started.");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        startLocationUpdates(mGoogleApiClient, mLocationRequest, TransitMap.this);
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(TransitMap.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    /**
     * @param mGoogleApiClient
     * @param mLocationRequest
     * @param listener
     */
    private void startLocationUpdates(GoogleApiClient mGoogleApiClient, LocationRequest mLocationRequest, LocationListener listener) {
        if (ActivityCompat.checkSelfPermission(mGoogleApiClient.getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, listener);
    }


    private void stopLocationUpdates(GoogleApiClient mGoogleApiClient, LocationListener listener) {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, listener);
    }

    private boolean stompSubscribed = false;

    @Override
    public void onLocationChanged(Location location) {
        synchronized (object) {
            if (mCurrentBestLocation == null)
                mCurrentBestLocation = location;
            if (LocationProvider.isBetterLocation(location, mCurrentBestLocation)) {
                mCurrentBestLocation = location;
            }
        }
    }

    private class WebsocketRunnable implements Runnable {
        @Override
        public void run() {
            Map<String, String> headersSetup = new HashMap<String, String>();
            headersSetup.put("host", "24.3.167.3");
            stompClient.connect("http://24.3.167.3:9000/monitor", headersSetup);
            if (!stompSubscribed) {
                stompClient.subscribe(new Subscription("/topic/ObdReadings", new ListenerSubscription() {
                    @Override
                    public void onMessage(Map<String, String> headers, final String body) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Gson gson = new Gson();
                                Map<String, String> sensorValues = gson.fromJson(body, new TypeToken<Map<String, String>>() {
                                }.getType());
                                Log.d(Constants.TAG, "received:" + body);
                                if (markers.get(sensorValues.get(AvailableCommandNames.VIN.getValue())) == null) {
                                    Marker marker = mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_train_black_24dp)).position(new LatLng(Double.parseDouble(sensorValues.get("Latitude")), Double.parseDouble(sensorValues.get("Longitude")))));
                                    marker.setTag(body);
                                    markers.put(sensorValues.get(AvailableCommandNames.VIN.getValue()), marker);
                                    mMap.setOnMarkerClickListener(TransitMap.this);
                                } else {
                                    markers.get(sensorValues.get(AvailableCommandNames.VIN.getValue())).setPosition(new LatLng(Double.parseDouble(sensorValues.get("Latitude")), Double.parseDouble(sensorValues.get("Longitude"))));
                                    markers.get(sensorValues.get(AvailableCommandNames.VIN.getValue())).setTag(body);
                                }
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(Double.parseDouble(sensorValues.get("Latitude")), Double.parseDouble(sensorValues.get("Longitude"))), 10));
                            }
                        });
                    }
                }));
                stompSubscribed = true;
            }
        }
    }

    /**
     * Called when the user clicks a marker.
     */
    @Override
    public boolean onMarkerClick(final Marker marker) {
        Toast.makeText(this,
                (String) marker.getTag(),
                Toast.LENGTH_LONG).show();
        return false;
    }
}
