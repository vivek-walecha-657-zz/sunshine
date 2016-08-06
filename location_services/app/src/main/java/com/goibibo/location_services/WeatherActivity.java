/**
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2015 Yoel Nunez <dev@nunez.guru>
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.goibibo.location_services;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.goibibo.location_services.data.Channel;
import com.goibibo.location_services.data.Condition;
import com.goibibo.location_services.data.LocationResult;
import com.goibibo.location_services.listener.GeocodingServiceListener;
import com.goibibo.location_services.listener.WeatherServiceListener;
import com.goibibo.location_services.service.GoogleMapsGeocodingService;
import com.goibibo.location_services.service.WeatherCacheService;
import com.goibibo.location_services.service.YahooWeatherService;


public class WeatherActivity extends AppCompatActivity implements WeatherServiceListener, GeocodingServiceListener, LocationListener {

  private ImageView weatherIconImageView;
  private TextView temperatureTextView;
  private TextView conditionTextView;
  private TextView locationTextView;

  private YahooWeatherService weatherService;
  private GoogleMapsGeocodingService geocodingService;
  private WeatherCacheService cacheService;

  private ProgressDialog dialog;

  // weather service fail flag
  private boolean weatherServicesHasFailed = false;

  private SharedPreferences preferences = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_weather);

    weatherIconImageView = (ImageView) findViewById(R.id.weatherIconImageView);
    temperatureTextView = (TextView) findViewById(R.id.temperatureTextView);
    conditionTextView = (TextView) findViewById(R.id.conditionTextView);
    locationTextView = (TextView) findViewById(R.id.locationTextView);

    preferences = PreferenceManager.getDefaultSharedPreferences(this);

    weatherService = new YahooWeatherService(this);
    weatherService.setTemperatureUnit(preferences.getString(getString(R.string.pref_temperature_unit), null));

    geocodingService = new GoogleMapsGeocodingService(this);
    cacheService = new WeatherCacheService(this);

    if (preferences.getBoolean(getString(R.string.pref_needs_setup), true)) {
      startSettingsActivity();
    } else {

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
    locationManager.requestSingleUpdate(provider, this, null);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return true;
  }

  private void startSettingsActivity() {
    Intent intent = new Intent(this, SettingsActivity.class);
    startActivity(intent);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.currentLocation:
        dialog.show();
        getWeatherFromCurrentLocation();
        return true;
      case R.id.settings:
        startSettingsActivity();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void serviceSuccess(Channel channel) {
    dialog.hide();

    Condition condition = channel.getItem().getCondition();

    int resourceId = getResources().getIdentifier("drawable/icon_" + condition.getCode(), null, getPackageName());

    @SuppressWarnings("deprecation")
    Drawable weatherIconDrawable = getResources().getDrawable(resourceId);

    weatherIconImageView.setImageDrawable(weatherIconDrawable);

    String temperatureLabel = getString(R.string.temperature_output, condition.getTemperature(), channel.getUnits().getTemperature());

    temperatureTextView.setText(temperatureLabel);
    conditionTextView.setText(condition.getDescription());
    locationTextView.setText(channel.getLocation());
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
  public void onStatusChanged(String s, int i, Bundle bundle) {
    // OPTIONAL: implement your custom logic here
  }

  @Override
  public void onProviderEnabled(String s) {
    // OPTIONAL: implement your custom logic here
  }

  @Override
  public void onProviderDisabled(String s) {
    // OPTIONAL: implement your custom logic here
  }
}
