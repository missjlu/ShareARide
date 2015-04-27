package edu.cmu.andrew.sharearide;

import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import edu.cmu.andrew.sharearide.backend.shareARideApi.ShareARideApi;
import edu.cmu.andrew.sharearide.backend.shareARideApi.model.RequestBean;
import edu.cmu.andrew.sharearide.backend.shareARideApi.model.TripBean;
import edu.cmu.andrew.sharearide.backend.shareARideApi.model.UserBean;
import edu.cmu.andrew.sharearide.backend.shareARideApi.model.UserBeanCollection;
import edu.cmu.andrew.utilities.EndPointManager;
import edu.cmu.andrew.utilities.TripSegment;

public class DriverMapFragment extends Fragment {

  private GoogleMap mMap; // Might be null if Google Play services APK is not available.
  private RelativeLayout mLayout;
  private SARActivity mContext;
  private List<LatLng> directions;
  private List<TripSegment> trip;
  private int currentTrip;

  @Override
  public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mContext = (SARActivity) super.getActivity ();
    mLayout = (RelativeLayout) inflater.inflate (R.layout.activity_passenger_map, container, false);

    directions = new ArrayList<> ();
    currentTrip = -1;
    setUpMapIfNeeded ();
    initTrip ();
    return mLayout;
  }

  @Override
  public void onResume () {
    super.onResume ();
    setUpMapIfNeeded ();
  }

  /**
   * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
   * installed) and the map has not already been instantiated.. This will ensure that we only ever
   * call {@link #setUpMap()} once when {@link #mMap} is not null.
   * <p/>
   * If it isn't installed {@link com.google.android.gms.maps.SupportMapFragment} (and
   * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
   * install/update the Google Play services APK on their device.
   * <p/>
   * A user can return to this FragmentActivity after following the prompt and correctly
   * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
   * have been completely destroyed during this process (it is likely that it would only be
   * stopped or paused), {@link #onCreate(android.os.Bundle)} may not be called again so we should call this
   * method in {@link #onResume()} to guarantee that it will be called.
   */
  private void setUpMapIfNeeded () {
    // Do a null check to confirm that we have not already instantiated the map.
    if (mMap == null) {
      // Try to obtain the map from the SupportMapFragment.
      mMap = ((SupportMapFragment) mContext.getSupportFragmentManager ().findFragmentById (R.id.map))
          .getMap ();
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
  private void setUpMap () {
    mMap.moveCamera (CameraUpdateFactory.newLatLngZoom (
        new LatLng (mContext.getLatitude (), mContext.getLongitude ()), 13));
    mMap.setMyLocationEnabled(true);
  }

  private void initTrip () {
    trip = new ArrayList<TripSegment> ();
    updateTripRiders (0);
  }

  private void updateTripRiders (int numOfRiders) {
    new AsyncTask<Integer, Void, Void> (){
      @Override
      protected Void doInBackground(Integer... params) {
        TripBean trip = new TripBean ();
        try {
          trip = EndPointManager.getEndpointInstance ().updateTrip(params[0], params[1]).execute ();
        } catch (IOException e) {
          e.printStackTrace ();
        }

        currentTrip = trip.getTripId ();

        return null;
      }
    }.execute (mContext.getUserID (), 0);
  }

  private void endTrip () {
    new AsyncTask<Integer, Void, Void> (){
      @Override
      protected Void doInBackground(Integer... params) {
        try {
          EndPointManager.getEndpointInstance ().endTrip(params[0]).execute ();
        } catch (IOException e) {
          e.printStackTrace ();
        }

        currentTrip = -1;

        return null;
      }
    }.execute (mContext.getUserID ());
    // *** calculate remaining fare

    trip = null;
  }

  private void readMessage () {

  }

  private void acceptRequest (RequestBean rb) {
    LatLng rSrc = new LatLng (rb.getSrcLatitude (), rb.getSrcLongitude ());
    LatLng rDst = new LatLng (rb.getDstLatitude (), rb.getDstLongitude ());

    List<LatLng> paths = new ArrayList<> ();
    paths.add (rSrc);
    paths.add (rDst);

    updateTripRiders (rb.getNumOfRiders ());
    List<Integer> passengers = new ArrayList<> ();
    if (trip.size () > 0) {
      TripSegment previous = trip.get (trip.size () - 1);
      paths.add (previous.getDestination ());
      previous.setDestination (rSrc);
      previous.setCompleted (true);

      passengers.addAll (previous.getPassengers ());
      passengers.add (new Integer (rb.getPassUserId ()));
    }

    for (TripSegment ts : trip) {
      if (! ts.isCompleted ()) {
        paths.add (ts.getDestination ());
      }
    }

    LatLng[] ll = new LatLng[paths.size ()];
    ll = paths.toArray (ll);
    new NextRouteTask (passengers).execute (ll);
    new AddTRTask ().execute (currentTrip, rb.getRequestId ());
  }

  private void fulfillRequest (RequestBean rb) {
    LatLng rDst = new LatLng (rb.getDstLatitude (), rb.getDstLongitude ());

    List<LatLng> paths = new ArrayList<> ();
    paths.add (rDst);
    TripSegment previous = trip.get (trip.size () - 1);
    previous.setCompleted (true);

    List<Integer> passengers = previous.getPassengers ();
    passengers.remove (new Integer (rb.getPassUserId ()));
    if (passengers.size () == 0) {
      endTrip ();
    } else {
      for (TripSegment ts : trip) {
        if (! ts.isCompleted ()) {
          paths.add (ts.getDestination ());
        }
      }

      LatLng[] ll = new LatLng[paths.size ()];
      ll = paths.toArray (ll);
      new NextRouteTask (passengers).execute (ll);
    }
  }

  class NextRouteTask extends AsyncTask <LatLng, Void, JSONArray> {

    List<Integer> passengers;

    public NextRouteTask (List<Integer> passengers) {
      this.passengers = passengers;
    }

    @Override
    protected JSONArray doInBackground (LatLng... data) {
      String key = "key=" + getString (R.string.google_maps_places_key);
      int minTimeDistance = Integer.MAX_VALUE;
      int minDistance = 0;
      int minTime = 0;
      LatLng minDestination = null;
      JSONArray minSteps = null;
      String origin = "origin=" + data[0].latitude + "," + data[0].longitude + "&";

      for (int i = 1; i < data.length; i++) {
        String destination = "destination=" + data[i].latitude + "," + data[i].longitude + "&";
        String json = mContext.getRemoteJSON (mContext.DIRECTION_BASE_URL + origin + destination + key);

        try {
          JSONObject routeObject = new JSONObject (json);
          JSONObject route = (JSONObject) routeObject.getJSONArray ("routes").get (0);
          JSONObject leg = (JSONObject) route.getJSONArray ("legs").get (0);

          int distance = Integer.parseInt (leg.getJSONObject ("distance").get ("value").toString ());
          int duration = Integer.parseInt (leg.getJSONObject ("duration").get ("value").toString ());

          int timeDistance = distance * duration;
          if (timeDistance < minTimeDistance) {
            minTimeDistance = timeDistance;
            minDistance = distance;
            minTime = duration;
            minDestination = data[i];
            minSteps = leg.getJSONArray ("steps");
          }
        } catch (JSONException jsone) {
          Log.i ("Hit the JSON error: ", jsone.toString ());
        }
      }

      trip.add (new TripSegment (trip.size (), data [0], minDestination, minDistance, minTime, passengers));

      return minSteps;
    }

    @Override
    protected void onPostExecute (JSONArray steps) {
      try {
        String polyline;

        for (int i = 0; i < steps.length (); i++) {
          JSONObject step = (JSONObject) steps.get (i);
          polyline = ((JSONObject) step.get ("polyline")).get ("points").toString ();

          directions.addAll (decodePoly (polyline));
        }
      } catch (JSONException jsone) {
        Log.i ("Hit the JSON error: ", jsone.toString ());
      }

      mMap.clear ();
      mMap.addPolyline (new PolylineOptions ()
          .addAll (directions)
          .width (10)
          .color (Color.rgb (1, 169, 212)));
    }

  }

  private List<LatLng> decodePoly (String encoded) {
    List<LatLng> poly = new ArrayList<LatLng> ();
    int index = 0, len = encoded.length ();
    int lat = 0, lng = 0;

    while (index < len) {
      int b, shift = 0, result = 0;
      do {
        b = encoded.charAt (index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lat += dlat;

      shift = 0;
      result = 0;
      do {
        b = encoded.charAt (index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);
      int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
      lng += dlng;

      LatLng ll = new LatLng((((double) lat / 1E5)),(((double) lng / 1E5)));
      poly.add (ll);
    }

    return poly;
  }

  class AddTRTask extends AsyncTask<Integer, Void, Void> {

    @Override
    protected Void doInBackground (Integer... data) {
      try {
        EndPointManager.getEndpointInstance ().updateTripRequest (data[0], data[1]).execute ();
      } catch (IOException e) {
        e.printStackTrace ();
      }
      return null;
    }
  }
}
