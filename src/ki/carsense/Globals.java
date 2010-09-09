package ki.carsense;


/**
 * Globals are written only from Main
 */
public class Globals
{
	public static final long FILE_VERSION = 1;
	
	public static CalibrationData calibration = new CalibrationData();
	
	public static class CalibrationData
	{
		public float ax = 0, ay = 0, az = 0, fx = 0, fy = 0, fz = 0;
	}
}
