package find.my.car;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

/**
 * This demo shows how GMS Location can be used to check for changes to the users location.  The
 * "My Location" button uses GMS Location to set the blue dot representing the users location. To
 * track changes to the users location on the map, we request updates from the
 * {@link LocationClient}.
 */
public class MainActivity extends FragmentActivity
    implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener {

	private GoogleMap mMap;
	private LocationClient mLocationClient;
	private Location location, human, car;
	private Button c, h;
	private Marker mHuman, mCar;
  	private LatLng p;
  	private SharedPreferences mPrefs;
	private Editor editor;
	private ArrayList<LatLng> poly;
	private URL url;
	private HttpURLConnection urlConnection;
	private InputStream is;
	private BufferedReader reader;
	private StringBuilder sb;
	private String line, result, encodedString;
	private JSONObject jsonObject;
	private JSONArray routeArray;
	private JSONObject routes, overviewPolylines;
	private List<LatLng> pointToDraw;
	private float distance, lat, lng;
	private Handler updateLocation;
	private PolylineOptions options;
  
  	// These settings are the same as the settings for the map. They will in fact give you updates at
  	// the maximal rates currently possible.
  	private static final LocationRequest REQUEST = LocationRequest.create()
  			.setInterval(5000)         // 5 seconds
  			.setFastestInterval(16)    // 16ms = 60fps
  			.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

  	@Override
  	protected void onCreate(Bundle savedInstanceState) {
  		super.onCreate(savedInstanceState);
  		setContentView(R.layout.mapview);
  	  
  		mPrefs = getPreferences(MODE_PRIVATE);
  		
  		updateLocation = new Handler();
  	  
  		c = (Button) findViewById(R.id.car);
  		c.setVisibility(View.INVISIBLE);
  		c.setEnabled(false);
  		h = (Button) findViewById(R.id.human);
  		h.setVisibility(View.INVISIBLE);
  		h.setEnabled(false);
  	}

  	@Override
  	protected void onResume() {
  		super.onResume();
  		setUpMapIfNeeded();
  		setUpLocationClientIfNeeded();
  		mLocationClient.connect();
  		
  		if (mLocationClient.isConnected()) {
  			c.setEnabled(true);
  			h.setEnabled(true);
  		}
  		
  		//Carrega a posição do carro caso o aplicativo volte ao primeiro plano
  		//Se nenhum valor tenha sido salvo em latitude ou longitude não faz nada
  		//Se há valor salvo para latitude e longitude carrega eles para o mapa e
  		//Seta variavel com esses valores para utilização na verificação de próximidade usuário-carro
  		lat = mPrefs.getFloat("latdest", 1000);
  		lng = mPrefs.getFloat("lngdest", 1000);
  		System.out.println(lat + " - " + lng);
  		if (lat != 1000 && lng != 1000) {
  			System.out.println("Posição do carro carregada!");
  			p = new LatLng(lat,lng);
  			mCar = mMap.addMarker(new MarkerOptions().position(p)
  					.title("My car is here!")
  					.icon(BitmapDescriptorFactory
  							.fromResource(R.drawable.ic_launcher)));
  			c.setVisibility(View.INVISIBLE);
  			h.setVisibility(View.VISIBLE);
  			    
  			car = new Location("My car is here!");
  			car.setLatitude(lat);
  			car.setLongitude(lng);
  		}
  			
  		//Carrega a posição inicial do usuário caso o aplicativo volte ao primeiro plano
  		//Se nenhum valor tenha sido salvo em latitude ou longitude não faz nada
  		//Se há valor salvo para latitude e longitude carrega eles para o mapa,
  		//Executa tarefa assincrona em background para traçado da rota e
  		//Inicializa tarefa para monitoramento da posição atual do usuário
  		lat = mPrefs.getFloat("latorig", 1000);
  		lng = mPrefs.getFloat("lngorig", 1000);
  		System.out.println(lat + " - " + lng);
  		if (lat != 1000 && lng != 1000) {
  			System.out.println("Minha posição carregada!");
  			p = new LatLng(lat,lng);
  			mHuman = mMap.addMarker(new MarkerOptions().position(p)
  					.title("I'm here!")
  					.icon(BitmapDescriptorFactory
  							.fromResource(R.drawable.ic_launcher)));
  			h.setVisibility(View.INVISIBLE);
  			  	
  			String strUrl = "http://maps.googleapis.com/maps/api/directions/json?"
  					+ "origin=" + (mHuman.getPosition().latitude) + ","
  					+ (mHuman.getPosition().longitude)
  					+ "&destination=" + (mCar.getPosition().latitude) + ","
  					+ (mCar.getPosition().longitude)
  					+ "&sensor=false&mode=walking";
  			new TraceRouteTask().execute(strUrl.toString());
  			new TraceRouteTask().cancel(true);
  			  	
  			updateLocation.removeCallbacks(UpdateLocationTask);
  			updateLocation.post(UpdateLocationTask);
  		}
  	}

  	@Override
  	public void onPause() {
  		super.onPause();
  		if (mLocationClient != null) {
  			mLocationClient.disconnect();
  		}
  		
  		//Salva posição inicial do usuário e posição do carro caso o aplicativo saia do primeiro plano
  		editor = mPrefs.edit();
  		if (mHuman != null) {
  			editor.putFloat("latorig", (float) mHuman.getPosition().latitude);
  			editor.putFloat("lngorig", (float) mHuman.getPosition().longitude);
  		}
  		if (mCar != null) {
  			editor.putFloat("latdest", (float) mCar.getPosition().latitude);
  			editor.putFloat("lngdest", (float) mCar.getPosition().longitude);
  		}
  		editor.apply();
  	}

  	private void setUpMapIfNeeded() {
  		// Do a null check to confirm that we have not already instantiated the map.
  		if (mMap == null) {
  			// Try to obtain the map from the SupportMapFragment.
  			mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
  			// Check if we were successful in obtaining the map.
		  	if (mMap != null) {
		  		mMap.setMyLocationEnabled(true);
		  		mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
		  		mMap.getUiSettings().setZoomControlsEnabled(true);
		  		mMap.getUiSettings().setMyLocationButtonEnabled(true);
		  		c.setVisibility(View.VISIBLE);
		  	}
	  	}
  	}

  	private void setUpLocationClientIfNeeded() {
  		if (mLocationClient == null) {
  			mLocationClient = new LocationClient(
  					getApplicationContext(),
  					this,  // ConnectionCallbacks
  					this); // OnConnectionFailedListener
	  	}
  	}

  	/**
   	* Button to get car Location. This demonstrates how to get the car Location as required,
   	* without needing to register a LocationListener.
   	*/
  	public void buttonCar (View v) {
  		if (mLocationClient != null && mLocationClient.isConnected()) {
  			location = mLocationClient.getLastLocation();
  			System.out.println(location);
  			p = new LatLng(location.getLatitude(),location.getLongitude());
  			mCar = mMap.addMarker(new MarkerOptions().position(p)
  					.title("My car is here!")
  					.icon(BitmapDescriptorFactory
  							.fromResource(R.drawable.ic_launcher)));
		  	mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(p, 18));
		  	  			
	      	c.setVisibility(View.INVISIBLE);
	      	h.setVisibility(View.VISIBLE);
	      	
		  	car = new Location("My car is here!");
  			car.setLatitude(lat);
  			car.setLongitude(lng);
	  }
  	}
  	
  	/**
   	* Button to get current Location. This demonstrates how to get the current Location as required,
   	* without needing to register a LocationListener.
   	*/
  	public void buttonHuman (View v) {
  		if (mLocationClient != null && mLocationClient.isConnected()) {
  			location = mLocationClient.getLastLocation();
  			System.out.println(location);
  			p = new LatLng(location.getLatitude(),location.getLongitude());
  			mHuman = mMap.addMarker(new MarkerOptions().position(p)
  					.title("I'm here!")
  					.icon(BitmapDescriptorFactory
  							.fromResource(R.drawable.ic_launcher)));
  			mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(p, 18));

		  	h.setVisibility(View.INVISIBLE);
		  	
		  	String strUrl = "http://maps.googleapis.com/maps/api/directions/json?"
  					+ "origin=" + (mHuman.getPosition().latitude) + ","
  					+ (mHuman.getPosition().longitude)
  					+ "&destination=" + (mCar.getPosition().latitude) + ","
  					+ (mCar.getPosition().longitude)
  					+ "&sensor=false&mode=walking";
  			new TraceRouteTask().execute(strUrl.toString());
  			new TraceRouteTask().cancel(true);
  			  	
  			updateLocation.removeCallbacks(UpdateLocationTask);
  			updateLocation.post(UpdateLocationTask);
	  }
  	}
  	
  	//Tarefa que monitora a movimentação do usuário e verifica se chegou próximo do seu carro
  	private Runnable UpdateLocationTask = new Runnable() {
		public void run() {
  			 if (mLocationClient != null && mLocationClient.isConnected()) {
  				 location = mLocationClient.getLastLocation();
  				 System.out.println(location);
				 p = new LatLng(location.getLatitude(),location.getLongitude());
				 human = new Location ("I'm here!");
				 human.setLatitude(location.getLatitude());
				 human.setLongitude(location.getLongitude());
  					   
				 distance = human.distanceTo(car);
				 
				 if (distance<=5) {
					 //toast
					 mMap.clear();
					 c.setVisibility(View.VISIBLE);
					 updateLocation.removeCallbacks(UpdateLocationTask);
				 }
  			 }
		}
  	};
  	
  	//Tarefa assincrona em background que fará conexão com site do google directions,
  	//Obterá os dados codificados da rota,
  	//Decodificará os dados da rota e
  	//Traçará a rota no mapa para o usuário
  	private class TraceRouteTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... urls) {
  			try {
  				/*httpclient = new DefaultHttpClient();
  				httppost = new HttpPost(urls[0]);
  				response = httpclient.execute(httppost);
  				entity = response.getEntity();
  				is = entity.getContent();
  				httpclient.getConnectionManager().shutdown();
  				reader = new BufferedReader(new InputStreamReader(is, "iso-8859-1"), 8);*/
  				//Conexão com o site
  				url = new URL(urls[0]);
  	            urlConnection = (HttpURLConnection) url.openConnection();
  	            urlConnection.connect();
  	            is = urlConnection.getInputStream();
  	            //Leitura dos dados obtidos do site
  	            reader = new BufferedReader(new InputStreamReader(is));
  				sb = new StringBuilder();
  				sb.append(reader.readLine() + "\n");
  				line = "";
  				while ((line = reader.readLine()) != null) {
  				    //sb.append(line + "\n");
  					sb.append(line);
  				}
  				result = sb.toString();
  				reader.close();
  				//Tratando os dados obtidos para serem decodificados
  				System.out.println(result);
  				jsonObject = new JSONObject(result);
  				routeArray = jsonObject.getJSONArray("routes");
  				routes = routeArray.getJSONObject(0);
  				overviewPolylines = routes.getJSONObject("overview_polyline");
  				encodedString = overviewPolylines.getString("points");
  				return encodedString;
  				
  			} catch (Exception e) {
  				e.printStackTrace();
  			} finally {
  				try {
  					is.close();
  					urlConnection.disconnect();
  				} catch (IOException e) {
  					e.printStackTrace();
  				}		
  			}
  			return null;
  		}
  		
  		protected void onPostExecute(String result) {
  			if (result != null) {
  				//Decodifica os dados pegos no google directions para obter os pontos da rota a ser traçada
  				pointToDraw = decodePoly(encodedString);
  				
  				//Desenha a routa do usuário até o carro utilzando os dados decodificados
  				options = new PolylineOptions();
  		        options.width(4);
  		        options.color(Color.RED);
  		        for (int i = 0; i< pointToDraw.size(); i++ ) {
  		        	options.add(pointToDraw.get(i));
  		        }
  		        mMap.addPolyline(options);
  			}
  		}
  	}
  	
  	//Transforma os dados codificados do google directions em uma lista GeoPoint(latitude,longitude)
  	private List<LatLng> decodePoly(String encoded) {
  	    poly = new ArrayList<LatLng>();
  	    int index = 0, len = encoded.length();
  	    int lat = 0, lng = 0;
  	    
  	    //User Location - esse ponto pode ser diferente do inicial obtido do google directions
  	    //Adicionamos esse ponto para ter o ponto do usuário como o inicial
  	    p = new LatLng(mHuman.getPosition().latitude,mHuman.getPosition().longitude);
  	    poly.add(p);
  	    		
  	    while (index < len) {
  	        int b, shift = 0, result = 0;
  	        do {
  	            b = encoded.charAt(index++) - 63;
  	            result |= (b & 0x1f) << shift;
  	            shift += 5;
  	        } while (b >= 0x20);
  	        int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
  	        lat += dlat;

  	        shift = 0;
  	        result = 0;
  	        do {
  	            b = encoded.charAt(index++) - 63;
  	            result |= (b & 0x1f) << shift;
  	            shift += 5;
  	        } while (b >= 0x20);
  	        int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
  	        lng += dlng;

  	        p = new LatLng((double) lat / 1E5,(double) lng / 1E5);
  	        poly.add(p);
  	    }
  	    //Car Location - esse ponto pode ser diferente do final obtido do google directions
  	    //Adicionamos esse ponto para ter o ponto do usuário como o final
  	    p = new LatLng(mCar.getPosition().latitude,mCar.getPosition().longitude);
  	    poly.add(p);
  	    
  	    return poly;
  	}

  	/**
   	* Implementation of {@link LocationListener}.
   	*/
  	@Override
  	public void onLocationChanged(Location location) {
	  	// Do nothing
  	}

  	/**
   	* Callback called when connected to GCore. Implementation of {@link ConnectionCallbacks}.
   	*/
  	@Override
  	public void onConnected(Bundle connectionHint) {
	  	mLocationClient.requestLocationUpdates(
			  	REQUEST,
			  	this);  // LocationListener
  	}

  	/**
   	* Callback called when disconnected from GCore. Implementation of {@link ConnectionCallbacks}.
   	*/
  	@Override
  	public void onDisconnected() {
	  	// Do nothing
  	}

  	/**
   	* Implementation of {@link OnConnectionFailedListener}.
   	*/
  	@Override
  	public void onConnectionFailed(ConnectionResult result) {
	  	// Do nothing
  	}
}