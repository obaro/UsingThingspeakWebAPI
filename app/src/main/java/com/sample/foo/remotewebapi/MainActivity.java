package com.sample.foo.remotewebapi;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RemoteWebAPI";
    private static final String THINGSPEAK_CHANNEL_ID = "46465";
    private static final String THINGSPEAK_API_KEY = "49V2EB2W3NPH38QB";
    private static final String THINGSPEAK_API_KEY_STRING = "api_key";
    private static final String THINGSPEAK_FIELD1 = "field1";
    private static final String THINGSPEAK_FIELD2 = "field2";
    private static final String THINGSPEAK_UPDATE_URL = "https://api.thingspeak.com/update?";
    private static final String THINGSPEAK_CHANNEL_URL = "https://api.thingspeak.com/channels/";
    private static final String THINGSPEAK_FEEDS_LAST = "/feeds/last?";

    LocationManager locationManager;

    double longitude, latitude;

    EditText latitudeEdit, longitudeEdit;
    TextView latitudeUpdate, longitudeUpdate;
    TextView distanceText;
    ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

        latitudeEdit = (EditText) findViewById(R.id.latitudeTarget);
        longitudeEdit = (EditText) findViewById(R.id.longitudeTarget);
        latitudeUpdate = (TextView) findViewById(R.id.latitudeUpdate);
        longitudeUpdate = (TextView) findViewById(R.id.longitudeUpdate);
        distanceText = (TextView) findViewById(R.id.distanceText);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
    }

    private boolean checkLocation() {
        if(!isLocationEnabled())
            showAlert();
        return isLocationEnabled();
    }

    private void showAlert() {
        final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle("Enable Location")
                .setMessage("Your Locations Settings is set to 'Off'.\nPlease Enable Location to " +
                        "use this app")
                .setPositiveButton("Location Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(myIntent);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    }
                });
        dialog.show();
    }

    private boolean isLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    public void toggleLocationUpdates(View view) {
        if(!checkLocation())
            return;

        Button button = (Button) view;
        if(button.getText().equals(getResources().getString(R.string.pause))) {
            locationManager.removeUpdates(locationListener);
            button.setText(R.string.resume);
        }
        else {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 2 * 60 * 1000, 10, locationListener);
            button.setText(R.string.pause);
        }
    }

    public void compareLocation(View view) {
        if(latitudeEdit.getText().length() == 0 ||
                longitudeEdit.getText().length() == 0) {
            Toast.makeText(this, "Input the target latitude/longitude", Toast.LENGTH_LONG).show();
            return;
        }
        new FetchThingspeakTask().execute();
    }

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(final Location location) {
            longitude = location.getLongitude();
            latitude = location.getLatitude();

            new UpdateThingspeakTask().execute();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    longitudeUpdate.setText(longitude + "");
                    latitudeUpdate.setText(latitude + "");
                }
            });
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {}

        @Override
        public void onProviderEnabled(String s) {}

        @Override
        public void onProviderDisabled(String s) {}
    };

    class UpdateThingspeakTask extends AsyncTask<Void, Void, String> {

        protected void onPreExecute() {
        }

        protected String doInBackground(Void... urls) {
            try {
                URL url = new URL(THINGSPEAK_UPDATE_URL + THINGSPEAK_API_KEY_STRING + "=" +
                        THINGSPEAK_API_KEY + "&" + THINGSPEAK_FIELD1 + "=" + latitude +
                        "&" + THINGSPEAK_FIELD2 + "=" + longitude);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    return stringBuilder.toString();
                }
                finally{
                    urlConnection.disconnect();
                }
            }
            catch(Exception e) {
                Log.e("ERROR", e.getMessage(), e);
                return null;
            }
        }

        protected void onPostExecute(String response) {
            // We completely ignore the response
            // Ideally we should confirm that our update was successful
        }
    }

    class FetchThingspeakTask extends AsyncTask<Void, Void, String> {

        Location target;

        protected void onPreExecute() {
            double latitude = new Double(latitudeEdit.getText().toString()).doubleValue();
            double longitude = new Double(longitudeEdit.getText().toString()).doubleValue();
            target = new Location("");
            target.setLatitude(latitude);
            target.setLongitude(longitude);

            latitudeEdit.setEnabled(false);
            longitudeEdit.setEnabled(false);
            distanceText.setText("");
            distanceText.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
        }

        protected String doInBackground(Void... urls) {
            try {
                URL url = new URL(THINGSPEAK_CHANNEL_URL + THINGSPEAK_CHANNEL_ID +
                        THINGSPEAK_FEEDS_LAST + THINGSPEAK_API_KEY_STRING + "=" +
                        THINGSPEAK_API_KEY + "");
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    return stringBuilder.toString();
                }
                finally{
                    urlConnection.disconnect();
                }
            }
            catch(Exception e) {
                Log.e("ERROR", e.getMessage(), e);
                return null;
            }
        }

        protected void onPostExecute(String response) {
            if(response == null) {
                Toast.makeText(MainActivity.this, "There was an error", Toast.LENGTH_SHORT).show();
                return;
            }
            latitudeEdit.setEnabled(true);
            longitudeEdit.setEnabled(true);

            distanceText.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);

            try {
                JSONObject channel = (JSONObject) new JSONTokener(response).nextValue();
                double latitude = channel.getDouble(THINGSPEAK_FIELD1);
                double longitude = channel.getDouble(THINGSPEAK_FIELD2);
                Location location = new Location("");
                location.setLatitude(latitude);
                location.setLongitude(longitude);
                float distance = location.distanceTo(target);
                distanceText.setText("The distance between both Locations is \n" +
                        distance + " meters");
                Log.e(TAG, "distance == " + distance);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
