package ki.carsense.activity;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import ki.carsense.Data;
import ki.carsense.SensorOverlay;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Paint.Style;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Process;
import android.os.PowerManager.WakeLock;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PlayerActivity extends Activity
{
	// CARSENSE string
	private static final int START_HEADER = 16;
	
	// Two longs
	private static final int END_HEADER = START_HEADER + 8 + 8;
	
	// 13 Floats and 1 Long
	private static final int RECORD_SIZE = 4 * 13 + 8;
	
	private File dataFile;
	
	private File movieFile;
	
	private SensorOverlay overlay;
	
	private MediaPlayer player;
		
	private Data data;
		
	private RandomAccessFile raf;
	
	private long delay;
	
	private long size;
	
	private long slots;
	
	private SurfaceView videoSurface;
	
	private boolean playing;
	
	private WakeLock wakeLock;
	
	private int videoDuration;
	
	private SeekBar progressBar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND);
		
		getWindow().setFormat(PixelFormat.TRANSLUCENT);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		data = new Data();
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, this.getClass().getName());
		
		Bundle b = getIntent().getExtras();
		String filename = b.getString("filename");
		dataFile = new File(Environment.getExternalStorageDirectory() + "/carsense/", filename);
		movieFile = new File(dataFile.getAbsoluteFile()+"."+RecordActivity.VIDEO_EXTENSION);

		if (!dataFile.exists() || !movieFile.exists())
		{
			Toast.makeText(this, "Missing files", 5000).show();
			finish();
			return;
		}

		player = new MediaPlayer();
		
		try
		{
			raf = new RandomAccessFile(dataFile, "r");
			size = raf.length();
			slots = (long) ((size - END_HEADER) / RECORD_SIZE);
			raf.seek(START_HEADER);
			
			// no version control yet [version]
			raf.readLong();
			
			delay = raf.readLong();
		}
		catch (Exception e)
		{
			Log.e(this.getClass().getName(), e.getMessage(), e);
			Toast.makeText(PlayerActivity.this, "Error while reading files", 5000).show();
			finish();
			return;
		}
		
		progressBar = new SeekBar(this);
		overlay = new SensorOverlay(this, data);
		videoSurface = new SurfaceView(this);
		videoSurface.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		videoSurface.getHolder().addCallback(new SurfaceHolder.Callback()
		{
			@Override
			public void surfaceCreated(SurfaceHolder holder)
			{
			}
			
			@Override
			public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
			{
				player.setDisplay(videoSurface.getHolder());
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder)
			{
				player.release();
			}
		});
		
		setContentView(videoSurface);
		addContentView(overlay, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		addContentView(new SymbolsOverlay(this), new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		FrameLayout pblayout = new FrameLayout(this);
		addContentView(pblayout, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		FrameLayout.LayoutParams pblayoutparams = new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
		pblayoutparams.setMargins(120, 20, 120, 0);
		pblayout.addView(progressBar, pblayoutparams);
				
		progressBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener()
		{
			@Override
			public void onStopTrackingTouch(SeekBar seekBar)
			{
			}
			
			@Override
			public void onStartTrackingTouch(SeekBar seekBar)
			{
			}
			
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
			{
				if (fromUser)
				{
					seek(progress);
				}
			}
		});
		
		overlay.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				toggleplay();
			}
		});
		
		try
		{
			player.reset();
			player.setDataSource(movieFile.getAbsolutePath());
			player.prepare();
			videoDuration = player.getDuration();
			progressBar.setMax(videoDuration);
		}
		catch (Exception e)
		{
			Log.e(this.getClass().getName(), e.getMessage(), e);
			Toast.makeText(PlayerActivity.this, e.getMessage(), 5000).show();
			finish();
			return;
		}
	}
		
	private void toggleplay()
	{
		if (playing)
		{
			playing = false;
		}
		else
		{
			playing = true;
			Thread t = new Thread(dataPlayer);
			t.start();
			t = new Thread(progressBarUpdater);
			t.start();
		}
	}
	
	public void seek(int ms)
	{
		seekFile(ms);
		player.seekTo(ms);
		
		if (!playing)
		{
			try
			{
				readRecord();
			}
			catch (IOException e)
			{
			}
			overlay.postInvalidate();
			// TODO: how to show the right video frame in SEEK?
//			player.start();
//			player.pause();
		}
	}
	
	public void seekFile(int ms)
	{
		long slot = (long) (ms * slots / videoDuration);
		long pos = slot * RECORD_SIZE + END_HEADER;
		
		try
		{
			raf.seek(pos);
		}
		catch (IOException e)
		{
		}
	}
	
	/**
	 * 
	 * @return the millisecond of this frame (0 = start)
	 * @throws IOException
	 */
	private long readRecord() throws IOException
	{
		long t = raf.readLong();
		data.ax = raf.readFloat();
		data.ay = raf.readFloat();
		data.az = raf.readFloat();
		data.fx = raf.readFloat();
		data.fy = raf.readFloat();
		data.fz = raf.readFloat();
		data.mx = raf.readFloat();
		data.my = raf.readFloat();
		data.mz = raf.readFloat();
		data.speed = raf.readFloat();
		data.lat = raf.readFloat();
		data.lon = raf.readFloat();
		data.alt = raf.readFloat();
		return t;
	}
	
	int videopos = 0;
	Runnable progressBarUpdater = new Runnable()
	{
		public void run()
		{
			while (playing)
			{
				try
				{
					videopos = player.getCurrentPosition();
					seekFile(videopos);
					progressBar.setProgress(videopos);
				}
				catch (IllegalStateException e)
				{
				}
				try
				{
					Thread.sleep(1500);
				}
				catch (InterruptedException e)
				{
					break;
				}
			}
		};
	};
	
	Runnable dataPlayer = new Runnable()
	{
		long t;
		
		@Override
		public void run()
		{
			player.start();
			try
			{
				while (playing)
				{
					// Read variables
					t = System.currentTimeMillis();
					if (raf.getFilePointer() >= size)
					{
						break;
					}
					readRecord();
					overlay.postInvalidate();
					t = System.currentTimeMillis() - t;
					
					try
					{
						if (t < delay)
						{
							Thread.sleep(delay - t);
						}
					}
					catch (InterruptedException e)
					{
						playing = false;
					}
				}
			}
			catch (IOException e)
			{
			}
			finally
			{
				try { player.pause(); } catch (Exception e){}
				playing = false;
				overlay.postInvalidate();
			}
		}
	};
	
	class SymbolsOverlay extends View
	{
		private Paint playp;
		
		private Path playPath;
		
		TextPaint tp;
		
		public SymbolsOverlay(Context c)
		{
			super(c);
			
			playp = new Paint();
		    playp.setStyle(Style.FILL);
		    playp.setColor(Color.argb(255, 0, 255, 0));
		    playp.setStrokeWidth(0);
			
			playPath = new Path();
			
			tp = new TextPaint();
			tp.setTextSize(24);
			tp.setColor(Color.argb(255, 0, 255, 0));
		}
		
		@Override
		protected void onDraw(Canvas c)
		{
			if (playing)
			{
				int w = getWidth();
				int h = getHeight();
				
				playPath.moveTo(w - 30, h - 40);
				playPath.lineTo(w - 50, h - 30);
				playPath.lineTo(w - 50, h - 50);
				c.drawPath(playPath, playp);
			}			
		}
	}
	
	@Override
	protected void onStart()
	{
		super.onStart();
		
		playing = true;
		Thread t = new Thread(dataPlayer);
		t.start();
		t = new Thread(progressBarUpdater);
		t.start();
	};
	
	@Override
	protected void onDestroy()
	{
		player.release();
		
		try
		{
			if (raf != null)
			{
				raf.close();
			}
		}
		catch (IOException e)
		{
		}
		
		super.onDestroy();
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		if (wakeLock != null && !wakeLock.isHeld())
		{
			wakeLock.acquire();
		}
	}
	
	@Override
	protected void onPause()
	{
		if (wakeLock != null && wakeLock.isHeld())
		{
			wakeLock.release();
		}
		super.onPause();
	}
}
