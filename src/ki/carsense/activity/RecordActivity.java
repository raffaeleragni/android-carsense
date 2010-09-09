package ki.carsense.activity;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;

import ki.carsense.Data;
import ki.carsense.Globals;
import ki.carsense.R;
import ki.carsense.SensorOverlay;
import ki.carsense.dialogs.RecordFilenameDialog;
import ki.carsense.dialogs.SelectRecordedFile;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Paint.Style;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

public class RecordActivity extends Activity
{
	private final static int DATA_GATHER_SPEED = SensorManager.SENSOR_DELAY_GAME;

	private static final int DATA_RECORD_DELAY_MS = 50;

	private static final int DIALOG_RECORD_FILENAME = 0;

	private static final int REQUEST_FILE_SELECTION = 1;

	private static final int REQUEST_PLAYBACK = REQUEST_FILE_SELECTION + 1;

	public static final String VIDEO_EXTENSION = "3gp";

	public static final int MESSAGE_RECORD_FINISHED = 1;

	private RecorderView recorderView;

	private SensorOverlay overlay;

	private WakeLock wakeLock;

	private Data data;

	private MediaRecorder recorder;

	private boolean recording;

	private File outputFile;

	private File outputMovieFile;

	private RandomAccessFile raf;
	
	private Thread recordingThread;
	
	private boolean restartMainActivity;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);

		recorder = new MediaRecorder();
		recorder.reset();
		recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		File f = new File(Environment.getExternalStorageDirectory() + "/carsense/tmp." + VIDEO_EXTENSION);
		f.getParentFile().mkdirs();
		recorder.setOutputFile(f.getAbsolutePath());

		data = new Data();
		recorderView = new RecorderView(this, recorder);
		overlay = new SensorOverlay(this, data);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, this.getClass().getName());

		// Get a real fullscreen
		getWindow().setFormat(PixelFormat.TRANSLUCENT);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(recorderView);
		addContentView(overlay, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		addContentView(new SymbolsOverlay(this), new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		
		restartMainActivity = false;
	}

	Handler message_handler = new Handler()
	{
		public void dispatchMessage(android.os.Message msg)
		{
			switch (msg.what)
			{
				// MediaRecorder once stopped gets RESETTED
				// Activity must be restarted or there is no way to recreate RecordViewer cleanly
				case MESSAGE_RECORD_FINISHED:
				{
					finish();
					if (restartMainActivity)
					{
						startActivity(new Intent(RecordActivity.this, RecordActivity.class));
					}
					break;
				}
			}
		};
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater mi = getMenuInflater();
		mi.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menu_calibrate:
			{
				calibrate();
				return true;
			}
			case R.id.menu_reset_calibrate:
			{
				resetCalibration();
				return true;
			}
			case R.id.menu_start_record:
			{
				if (!recording)
				{
					showDialog(DIALOG_RECORD_FILENAME);
				}
				else
				{
					recording = false;
					restartMainActivity = true;
				}
				break;
			}
			case R.id.menu_session_playback:
			{
				if (!recording)
				{
					Intent i = new Intent(this, SelectRecordedFile.class);
					startActivityForResult(i, REQUEST_FILE_SELECTION);
				}
				break;
			}
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == REQUEST_FILE_SELECTION && resultCode == RESULT_OK)
		{
			Bundle b = new Bundle();
			b.putString("filename", data.getStringExtra("filename"));
			Intent i = new Intent(RecordActivity.this, PlayerActivity.class);
			i.putExtras(b);

			finish();
			startActivityForResult(i, REQUEST_PLAYBACK);
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case DIALOG_RECORD_FILENAME:
			{
				return new RecordFilenameDialog(this, record_filename_ok_listener);
			}
		}

		return super.onCreateDialog(id);
	}

	private RecordFilenameDialog.OKListener record_filename_ok_listener = new RecordFilenameDialog.OKListener()
	{
		@Override
		public void ok(String filename)
		{
			try
			{
				outputFile = new File(Environment.getExternalStorageDirectory() + "/carsense/", filename + ".cs");
				outputFile.getParentFile().mkdirs();
				outputFile.createNewFile();

				outputMovieFile = new File(outputFile.getAbsolutePath() + "." + RecordActivity.VIDEO_EXTENSION);
				recorder.setOutputFile(outputFile.getAbsolutePath() + "." + RecordActivity.VIDEO_EXTENSION);

				raf = new RandomAccessFile(outputFile, "rw");
				raf.writeChars("CARSENSE");
				raf.writeLong(Globals.FILE_VERSION);
				raf.writeLong(DATA_RECORD_DELAY_MS);
				
				recordingThread = new Thread(dataRecorder);
				recordingThread.start();
			}
			catch (Exception e)
			{
				Toast.makeText(RecordActivity.this, e.getClass().getName() + ": " + e.getMessage(), 4000).show();
			}
		}
	};

	private void resetCalibration()
	{
		Globals.calibration.fx = 0;
		Globals.calibration.fy = 0;
		Globals.calibration.fz = 0;
		Globals.calibration.ax = 0;
		Globals.calibration.ay = 0;
		Globals.calibration.az = 0;
	}

	private void calibrate()
	{
		Globals.calibration.fx = data.fx;
		Globals.calibration.fy = data.fy;
		Globals.calibration.fz = data.fz;
		Globals.calibration.ax = data.ax;
		Globals.calibration.ay = data.ay;
		// # Can't calibrate thix axis Globals.calibration.az = data.az - 90;
	}

	private LocationListener locationListener = new LocationListener()
	{
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras)
		{
		}

		@Override
		public void onProviderEnabled(String provider)
		{
		}

		@Override
		public void onProviderDisabled(String provider)
		{
		}

		@Override
		public void onLocationChanged(Location location)
		{
			if (location != null && location.hasSpeed())
			{
				float speed = location.getSpeed();
				// Known bug: sometimes speed goes over 120 m/s, leave unchanged when happens
				if (speed < 120)
				{
					data.speed = location.getSpeed();
				}
				data.lat = (float) location.getLatitude();
				data.lon = (float) location.getLongitude();
				data.alt = (float) location.getAltitude();
			}
			else
			{
				data.speed = 0f;
				data.lat = 0f;
				data.lon = 0f;
				data.alt = 0f;
			}

			overlay.invalidate();
		}
	};

	private SensorEventListener sensorListener = new SensorEventListener()
	{
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy)
		{
		}

		@Override
		public void onSensorChanged(SensorEvent event)
		{
			switch (event.sensor.getType())
			{
				case Sensor.TYPE_ACCELEROMETER:
				{
					data.fx = event.values[0] - Globals.calibration.fx;
					data.fy = event.values[1] - Globals.calibration.fy;
					data.fz = event.values[2] - Globals.calibration.fz;
					break;
				}
				case Sensor.TYPE_ORIENTATION:
				{
					data.ax = event.values[0] - Globals.calibration.ax;
					data.ay = event.values[1] - Globals.calibration.ay;
					data.az = event.values[2] - Globals.calibration.az;
					break;
				}
				case Sensor.TYPE_MAGNETIC_FIELD:
				{
					data.mx = event.values[0];
					data.my = event.values[1];
					data.mz = event.values[2];
					break;
				}
			}
			
			overlay.invalidate();
		}
	};

	class SymbolsOverlay extends View
	{
		private Paint recp;
		
		public SymbolsOverlay(Context c)
		{
			super(c);

			recp = new Paint();
			recp.setStyle(Style.FILL);
			recp.setColor(Color.argb(255, 255, 0, 0));
			recp.setStrokeWidth(0);
		}

		@Override
		protected void onDraw(Canvas c)
		{
			if (recording)
			{
				int w = getWidth();
				int h = getHeight();
				c.drawCircle(w - 40, h - 40, 15, recp);
			}
		}
	}

	Runnable dataRecorder = new Runnable()
	{
		@Override
		public void run()
		{
			recording = true;

			long zerotime = System.currentTimeMillis();
			long t;
			
			try
			{
				recorder.setOutputFile(outputFile.getAbsolutePath() + "." + RecordActivity.VIDEO_EXTENSION);
				recorder.start();

				while (recording)
				{
					t = System.currentTimeMillis();
					raf.writeLong(t - zerotime);
					raf.writeFloat(data.ax);
					raf.writeFloat(data.ay);
					raf.writeFloat(data.az);
					raf.writeFloat(data.fx);
					raf.writeFloat(data.fy);
					raf.writeFloat(data.fz);
					raf.writeFloat(data.mx);
					raf.writeFloat(data.my);
					raf.writeFloat(data.mz);
					raf.writeFloat(data.speed);
					raf.writeFloat(data.lat);
					raf.writeFloat(data.lon);
					raf.writeFloat(data.alt);
					t = System.currentTimeMillis() - t;

					try
					{
						if (t < DATA_RECORD_DELAY_MS)
						{
							Thread.sleep(DATA_RECORD_DELAY_MS - t);
						}
					}
					catch (InterruptedException e)
					{
						recording = false;
					}
				}
			}
			catch (Exception e)
			{
				Log.e(RecordActivity.class.getName(), e.getMessage(), e);
			}
			finally
			{
				try
				{
					raf.close();
					recorder.stop();
					File f = new File(Environment.getExternalStorageDirectory() + "/carsense/tmp." + RecordActivity.VIDEO_EXTENSION);
					f.renameTo(outputMovieFile);
					message_handler.dispatchMessage(Message.obtain(message_handler, RecordActivity.MESSAGE_RECORD_FINISHED));
				}
				catch (Exception e)
				{
				}
			}

			recording = false;
		}
	};

	public class RecorderView extends SurfaceView implements SurfaceHolder.Callback
	{
		MediaRecorder tempRecorder;

		public RecorderView(Context context, MediaRecorder recorder)
		{
			super(context);
			tempRecorder = recorder;
			getHolder().addCallback(this);
			getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		public void surfaceCreated(SurfaceHolder holder)
		{
			if (tempRecorder != null)
			{
				tempRecorder.setPreviewDisplay(getHolder().getSurface());
			}
		}

		public void surfaceDestroyed(SurfaceHolder holder)
		{
			if (tempRecorder != null)
			{
				tempRecorder.release();
			}
		}

		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
		{
			try
			{
				if (tempRecorder != null)
				{
					tempRecorder.setPreviewDisplay(getHolder().getSurface());
					tempRecorder.prepare();
				}
			}
			catch (Exception e)
			{
				Toast.makeText(this.getContext(), e.getMessage(), 3000).show();
				Log.e("", "", e);
			}
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if (wakeLock != null && !wakeLock.isHeld())
		{
			wakeLock.acquire();
		}
		SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ORIENTATION);
		for (Sensor s : sensors)
		{
			sensorManager.registerListener(sensorListener, s, DATA_GATHER_SPEED);
		}
		sensors = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);
		for (Sensor s : sensors)
		{
			sensorManager.registerListener(sensorListener, s, DATA_GATHER_SPEED);
		}
		sensors = sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);
		for (Sensor s : sensors)
		{
			sensorManager.registerListener(sensorListener, s, DATA_GATHER_SPEED);
		}
		LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3, 3, locationListener);
	}

	@Override
	protected void onPause()
	{
		recording = false;
		if (dataRecorder != null)
		{
			recording = false;
			if (recordingThread != null)
			{
				recordingThread.interrupt();
			}
		}
		LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		locationManager.removeUpdates(locationListener);
		SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensorManager.unregisterListener(sensorListener);
		if (wakeLock != null && wakeLock.isHeld())
		{
			wakeLock.release();
		}

		super.onPause();
	}

}
