package com.example.n3w.psusmartparking;

import android.*;
import android.Manifest;
import android.animation.IntEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.androidn3w.psusmartparking.util.DirectionsAPI;
import com.example.androidn3w.psusmartparking.util.DirectionsJSONParser;
import com.example.androidn3w.psusmartparking.util.MapUtil;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.AutocompleteFilter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import pl.tajchert.nammu.Nammu;
import pl.tajchert.nammu.PermissionCallback;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQUEST_LOCATION = 1;
    private String mCurrentLocStr;
    private TextView mLocTextView;
    private GoogleMap mMapView;
    private static final long UPDATE_INTERVAL = 5000;
    private static final long FASTEST_INTERVAL = 1000;
    private List<LatLng> listOfLatLng = new ArrayList<>();
    private LocationRequest mRequest;
    public static int PLACE_AUTOCOMPLETE_REQUEST_CODE = 2;
    private GoogleApiClient mApiClient;
    private Polygon polygon;
    private MarkerOptions mapMarker;
    int currentPt;
    int mAnimationZoom = 15;
    int mPinDrawables[] = new int[]{R.drawable.pin_01,
            R.drawable.pin_02,
            R.drawable.pin_03,
            R.drawable.pin_04,
            R.drawable.pin_05,
            R.drawable.pin_06,
            R.drawable.pin_07};
    private int pinCount = 0;
    private ImageView mClearMarkersBtn;
    private ImageView mAnimationBtn;
    private ImageView mGeoCodingBtn;
    private Circle circle;
    private ValueAnimator vAnimator;
    private LatLng defaultLocation;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindWidgets();
        setEvents();
    }

    private void setEvents() {
        mClearMarkersBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMapView.clear();
                listOfLatLng.clear();
                Toast.makeText(MainActivity.this, "Clear Object Already.", Toast.LENGTH_SHORT).show();
            }
        });

        mGeoCodingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    AutocompleteFilter typeFilter = new AutocompleteFilter.Builder().
                            setTypeFilter(AutocompleteFilter.TYPE_FILTER_NONE).build();

                    Intent intent =
                            new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                                    .setFilter(typeFilter)
                                    .build(MainActivity.this);
                    startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
                } catch (GooglePlayServicesRepairableException e) {
                    // TODO: Handle the error.
                } catch (GooglePlayServicesNotAvailableException e) {
                    // TODO: Handle the error.
                }
            }
        });

        mAnimationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentPt = -1;

                mMapView.animateCamera(
                        CameraUpdateFactory.zoomTo((mAnimationZoom + 2)),
                        5000,
                        animationTask);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                placeMarker(place.getLatLng().latitude, place.getLatLng().longitude, place.getName() + "\n" + place.getAddress());
                drawDirection(place.getLatLng());
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                Toast.makeText(getApplicationContext(), status.toString(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void drawDirection(final LatLng latLng) {

        mMapView.setTrafficEnabled(false);
        Location location = getLastLocation();
        if (location != null) {
            final LatLng origin = convertToLatLng(location);
            new DirectionsAPI(getApplicationContext()).drawDirection(mMapView, origin, latLng, Color.parseColor("#00FF00"), new DirectionsJSONParser.OnDirectionAPIListener() {
                @Override
                public void onFinished(DirectionsAPI api, ArrayList<LatLng> points) {
                    zoomTwoPosition(origin, latLng);
                    Toast.makeText(getApplicationContext(), "Rounte Distance: " + api.routeDistance + "m", Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    public void zoomTwoPosition(LatLng origin, LatLng dest) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(origin);
        builder.include(dest);

        LatLngBounds bounds = builder.build();
        int padding = 300; // offset from edges of the map in pixels
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        mMapView.animateCamera(cu);
    }

    private void placeMarker(double lat, double lng, String title) {
        LatLng latLng = new LatLng(lat, lng);


        MarkerOptions markerOption = new MarkerOptions();
        markerOption.position(latLng);
        markerOption.title(title);
        markerOption.icon(BitmapDescriptorFactory.fromResource(R.drawable.pin_01));
        mMapView.addMarker(markerOption);
        mMapView.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
    }

    private void bindWidgets() {

        mLocTextView = (TextView) findViewById(R.id.mLocationTextView);
        mGeoCodingBtn = (ImageView) findViewById(R.id.mGeoCodingBtn);
        mAnimationBtn = (ImageView) findViewById(R.id.mAnimationBtn);
        mClearMarkersBtn = (ImageView) findViewById(R.id.clearMarkersBtn);

        //bind google map
        FragmentManager fragmentMgr = getSupportFragmentManager();
        SupportMapFragment mMapViewFragment = (SupportMapFragment) fragmentMgr.findFragmentById(R.id.mMapView);
        mMapViewFragment.getMapAsync(this);

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMapView = googleMap;
        setupMap();
    }

    @SuppressWarnings("MissingPermission")
    public Location getLastLocation() {
        Location location = LocationServices.FusedLocationApi.getLastLocation(mApiClient);

        if (location != null) {
            updateLocationTextView(location);
        }
        return location;
    }

    public LatLng convertToLatLng(Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    public void animateToDefaultLocation() {
        if (defaultLocation == null) {
            defaultLocation = convertToLatLng(getLastLocation());
            mMapView.animateCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15));
        }
    }

    @SuppressWarnings("MissingPermission")
    public void startLocationTracking() {

        mMapView.setMyLocationEnabled(true);
        mApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(@Nullable Bundle bundle) {

                        //retrive last location
                        getLastLocation();

                        //create location tracking request
                        mRequest = LocationRequest.create();
                        mRequest.setInterval(UPDATE_INTERVAL);
                        mRequest.setFastestInterval(FASTEST_INTERVAL);
                        mRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
                        LocationServices.FusedLocationApi.requestLocationUpdates(mApiClient, mRequest, new LocationListener() {
                            @Override
                            public void onLocationChanged(Location location) {
                                animateToDefaultLocation();
                                updateLocationTextView(location);
                                drawCircleAtMarker(location);
                            }
                        });
                    }

                    @Override
                    public void onConnectionSuspended(int i) {

                    }
                }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                    }
                }).build();

        mApiClient.connect();
    }

    public void drawCircleAtMarker(Location location) {

        if (circle != null) {
            circle.remove();
            vAnimator.cancel();
        }
        circle = mMapView.addCircle(new CircleOptions().center(convertToLatLng(location))
                .strokeColor(Color.parseColor("#E91E63")).radius(100));

        vAnimator = new ValueAnimator();
        vAnimator.setRepeatCount(ValueAnimator.INFINITE);
        vAnimator.setRepeatMode(ValueAnimator.RESTART);  /* PULSE */
        vAnimator.setIntValues(0, 100);
        vAnimator.setDuration(1000);
        vAnimator.setEvaluator(new IntEvaluator());
        vAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        vAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float animatedFraction = valueAnimator.getAnimatedFraction();
                // Log.d("animation", "" + animatedFraction);
                circle.setRadius(animatedFraction * 100);
            }
        });
        vAnimator.start();
    }

    GoogleMap.CancelableCallback animationTask =
            new GoogleMap.CancelableCallback() {

                @Override
                public void onCancel() {
                    Toast.makeText(getApplicationContext(), "On Cancel", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFinish() {

                    if (++currentPt < listOfLatLng.size()) {

                        //Get the current location
                        Location startingLocation = new Location("starting point");
                        startingLocation.setLatitude(mMapView.getCameraPosition().target.latitude);
                        startingLocation.setLongitude(mMapView.getCameraPosition().target.longitude);

                        //Get the target location
                        Location endingLocation = new Location("ending point");
                        endingLocation.setLatitude(listOfLatLng.get(currentPt).latitude);
                        endingLocation.setLongitude(listOfLatLng.get(currentPt).longitude);

                        //Find the Bearing from current location to next location
                        float targetBearing = startingLocation.bearingTo(endingLocation);

                        LatLng targetLatLng = listOfLatLng.get(currentPt);
                        float targetZoom = mAnimationZoom;

                        //Create a new CameraPosition
                        CameraPosition cameraPosition =
                                new CameraPosition.Builder()
                                        .target(targetLatLng)
                                        .bearing(targetBearing)
                                        .zoom(targetZoom)
                                        .build();


                        mMapView.animateCamera(
                                CameraUpdateFactory.newCameraPosition(cameraPosition),
                                5000,
                                animationTask);

                        // cancel when call stopAnimation
                        // mMapView.stopAnimation();


                        Toast.makeText(getApplicationContext(), "Animate to: " + listOfLatLng.get(currentPt) + "\n" +
                                "Bearing: " + targetBearing, Toast.LENGTH_SHORT).show();


                    } else {
                        Toast.makeText(getApplicationContext(), "onFinish", Toast.LENGTH_SHORT).show();
                    }

                }
            };

    @Override
    protected void onStop() {
        super.onStop();
        mApiClient.disconnect();
    }

    private void updateLocationTextView(Location location) {
        DecimalFormat formatter = new DecimalFormat("#,###.00");
        final String lat = formatter.format(location.getLatitude());
        final String lng = formatter.format(location.getLongitude());

        mCurrentLocStr = String.format("Lat: %s°, Long: %s°", lat, lng);
        mLocTextView.setText(mCurrentLocStr);
    }

    public void setupMap() {

        Nammu.askForPermission(this, Manifest.permission.ACCESS_FINE_LOCATION, new PermissionCallback() {
            @Override
            public void permissionGranted() {
                startLocationTracking();
            }

            @Override
            public void permissionRefused() {
                Toast.makeText(getApplicationContext(), "You cannot use this app, Required permission!", Toast.LENGTH_LONG).show();
                finish();
            }
        });

        mMapView.getUiSettings().setZoomControlsEnabled(true);
        mMapView.setTrafficEnabled(true);

        mMapView.setMapType(GoogleMap.MAP_TYPE_NORMAL);

        mMapView.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
            @Override
            public void onInfoWindowClick(Marker marker) {
                Intent i = new Intent(getApplicationContext(), StreetViewActivity.class);
                i.putExtra("lat", marker.getPosition().latitude);
                i.putExtra("lng", marker.getPosition().longitude);

                startActivity(i);
            }
        });

        mMapView.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                View v = LayoutInflater.from(getApplicationContext()).inflate(R.layout.marker_info_content, null);
                TextView tvTitle = (TextView) v.findViewById(R.id.tv_title);
                if (marker.getTitle() != null && !marker.getTitle().equals("")) {
                    tvTitle.setText(marker.getTitle());
                    tvTitle.setVisibility(View.VISIBLE); // VISIBLE, INVISIBLE, GONE
                } else {
                    tvTitle.setVisibility(View.GONE);
                }
                LatLng latLng = marker.getPosition();
                TextView poistionTextView = (TextView) v.findViewById(R.id.position);
                DecimalFormat formatter = new DecimalFormat("#,###.000");

                String lat = formatter.format(latLng.latitude) + "°";
                String lng = formatter.format(latLng.longitude) + "°";
                poistionTextView.setText(lat + "," + lng);
                return v;
            }

            @Override
            public View getInfoContents(Marker marker) {
                return null;
            }
        });

        mMapView.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                mMapView.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 15));
                marker.showInfoWindow();
                return true;
            }
        });

        mMapView.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                drawPolygon(latLng);
            }
        });

    }

    private void zoomAllMarkers() {

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng position : listOfLatLng) {
            builder.include(position);
        }
        LatLngBounds bounds = builder.build();
        int padding = 200; // offset from edges of the map in pixels
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        mMapView.animateCamera(cu);

    }

    private void showPolygonSize() {
        // calculate area in polygon
        double sizeInSquareMeters = MapUtil.calculatePolygonArea(polygon.getPoints());
        DecimalFormat formatter = new DecimalFormat("#,###.00");
        String msg = formatter.format(sizeInSquareMeters / 1000000) + " kilometer²";
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }


    private void drawPolygon(LatLng latLng) {
        listOfLatLng.add(latLng);
        if (listOfLatLng.size() > 2) {
            if (polygon != null) {
                polygon.remove();
            }


            polygon = mMapView.addPolygon(new PolygonOptions()
                    .addAll(listOfLatLng)
                    .strokeColor(Color.parseColor("#3978DD"))
                    .fillColor(Color.parseColor("#773978DD")));
            polygon.setStrokeWidth(2);
            zoomAllMarkers();
            showPolygonSize();
        }
        mMapView.addMarker(new MarkerOptions().position(latLng).icon(getDummyMarker()));
    }

    public BitmapDescriptor getDummyMarker() {

        int random = pinCount++ % 7;
        return BitmapDescriptorFactory.fromResource(mPinDrawables[random]);
    }
}
