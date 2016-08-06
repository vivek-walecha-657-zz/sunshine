package com.goibibo.location_services;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.goibibo.location_services.data.Channel;
import com.goibibo.location_services.data.Condition;
import com.goibibo.location_services.data.LocationResult;
import com.goibibo.location_services.listener.GeocodingServiceListener;
import com.goibibo.location_services.listener.WeatherServiceListener;
import com.goibibo.location_services.service.GoogleMapsGeocodingService;
import com.goibibo.location_services.service.WeatherCacheService;
import com.goibibo.location_services.service.YahooWeatherService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


public class MapsActivity extends AppCompatActivity implements
    OnMapReadyCallback,
    GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener,
    GoogleMap.OnMarkerDragListener,
    GoogleMap.OnMapLongClickListener,
    View.OnClickListener, WeatherServiceListener, GeocodingServiceListener, LocationListener {

  //Our Map
  private GoogleMap mMap;

  //To store longitude and latitude from map
  private double longitude;
  private double latitude;

  //Buttons
  private ImageButton buttonCurrent;
  private ImageButton buttonWeather;

  private ProgressDialog dialog;

  //Google ApiClient
  private GoogleApiClient googleApiClient;
  private String address;
  private String city;
  private String state;
  private String country;
  private StringBuilder msg;
  private MarkerOptions marker;


  // Weather Update
  private YahooWeatherService weatherService;
  private GoogleMapsGeocodingService geocodingService;
  private WeatherCacheService cacheService;


  // weather service fail flag
  private boolean weatherServicesHasFailed = false;

  private SharedPreferences preferences = null;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_maps);
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setTitle("Sunshine");
    toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.white));
    toolbar.setNavigationIcon(R.drawable.ic_close);
    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(final View v) {
        finish();
      }
    });



    preferences = PreferenceManager.getDefaultSharedPreferences(this);

    weatherService = new YahooWeatherService(this);
    weatherService.setTemperatureUnit(preferences.getString(getString(R.string.pref_temperature_unit), null));

    geocodingService = new GoogleMapsGeocodingService(this);
    cacheService = new WeatherCacheService(this);
    buttonWeather = (ImageButton) findViewById(R.id.buttonWeather);


    buttonWeather.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(final View v) {
        Intent i = new Intent(MapsActivity.this, WeatherActivity.class);
        startActivity(i);
      }
    });


      dialog = new ProgressDialog(this);
      dialog.setMessage(getString(R.string.loading));
      dialog.setCancelable(false);
      if (!this.isFinishing()) {
        dialog.show();
      }

      String location = null;

      if (preferences.getBoolean(getString(R.string.pref_geolocation_enabled), true)) {
        String locationCache = preferences.getString(getString(R.string.pref_cached_location), null);

        if (locationCache == null) {
          getWeatherFromCurrentLocation();
        } else {
          location = locationCache;
        }
      } else {
        location = preferences.getString(getString(R.string.pref_manual_location), null);
      }

      if (location != null) {
        weatherService.refreshWeather(location);
      }


    //Initializing googleapi client
    googleApiClient = new GoogleApiClient.Builder(this)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(LocationServices.API)
        .build();

    //Initializing views and adding onclick listeners

    buttonCurrent = (ImageButton) findViewById(R.id.buttonCurrent);
    buttonCurrent.setOnClickListener(this);

    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    if (!this.isFinishing()) {
      SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
          .findFragmentById(R.id.map);
      mapFragment.getMapAsync(this);

    }

  }

  private void startSettingsActivity() {
    Intent intent = new Intent(this, SettingsActivity.class);
    startActivity(intent);
  }


  @Override
  protected void onStart() {
    googleApiClient.connect();
    super.onStart();
  }

  @Override
  protected void onStop() {
    googleApiClient.disconnect();
    super.onStop();
  }

  //Getting current location
  private void getCurrentLocation() {
    mMap.clear();
    //Creating a location object
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return;
    }
    Location location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
    if (location != null) {
      //Getting longitude and latitude
      longitude = location.getLongitude();
      latitude = location.getLatitude();

      //moving the map to location
      moveMap();
    }
  }

  //Function to move the map
  private void moveMap() {
    //String to display current latitude and longitude


    //Creating a LatLng Object to store Coordinates
    LatLng latLng = new LatLng(latitude, longitude);

    marker = new MarkerOptions()
        .position(latLng) //setting position
        .draggable(true) //Making the marker draggable
        .title(convertLatLngToAddress(latitude, longitude));

    //Adding marker to map
    mMap.addMarker(marker); //Adding a title

    //Moving the camera
    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

    //Animating the camera
    mMap.animateCamera(CameraUpdateFactory.zoomTo(15));
    mMap.addMarker(marker).showInfoWindow();
    mMap.getUiSettings().setMapToolbarEnabled(true);
  }

  private String convertLatLngToAddress(Double latitude, Double longitude) {


    msg = new StringBuilder();
    Geocoder geocoder;
    List<Address> addresses;
    geocoder = new Geocoder(this, Locale.getDefault());

    try {
      addresses = geocoder.getFromLocation(latitude, longitude, 1); // Here 1 represent max location result to returned, by documents it recommended 1 to 5
     if(addresses.size()>0) {
       address = addresses.get(0).getAddressLine(0); // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
       city = addresses.get(0).getLocality();
       state = addresses.get(0).getAdminArea();
       country = addresses.get(0).getCountryName();
       if (null != address) {
         msg.append(address + ", ");
       }
       if (null != city) {
         msg.append(city + ", ");
       }
       if (null != state) {
         msg.append(state + ", ");
       }
       if (null != country) {
         msg.append(country + ", ");
       }
     }

    } catch (IOException e) {
      e.printStackTrace();
    }


    return msg.toString();


  }

  @Override
  public void onMapReady(GoogleMap googleMap) {
    mMap = googleMap;
    LatLng latLng = new LatLng(-34, 151);
    mMap.addMarker(new MarkerOptions().position(latLng).draggable(true));
    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
    mMap.setOnMarkerDragListener(this);
    mMap.setOnMapLongClickListener(this);
  }

  @Override
  public void onConnected(Bundle bundle) {
    getCurrentLocation();
  }

  @Override
  public void onConnectionSuspended(int i) {

  }

  @Override
  public void onConnectionFailed(ConnectionResult connectionResult) {

  }

  @Override
  public void onMapLongClick(LatLng latLng) {
    //Clearing all the markers
    mMap.clear();

    //Adding a new marker to the current pressed position
    mMap.addMarker(new MarkerOptions()
        .position(latLng)
        .draggable(true));

  }

  @Override
  public void onMarkerDragStart(Marker marker) {

  }

  @Override
  public void onMarkerDrag(Marker marker) {

  }

  @Override
  public void onMarkerDragEnd(Marker marker) {
    //Getting the coordinates
    latitude = marker.getPosition().latitude;
    longitude = marker.getPosition().longitude;


    //Moving the map
    moveMap();
  }

  @Override
  public void onClick(View v) {
    if (v == buttonCurrent) {
      getCurrentLocation();
      moveMap();
    }
  }



  private void getWeatherFromCurrentLocation() {
    // system's LocationManager
    LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

    // medium accuracy for weather, good for 100 - 500 meters
    Criteria locationCriteria = new Criteria();
    locationCriteria.setAccuracy(Criteria.ACCURACY_MEDIUM);

    String provider = locationManager.getBestProvider(locationCriteria, true);

    // single location update

    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return;
    }
    locationManager.requestSingleUpdate(provider, MapsActivity.this, null);
  }



  @Override
  public void serviceSuccess(Channel channel) {
    dialog.hide();
    Condition condition = channel.getItem().getCondition();
    String temperatureLabel = getString(R.string.temperature_output, condition.getTemperature(), channel.getUnits().getTemperature());
    Toast.makeText(this,temperatureLabel,Toast.LENGTH_LONG);
  }

  @Override
  public void serviceFailure(Exception exception) {
    // display error if this is the second failure
    if (weatherServicesHasFailed) {
      dialog.hide();
      Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
    } else {
      // error doing reverse geocoding, load weather data from cache
      weatherServicesHasFailed = true;
      // OPTIONAL: let the user know an error has occurred then fallback to the cached data
      Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();

      cacheService.load(this);
    }
  }

  @Override
  public void geocodeSuccess(LocationResult location) {
    // completed geocoding successfully
    weatherService.refreshWeather(location.getAddress());

    SharedPreferences.Editor editor = preferences.edit();
    editor.putString(getString(R.string.pref_cached_location), location.getAddress());
    editor.apply();
  }

  @Override
  public void geocodeFailure(Exception exception) {
    // GeoCoding failed, try loading weather data from the cache
    cacheService.load(this);
  }

  @Override
  public void onLocationChanged(Location location) {
    geocodingService.refreshLocation(location);
  }

  @Override
  public void onStatusChanged(final String provider, final int status, final Bundle extras) {

  }

  @Override
  public void onProviderEnabled(final String provider) {

  }

  @Override
  public void onProviderDisabled(final String provider) {

  }


}