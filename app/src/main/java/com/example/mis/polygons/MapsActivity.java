package com.example.mis.polygons;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toIntExact;
import static java.lang.Math.toRadians;

/*
Extensive additions from google maps sample project: CurrentPlaceDetailsOnMap
https://developers.google.com/maps/documentation/android-api/current-place-tutorial
*/

public class MapsActivity extends FragmentActivity implements View.OnClickListener, OnMapReadyCallback,GoogleMap.OnMapLongClickListener {

    private EditText label_input;
    private Button clear_btn;
    private Button polygon_btn;
    private TextView hint_text;

    private GoogleMap mMap;
    private int marker_count;
    private int polygon_count;
    private boolean mLocationPermissionGranted;
    private boolean drawing_polygon;
    private Polyline polyline;

    private static final String TAG = MapsActivity.class.getSimpleName();

    // The entry point to the Fused Location Provider.
    private FusedLocationProviderClient mFusedLocationProviderClient;
    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private Location mLastKnownLocation;

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private final LatLng mDefaultLocation = new LatLng(-33.8523341, 151.2106085);
    private static final int DEFAULT_ZOOM = 15;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;

    private SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //link views
        setContentView(R.layout.activity_maps);
        label_input = findViewById(R.id.label_input);
        clear_btn = findViewById(R.id.clear_btn);
        polygon_btn = findViewById(R.id.polygon_btn);
        hint_text = findViewById(R.id.hint_text);

        clear_btn.setOnClickListener(this);
        polygon_btn.setOnClickListener(this);

        sharedPreferences = getPreferences(Context.MODE_PRIVATE);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Construct a FusedLocationProviderClient.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        marker_count = 0;
        polygon_count = 0;
        drawing_polygon = false;

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

        getLocationPermission();

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI();

        // Get the current location of the device and set the position of the map.
        getDeviceLocation();

        mMap.setOnMapLongClickListener(this);

        //load saved markers from preferences
        loadMarkers();
        loadPolygons();

        Log.d(TAG, "onMapReady: map loaded");
    }

    private void getLocationPermission() {
        /* https://developers.google.com/maps/documentation/android-api/current-place-tutorial
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        mLocationPermissionGranted = false;
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true;
                }
            }
        }
        updateLocationUI();
    }

    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            mMap.moveCamera(CameraUpdateFactory
                                    .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM));
                            mMap.getUiSettings().setMyLocationButtonEnabled(false);
                        }
                    }
                });
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    private void updateLocationUI() {
        if (mMap == null) {
            return;
        }
        try {
            if (mLocationPermissionGranted) {
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                mMap.setMyLocationEnabled(false);
                mMap.getUiSettings().setMyLocationButtonEnabled(false);
                mLastKnownLocation = null;
                getLocationPermission();
            }
        } catch (SecurityException e)  {
            Log.e("Exception: %s", e.getMessage());
        }
    }

    @Override
    public void onClick(View view){

        switch (view.getId()){

            case R.id.clear_btn:
                //reset map and preferences
                Log.d(TAG, "onClick: clearing markers and polygons");
                mMap.clear();
                marker_count = 0;
                polygon_count = 0;
                polyline = null;
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.clear();
                editor.commit();
                break;

            case R.id.polygon_btn:
                //start or end polygon
                handlePolygons();
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {

        //add a marker
        String marker_txt = label_input.getText().toString();
        label_input.setText("");
        if (marker_txt.equals(""))
            marker_txt = "marker " + marker_count;

        newMarker(latLng, marker_txt, false);

        if (drawing_polygon)
            addMarkertoPolygon(latLng);
    }

    //draws and saves marker
    private void newMarker(LatLng marker_position, String marker_txt, boolean centroid){
        marker_count++;
        drawMarker(marker_position, marker_txt, centroid);
        saveMarker(marker_position, marker_txt);
    }
    private void drawMarker(LatLng marker_position, String marker_txt, boolean centroid){
        Marker m = mMap.addMarker(new MarkerOptions()
                .position(marker_position)
                .title(marker_txt));

        //set colour of marker
        //https://developers.google.com/maps/documentation/android-api/marker
        if (centroid){
            m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
        }
        else {
            m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        }

    }
    //saves marker info in shared prefs
    private void saveMarker(LatLng marker_position, String marker_txt){
        SharedPreferences.Editor editor = sharedPreferences.edit();

        //create a new string set
        Set<String> prefSet = new HashSet<String>();
        //add lat:
        prefSet.add("lat=" + Double.toString(marker_position.latitude));
        //add long
        prefSet.add("lon=" + Double.toString(marker_position.longitude));
        //add marker
        prefSet.add("label="+ marker_txt);

        editor.putStringSet("marker" + (marker_count-1), prefSet);
        editor.apply();

        Log.d(TAG, "saveMarker: marker saved to preferences as marker " + (marker_count-1));
    }

    //loads markers from saved sharedPreferences
    private void loadMarkers(){

        boolean continue_load = true;
        int marker_to_load = 0;
        while (continue_load){

            String prefString = "marker" + Integer.toString(marker_to_load);
            if (sharedPreferences.contains(prefString)){
                Log.d(TAG, "loadMarkers: loading marker " + marker_to_load);

                Set<String> marker_info = sharedPreferences.getStringSet(prefString, null);
                double longitude = 0.0, latitude = 0.0;
                String marker_text = "";
                boolean isCentroid = false;

                if (marker_info != null){

                    for (String pref : marker_info){
                        if (pref.startsWith("lat=")){
                            latitude = Double.parseDouble(pref.substring(pref.lastIndexOf('=') + 1));
                        }
                        else if (pref.startsWith("lon=")){
                            longitude = Double.parseDouble(pref.substring(pref.lastIndexOf('=') + 1));
                        }
                        else if (pref.startsWith("label=")){
                            marker_text = pref.substring(pref.indexOf('=') + 1);
                            if (marker_text.startsWith("Polygon Area"))
                                isCentroid = true;
                        }
                        else {
                            Log.d(TAG, "loadMarkers: erroneous marker info found");
                        }
                    }
                    // add marker
                    Marker m = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(latitude,longitude))
                            .title(marker_text));
                    marker_count++;

                    //set colour
                    if (isCentroid)
                        m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE));
                    else
                        m.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));

                    marker_to_load++;
                }
            }
            else {
                //assume no more markers with higher numbers - stop load
                Log.d(TAG, "loadMarkers: finished loading markers");
                continue_load = false;
            }
        }
    }
    //save polygon to shared prefs
    private void savePolygon(int numPoints){
        SharedPreferences.Editor editor = sharedPreferences.edit();

        //create a new string set
        Set<String> prefSet = new HashSet<String>();
        //add marker indexes that make up polygon
        //assume that centroid hasnt been added yet
        for (int marker = marker_count - 1; marker > (marker_count - 1 - numPoints); marker--){
            prefSet.add(Integer.toString(marker));
        }

        editor.putStringSet("polygon" + polygon_count, prefSet);
        editor.apply();

        Log.d(TAG, "savePolygon: saved polygon " + polygon_count + "to preferences");

        polygon_count++;
    }

    //load polygons form shared prefs
    private void loadPolygons(){

        boolean continue_load = true;
        int polygon_to_load = 0;
        while (continue_load){

            String prefString = "polygon" + Integer.toString(polygon_to_load);
            if (sharedPreferences.contains(prefString)){
                //if found polygon
                //get markers that should be loaded
                Set<String> polygon_markers = sharedPreferences.getStringSet(prefString, null);
                ArrayList<LatLng> polygon_points = new ArrayList<>();

                if (polygon_markers != null){

                    //find lowest numbered marker
                    int firstMarker = Integer.MAX_VALUE;
                    for (String s : polygon_markers){
                        if (Integer.parseInt(s) < firstMarker)
                            firstMarker = Integer.parseInt(s);
                    }

                    //for each marker in polygon - load in order
                    for (int i = firstMarker; i < firstMarker + polygon_markers.size(); i++){
                        //find marker in preferences and retrieve lat and long
                        double mLat = 0.0, mLong = 0.0;
                        String marker_key = "marker" + i;
                        if (sharedPreferences.contains(marker_key)){
                            Set<String> marker_info = sharedPreferences.getStringSet(marker_key, null);
                            for (String s : marker_info){
                                if (s.startsWith("lat"))
                                    mLat = Double.parseDouble(s.substring(s.indexOf('=') + 1));
                                else if (s.startsWith("lon"))
                                    mLong = Double.parseDouble(s.substring(s.indexOf('=') + 1));
                            }
                            //add point to polygon as first point - reverse scanning order
                            polygon_points.add(0, new LatLng(mLat, mLong));
                        }
                        else {
                            Log.d(TAG, "loadPolygons: error retrieving a marker from preferences");
                        }
                    }

                    // add polygon
                    PolygonOptions po = new PolygonOptions();
                    po.fillColor(0x7FF9CEBB);
                    po.strokeColor(0xFFF9CEBB);
                    po.clickable(true);
                    for (LatLng point : polygon_points) {
                        po.add(point);
                    }
                    Polygon polygon = mMap.addPolygon(po);


                    polygon_count++;
                    polygon_to_load++;
                }
            }
            else {
                //assume no more polygons with higher numbers - stop load
                continue_load = false;
            }
        }
    }


    private void addMarkertoPolygon(LatLng marker_position){

        //if this is the first point in the line
        // polyline must be initialised
        if (polyline == null){
            PolylineOptions po = new PolylineOptions();
            po.add(marker_position);
            polyline = mMap.addPolyline(po);
        }
        else {
            List<LatLng> points = polyline.getPoints();
            points.add(marker_position);
            polyline.setPoints(points);
        }
    }

    //completes polygon if necessary and handles area and centroid calculation
    private void handlePolygons(){

        //check if starting or finishing
        if (drawing_polygon){

            //finish drawing
            drawing_polygon = false;
            //change button and hint text
            polygon_btn.setText(R.string.polygon_btn_text_start);
            hint_text.setText(R.string.hint_text_string);

            //check for markers
            if (polyline != null){

                List<LatLng> polygon_points = polyline.getPoints();
                if (polygon_points.size() > 2) {
                    PolygonOptions po = new PolygonOptions();
                    po.fillColor(0x7FF9CEBB);
                    po.strokeColor(0xFFF9CEBB);
                    po.clickable(true);
                    for (LatLng point : polygon_points) {
                        po.add(point);
                    }
                    Polygon polygon = mMap.addPolygon(po);
                    Toast.makeText(this, "Polygon Created", Toast.LENGTH_SHORT).show();

                    //save polygon
                    savePolygon(polygon_points.size());

                    //add centroid marker
                    // with area as label
                    double area = calculatePolygonAreaFromLatLng(polygon.getPoints());
                    LatLng centroid = calculateCentroid(polygon.getPoints());
                    //add marker
                    //set text according to area - arbitrary threshold
                    String area_text;
                    if (area < 100000){
                        area_text = String.format("%.0f m2", area);
                    }
                    else if (area > 1000000000){
                        area_text = String.format("%.0f km2", area/1000000);
                    }
                    else {
                        area_text = String.format("%.3f km2", area/1000000);
                    }
                    newMarker(centroid, "Polygon Area = " + area_text, true);
                    polyline.remove();
                    polyline = null;
                }
                else {
                    Toast.makeText(this, "At least 3 markers are needed for a polygon",
                            Toast.LENGTH_SHORT).show();

                    //reset polyline here - delete x most recent markers
                    polyline.remove();
                    polyline = null;
                }
            }
            else {
                //polyline is null - no points added yet
                Toast.makeText(this, "At least 3 markers are needed for a polygon",
                        Toast.LENGTH_SHORT).show();
            }

        }
        else {
            //start drawing
            drawing_polygon = true;

            //change button and hint text
            polygon_btn.setText(R.string.polygon_btn_text_end);
            hint_text.setText(R.string.hint_text_string_polygon);
        }
    }

    //calculate the area from co0-ordinates described by doubles
    //used to get area in m2
    private double calcPolygonAreaDoubles(List<Point> points) {

        //http://paulbourke.net/geometry/polygonmesh/PolygonUtilities.java
        //check that last point is equal to first point - add if not
        if (points.get(0).x != points.get(points.size() - 1).x
                || points.get(0).y != points.get(points.size() - 1).y){
            points.add(points.get(0));
        }

        double a = 0.0;
        int j;
        for (int i = 0; i < points.size(); i++){
            j = (i + 1) % points.size();
            a += points.get(i).x * points.get(j).y;
            a -= points.get(j).x * points.get(i).y;
        }
        double area = abs(a * 0.5);

        Log.d(TAG, "calculatePolygonArea: as " + area + "m2");

        return area;
    }

    //returns area in m2 when given a set of latitudes and longitudes
    private double calculatePolygonAreaFromLatLng(List<LatLng> points){
        //http://paulbourke.net/geometry/polygonmesh/PolygonUtilities.java

        Log.d(TAG, "calculatePolygonArea: started calculation");

        double area = 0.0;
        double OFFSET_m = 500000.0;
        List<Point> meter_points = new ArrayList<Point>();

        //set first point as 0,0
        meter_points.add(new Point(OFFSET_m, OFFSET_m));
        LatLng ref_point = points.get(0);

        //for each other point, calculate co-ordinates as metres and add
        for (int i = 1; i < points.size(); i++){
            LatLng p = points.get(i);
            double lat_m = haversine_distance
                    (new LatLng(ref_point.latitude, p.longitude), new LatLng(p.latitude, p.longitude));
            double long_m = haversine_distance
                    (new LatLng(p.latitude, ref_point.longitude), new LatLng(p.latitude, p.longitude));

            //adjust for negative differences
            if (p.latitude < ref_point.latitude)
                lat_m *= -1;
            if (p.longitude < ref_point.longitude)
                long_m *= -1;

            //save to list
            meter_points.add(new Point(long_m + OFFSET_m, lat_m + OFFSET_m));
        }

        area = calcPolygonAreaDoubles(meter_points);

        return area;
    }

    //calculates centroid of polygon described by LatLng points
    // returns that LatLng point
    private LatLng calculateCentroid(List<LatLng> points){

        //calculate centroid
        //ref Paul Bourke
        //http://www.seas.upenn.edu/~sys502/extra_materials/Polygon%20Area%20and%20Centroid.pdf

        double centr_lon = 0.0, centr_lat = 0.0;
        double sum = 0.0;

        int j;
        for (int i = 0; i < points.size(); i++){
            j = (i+1) % points.size();
            LatLng p1 = points.get(i);
            LatLng p2 = points.get(j);
            double factor = (p1.longitude * p2.latitude) - (p2.longitude * p1.latitude);
            sum += factor;
            centr_lon += (p1.longitude + p2.longitude) * factor;
            centr_lat += (p1.latitude + p2.latitude) * factor;
        }

        double area = 0.5 * sum;
        centr_lat = centr_lat / 6 / area;
        centr_lon = centr_lon / 6 / area;

        return new LatLng(centr_lat, centr_lon);
    }

    //calculates distance in metres between 2 points on earth modeled as sphere
    private double haversine_distance (LatLng p1, LatLng p2){
        //www.movable-type.co.uk/scripts/latlong.html

        double lat_diff = toRadians(p1.latitude - p2.latitude);
        double long_diff = toRadians(p1.longitude - p2.longitude);
        final double EARTH_RADIUS = 6371000; // metres

        double a = sin(lat_diff / 2.0) * sin(lat_diff / 2.0) +
                cos(toRadians(p1.latitude)) * cos(toRadians(p2.latitude)) * sin(long_diff / 2.0) * sin(long_diff / 2.0);
        double c = 2 * atan2(sqrt(a), sqrt(1.0 - a));
        double distance = EARTH_RADIUS * c;

        return distance;

    }

}
