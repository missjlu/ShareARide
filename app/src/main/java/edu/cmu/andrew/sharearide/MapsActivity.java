package edu.cmu.andrew.sharearide;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import edu.cmu.andrew.utilities.PlaceJSONParser;

public class MapsActivity extends FragmentActivity
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

  private GoogleMap mMap; // Might be null if Google Play services APK is not available.
  private GoogleApiClient mGoogleApiClient;
  private Location mLastLocation;
  private double latitude;
  private double longitude;

  private static final String GEOCODE_BASE_URL = "https://maps.googleapis.com/maps/api/geocode/xml?address=";
  private static final String UBER_PRICE_BASE_URL = "https://api.uber.com/v1/estimates/price?";

  //for autocomplete
  AutoCompleteTextView atvPlaces;
  PlacesTask placesTask;
  ParserTask parserTask;

  @Override
  protected void onCreate (Bundle savedInstanceState) {
    super.onCreate (savedInstanceState);
    setContentView (R.layout.activity_maps);
    buildGoogleApiClient ();
    setUpMapIfNeeded ();

      //System.out.println("in on create");
    buildAutoComplete();
  }

    private void buildAutoComplete() {
        atvPlaces = (AutoCompleteTextView) findViewById(R.id.destiTxt);
        atvPlaces.setThreshold(1);

        atvPlaces.addTextChangedListener(new TextWatcher() {

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
              //  System.out.println("on text changed" + s);
                placesTask = new PlacesTask();
                placesTask.execute(s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
                // TODO Auto-generated method stub
            }

            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub
            }
        });
    }

    /** A method to download json data from url */
    private String downloadUrl(String strUrl) throws IOException{
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while( ( line = br.readLine()) != null){
                sb.append(line);
            }

            data = sb.toString();
            //System.out.println("json" + sb);
            br.close();

        }catch(Exception e){
            Log.d("Exception while downloading url", e.toString());
        }finally{
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    // Fetches all places from GooglePlaces AutoComplete Web Service
    private class PlacesTask extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... place) {
            // For storing data from web service
            String data = "";

            // Obtain browser key from https://code.google.com/apis/console
            String key = "key=AIzaSyCzlb0AafU0DnwEUC_712hPx0zp0phunTQ";

            String input="";

            try {
                input = "input=" + URLEncoder.encode(place[0], "utf-8");
               // System.out.println("do in background input" + input);
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }

            // place type to be searched
            String types = "types=address";

            // Sensor enabled
            String sensor = "sensor=false";

            // Building the parameters to the web service
            String parameters = input+"&"+types+"&"+sensor+"&"+key;

            // Output format
            String output = "json";

            // Building the url to the web service
            String url = "https://maps.googleapis.com/maps/api/place/autocomplete/"+output+"?"+parameters;

            try{
                // Fetching the data from we service
                data = downloadUrl(url);
            }catch(Exception e){
                Log.d("Background Task",e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            // Creating ParserTask
            parserTask = new ParserTask();

            // Starting Parsing the JSON string returned by Web Service
            parserTask.execute(result);
        }
    }
    /** A class to parse the Google Places in JSON format */
    private class ParserTask extends AsyncTask<String, Integer, List<HashMap<String,String>>>{

        JSONObject jObject;

        @Override
        protected List<HashMap<String, String>> doInBackground(String... jsonData) {

            List<HashMap<String, String>> places = null;

            PlaceJSONParser placeJsonParser = new PlaceJSONParser();

            try{
                jObject = new JSONObject(jsonData[0]);

                // Getting the parsed data as a List construct
                places = placeJsonParser.parse(jObject);
                //System.out.println("places" + places.size());
            }catch(Exception e){
                Log.d("Exception",e.toString());
            }
            return places;
        }

        @Override
        protected void onPostExecute(List<HashMap<String, String>> result) {

            String[] from = new String[] { "description"};
            int[] to = new int[] { android.R.id.text1 };

            // Creating a SimpleAdapter for the AutoCompleteTextView
            SimpleAdapter adapter = new SimpleAdapter(getBaseContext(), result, android.R.layout.simple_list_item_1, from, to);

            // Setting the adapter
            atvPlaces.setAdapter(adapter);
        }
    }




    @Override
  protected void onResume () {
    super.onResume ();
    setUpMapIfNeeded();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mGoogleApiClient.connect();
  }

  @Override
  protected void onStop() {
    mGoogleApiClient.disconnect();
    super.onStop();
  }

  /**
   * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
   * installed) and the map has not already been instantiated.. This will ensure that we only ever
   * call {@link #setUpMap()} once when {@link #mMap} is not null.
   * <p/>
   * If it isn't installed {@link SupportMapFragment} (and
   * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
   * install/update the Google Play services APK on their device.
   * <p/>
   * A user can return to this FragmentActivity after following the prompt and correctly
   * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
   * have been completely destroyed during this process (it is likely that it would only be
   * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
   * method in {@link #onResume()} to guarantee that it will be called.
   */
  private void setUpMapIfNeeded () {
    // Do a null check to confirm that we have not already instantiated the map.
    if (mMap == null) {
      // Try to obtain the map from the SupportMapFragment.
      mMap = ((SupportMapFragment) getSupportFragmentManager ().findFragmentById (R.id.map))
          .getMap();
      // Check if we were successful in obtaining the map.
      if (mMap != null) {
        setUpMap ();
      }
    }
  }

  /**
   * This is where we can add markers or lines, add listeners or move the camera. In this case, we
   * just add a marker near Africa.
   * <p/>
   * This should only be called once and when we are sure that {@link #mMap} is not null.
   */
  private void setUpMap() {
    mMap.setMyLocationEnabled(true);
  }

  private void setUpDestination(double dest_latitude, double dest_longitude, String address) {
      Log.i("add marker", "method executed");
      if (mMap != null) {
          Log.i("map not null", "method executed");
          System.out.println("************outside" + dest_latitude + dest_longitude);
          mMap.moveCamera(CameraUpdateFactory.newLatLngZoom (new LatLng (dest_latitude, dest_longitude),13));
          Marker marker = mMap.addMarker(new MarkerOptions()
                  .position(new LatLng(dest_latitude, dest_longitude))
                  .title("Your destination: " + address));
      }
  }

  protected synchronized void buildGoogleApiClient() {
    mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(LocationServices.API)
        .build();
  }

  @Override
  public void onConnected (Bundle connectionHint) {
    mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
            mGoogleApiClient);
    if (mLastLocation != null) {
      latitude = mLastLocation.getLatitude ();
      longitude = mLastLocation.getLongitude();

      // Gets the current location place name
      Geocoder geoCoder = new Geocoder (this);
      List <Address> places = null;
      try {
        places = geoCoder.getFromLocation (latitude, longitude, 1);
      } catch (IOException ioe) { }
      String thisPlace = (places.isEmpty() ? null : places.get (0).getAddressLine (0));

      // Prints current location to TextView
      ((TextView) findViewById (R.id.my_location)).setText ("Current location: " + thisPlace);
      mMap.moveCamera(CameraUpdateFactory.newLatLngZoom (new LatLng (latitude, longitude), 13));
    }
  }

  @Override
  public void onConnectionSuspended(int cause) {
    // The connection has been interrupted.
    // Disable any UI components that depend on Google APIs
    // until onConnected() is called.
  }

  @Override
  public void onConnectionFailed(ConnectionResult result) {
    // This callback is important for handling errors that
    // may occur while attempting to connect with Google.
    //
    // More about this in the next section.
  }

  public void selectDriver(View view){

      String destinationTxt = ((EditText)findViewById(R.id.destiTxt)).getText().toString();

      //cannot make http request in main thread, has to create a asyn helper thread
      //calculatePriceAndTime(destinationTxt);

      new AsyncGooglePlaceSearch().execute(destinationTxt);





      //Intent intent = new Intent(this,DriverSelected.class);
      //startActivity(intent);

  }


    private class AsyncGooglePlaceSearch extends
            AsyncTask<String, Void, String[]> {

        private double dest_latitude = 0;
        private double dest_longitude = 0;
        private String address;

        @Override
        protected String[] doInBackground(String... urls) {
            address = urls[0];
            return calculatePriceAndTime(urls[0]);
        }

        @Override
        protected void onPostExecute(String[] estimates) {
            //ip.placeReady(place);
            ((TextView) findViewById (R.id.my_location)).setText ("Lowest Price: " + estimates[0] + "\n" +"Time to Pickup: " + estimates[1]);
            (findViewById (R.id.requestMainLayout) ).setVisibility(View.INVISIBLE);
            setUpDestination(dest_latitude, dest_longitude, address);
        }

        private String[] calculatePriceAndTime(String destinationTxt) {

            String[] estimates = new String[2];


            if (destinationTxt != null) {

                //First get the destination coordinates and display on Google Map
                getLocation(destinationTxt);

                //Then calculate price for the whole journey and the time for the uber driver to pick up the passenger
                String url = UBER_PRICE_BASE_URL + "start_latitude=" + latitude +"&start_longitude=" + longitude + "&end_latitude=" + dest_latitude + "&end_longitude=" + dest_longitude + "&server_token=" + getString(R.string.uber_api_key);
                Log.i("URL for Uber API", url);
                //String url = "https://api.uber.com/v1/estimates/price?start_latitude=37.625732&start_longitude=-122.377807&end_latitude=37.785114&end_longitude=-122.406677&server_token=" + getString(R.string.uber_api_key);

                try {

                    HttpClient httpClient = new DefaultHttpClient();
                    HttpGet priceRequest = new HttpGet(url);
                    HttpResponse httpResult = httpClient.execute(priceRequest);
                    String json = EntityUtils.toString(httpResult.getEntity(), "UTF-8");
                    JSONObject priceObject = new JSONObject(json);
                    //System.out.println(priceObject.get("prices"));
                    JSONArray allPrice = priceObject.getJSONArray("prices");

                    String[] estimateForProduct = new String[6];
                    //Double[] lowEstimateForProduct = new Double[6];
                    Double lowestEstimateForProduct = 1000.0;

                    for(int i=0; i<allPrice.length(); i++) {
                        //System.out.println(allPrice.get(i));
                        JSONObject priceForEachProduct = (JSONObject)allPrice.get(i);
                        System.out.println(priceForEachProduct.get("estimate"));
                        estimateForProduct[i] = priceForEachProduct.get("estimate").toString();
                        lowestEstimateForProduct = Math.min(lowestEstimateForProduct, Double.valueOf(priceForEachProduct.get("low_estimate").toString()));

                    }
                    //Log.i("price info: ", priceObject.get("estimate").toString());
                    estimates[0] =   String.valueOf(lowestEstimateForProduct);
                    estimates[1] =   String.valueOf(dest_longitude);

                } catch (MalformedURLException e) {
                    Log.i("Hit the malformedURLerror: ", e.toString());
                } catch (IOException ioe) {
                    Log.i("Hit the IO error: ", ioe.toString());
                }
                catch (org.json.JSONException jsone) {
                    Log.i("Hit the JSON error: ", jsone.toString());
                }

            }


            return estimates;

        }

        private void getLocation(String destinationTxt) {

            String url = GEOCODE_BASE_URL + destinationTxt.replaceAll(" ", "+") + "&key=" + getString(R.string.google_maps_key);
            //String url = "https://maps.googleapis.com/maps/api/geocode/xml?address=1600+Amphitheatre+Parkway,+Mountain+View,+CA&key="+ R.string.google_maps_key;
            Document doc = getRemoteXML(url);

            doc.getDocumentElement().normalize();

            NodeList nl = doc.getElementsByTagName("result");

            if (nl.getLength() != 0) {
                Node n = nl.item(0);
                Element e = (Element) n;
                dest_latitude = Double.valueOf(e.getElementsByTagName("lat").item(0).getTextContent());
                dest_longitude = Double.valueOf(e.getElementsByTagName("lng").item(0).getTextContent());

            }

            System.out.println("************" + dest_latitude + dest_longitude);

        }


        private Document getRemoteXML(String url) {
            //Log.i("******", url);
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                InputSource is = new InputSource(url);
                return db.parse(is);
            } catch (Exception e) {
                Log.i("Hit the error: ", e.toString());
                return null;
            }
        }


    }


}
