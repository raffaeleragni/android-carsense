package ki.carsense;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.text.TextPaint;
import android.view.View;

public class SensorOverlay extends View
{
	// # private static final double EARTH_GRAVITY = 9.797645;

	private static final int STROKE_WIDTH = 4;

	private static final int CIRCLES_RADIUS = 4;

	private static final int FONT_SIZE = 24;

	private static final int FONT_SIZE_BIGGER = 40;

	private static final int FONT_STROKE_WIDTH = 4;

	// # private static final int PITCH_TRESHOLD_WARNING = 30;

	private static final int LEVEL_TRESHOLD_WARNING = 30;

	private static final double LATERAL_GRAVITY_TRESHOLD_WARNING = 2.5;

	private static final double FRONTAL_GRAVITY_TRESHOLD_WARNING = 2.5;

	private static final double GRAVITY_PIXEL_FACTOR = 40;

	// This value is multipled with the screen width
	private static final double LEVEL_WIDTH = 0.45;

	// This value is the division of the screen width/height
	private static final double SCREEN_MARGIN = 10;

	// p is the normal paint
	private Paint p;

	// wp is the warning paint (red or orange)
	private Paint wp;

	// fp is the fill paint
	private Paint fp;

	// fp is the fill paint
	private Paint wfp;

	// Text Paint
	private TextPaint tp;

	// bp is the bigger text paint
	private TextPaint btp;

	// Warning Text Paint
	private TextPaint wtp;

	private Data data;

	public void setData(Data data)
	{
		this.data = data;
	}

	public Data getData()
	{
		return data;
	}

	public SensorOverlay(Context ctx, Data data)
	{
		super(ctx);

		this.data = data;

		p = new Paint();
		p.setStyle(Style.STROKE);
		p.setStrokeWidth(STROKE_WIDTH);
		p.setColor(Color.argb(255, 0, 255, 0));

		wp = new Paint();
		wp.setStyle(Style.STROKE);
		wp.setStrokeWidth(STROKE_WIDTH);
		wp.setColor(Color.argb(255, 255, 0, 0));

		fp = new Paint();
		fp.setStyle(Style.FILL);
		fp.setStrokeWidth(0);
		fp.setColor(Color.argb(255, 0, 255, 0));

		wfp = new Paint();
		wfp.setStyle(Style.FILL);
		wfp.setStrokeWidth(0);
		wfp.setColor(Color.argb(255, 255, 0, 0));

		tp = new TextPaint();
		tp.setStyle(Style.FILL);
		tp.setColor(Color.argb(255, 0, 255, 0));
		tp.setTextSize(FONT_SIZE);
		tp.setAntiAlias(true);
		tp.setStrokeWidth(FONT_STROKE_WIDTH);

		btp = new TextPaint();
		btp.setStyle(Style.FILL);
		btp.setColor(Color.argb(255, 0, 255, 0));
		btp.setTextSize(FONT_SIZE_BIGGER);
		btp.setAntiAlias(true);

		wtp = new TextPaint();
		wtp.setStyle(Style.FILL);
		wtp.setColor(Color.argb(255, 255, 0, 0));
		wtp.setTextSize(FONT_SIZE);
		wtp.setAntiAlias(true);
		wtp.setStrokeWidth(FONT_STROKE_WIDTH);
	}

	@Override
	@SuppressWarnings("unused")
	protected void onDraw(Canvas c)
	{
		int w = c.getWidth();
		int h = c.getHeight();

		// Heading
		float ax = data.ax;
		// Level
		float ay = data.ay;
		// Pitch
		float az = data.az;

		// Positive x strength = gravity -- used to erase earth gravity from z
		float fx = data.fx;
		// Positive y strength = right, Negative y = left
		float fy = data.fy;
		// Negative z strength = acceleration, Positive z = deceleration
		float fz = data.fz;

		// Magnetic x, y, z - Not used yet
		// # float mx = 0, my = 0, mz = 0;

		float speed = data.speed;

		//
		// SPEED
		//
		{
			String s = Integer.toString((int) (speed * 3600 / 1000));
			c.drawText(s, w - 10 - btp.measureText(s), h / 2 + h / 4, btp);
			c.drawText("km/h", w - 10 - tp.measureText("km/h"), h / 2 + h / 4 + 30, tp);
		}

		//
		// PITCH - doesn't to negative, sensor limitations
		// TODO pitch can't be calibrated correctly and doesn't go after 90° positive when standing vertical
//		{
//			// Value of g for the ui: what user will see as a number
//			int g_ui = (int) Math.abs(az - 90);
//			boolean g_warning = g_ui > PITCH_TRESHOLD_WARNING;// # || g_ui < -PITCH_TRESHOLD_WARNING;
//			// Structural line
//			c.drawLine(w - 50, 30, w - 50, (h - 60) / 2 + 30, g_warning ? wp : p);
//			// Indicator - horizontal line y
//			// 90° is considered leveled. 0=min, 180=max
//			// Transform g in line pixel units --- [[ g : 180 = x : (h - 60) ]]
//			int ly = 30 + (int) ((az > 180 ? 180 : (az < 0 ? 0 : az)) * (h - 60) / 180);
//			c.drawLine(w - 70, ly, w - 30, ly, g_warning ? wp : p);
//			c.drawCircle(w - 70 - CIRCLES_RADIUS, ly, CIRCLES_RADIUS, g_warning ? wp : p);
//			c.drawCircle(w - 30 + CIRCLES_RADIUS, ly, CIRCLES_RADIUS, g_warning ? wp : p);
//			// Write number
//			String s = Integer.toString(g_ui);
//			c.drawText(s, w - 85 - tp.measureText(s), ly + (int) (FONT_SIZE * 0.4), g_warning ? wtp : tp);
//		}

		//
		// LEVEL
		//
		{
			// l = Leveling
			double l = ay;
			l = l < -90 ? -(l + 180) : l;
			l = l > 90 ? -(l - 180) : l;
			boolean l_warning = l > LEVEL_TRESHOLD_WARNING || l < -LEVEL_TRESHOLD_WARNING;
			double rl = Math.toRadians(l - 180);
			// Inverse is for the other end of the line
			double inv_rl = Math.toRadians(l);
			int level_width_half = (int) (LEVEL_WIDTH * w / 2);
			// Determine x and y from degrees
			int lx = w / 2 + (int) (Math.cos(inv_rl) * level_width_half);
			int ly = h / 2 + (int) (Math.sin(inv_rl) * level_width_half);
			int lx2 = w / 2 + (int) (Math.cos(rl) * level_width_half);
			int ly2 = h / 2 + (int) (Math.sin(rl) * level_width_half);
			// Level line
			c.drawLine(lx, ly, lx2, ly2, l_warning ? wp : p);
			c.drawCircle(lx + CIRCLES_RADIUS, ly, CIRCLES_RADIUS, l_warning ? wp : p);
			c.drawCircle(lx2 - CIRCLES_RADIUS, ly2, CIRCLES_RADIUS, l_warning ? wp : p);
			// Write number
			// # c.drawText(Integer.toString((int) l), w/2, (int) (h/2 + FONT_SIZE*2.5), l_warning ? wtp : tp);
		}

		//
		// ORIENTATION
		//
		// TODO understand magnetic orientation and put that
//		{
//			String s = Integer.toString((int) ax);
//			c.drawText(s, (int) (w / 2 - tp.measureText(s) / 2), (int) (h / SCREEN_MARGIN), tp);
//		}

		//
		// Lateral Gravity
		//
		{
			double y = fy;
			int d = (int) (w / 2 + GRAVITY_PIXEL_FACTOR * fy);
			d = (int) (d > w - (w / SCREEN_MARGIN) ? w - (w / SCREEN_MARGIN)
					: (d < (w / SCREEN_MARGIN) ? (w / SCREEN_MARGIN) : d));
			c.drawLine(w / 2, h - 30, w / 2, h - 90,
					(y > LATERAL_GRAVITY_TRESHOLD_WARNING || y < -LATERAL_GRAVITY_TRESHOLD_WARNING) ? wp : p);
			c.drawRect(d, h - 45, w / 2, h - 75,
					(y > LATERAL_GRAVITY_TRESHOLD_WARNING || y < -LATERAL_GRAVITY_TRESHOLD_WARNING) ? wfp : fp);
		}

		//
		// Frontal Acceleration - gravity included
		//
		{
			double z = -fz;
			int d = (int) (h / 2 - GRAVITY_PIXEL_FACTOR * z);
			d = (int) (d > h - (h / SCREEN_MARGIN) ? h - (h / SCREEN_MARGIN) : (d < h / SCREEN_MARGIN ? h / SCREEN_MARGIN : d));
			c.drawLine(30, h / 2, 90, h / 2, (z > FRONTAL_GRAVITY_TRESHOLD_WARNING || z < -FRONTAL_GRAVITY_TRESHOLD_WARNING) ? wp
					: p);
			c.drawRect(45, d, 75, h / 2, (z > FRONTAL_GRAVITY_TRESHOLD_WARNING || z < -FRONTAL_GRAVITY_TRESHOLD_WARNING) ? wfp
					: fp);
		}
	}
}