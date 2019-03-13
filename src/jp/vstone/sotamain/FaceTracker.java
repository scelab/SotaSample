package jp.vstone.sotamain;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jp.pux.lib.PFIDLibrary.FacePosition;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.camera.FaceDetectResult;

public final class FaceTracker {
	private static boolean isTracking = false;
	private static String target = "";
	private static Byte[] servoIds = new Byte[] {CSotaMotion.SV_HEAD_Y, CSotaMotion.SV_HEAD_P,};
	private static int FaceTrack_YawMax   =  900;
	private static int FaceTrack_YawMin   = -900;
	private static int FaceTrack_PitchMax =  300;
	private static int FaceTrack_PitchMin = -300;
	private static double FaceTrack_P_GAIN = 0.25D;
	private static double FaceTrack_D_GAIN = 0.55D;
	private static double FaceTrack_Y_GAIN = 1.0D;
	private static int yaw   =  0;
	private static int pitch =  0; //-150;
	private static int width  = 640;
	private static int height = 480;
	private static double old_yaw   = 0.0D;
	private static double old_pitch = 0.0D;
	private static double maxspeed_yaw   = 100.0D;
	private static double maxspeed_pitch = 100.0D;
	private static List<Double> lastTargetPosition;
	private static int lastYawToHuman;
	private static int lastPitchToHuman;
	private static Thread trackingThread;

	public static void start(String target) {
		if (isTracking) {
			FaceTracker.target = target;
			return;
		}
		isTracking = true;
		FaceTracker.target = target;
		Runnable r = () -> {
			while (isTracking) {
				tracking(FaceTracker.target);
			}
		};
		trackingThread = new Thread(r);
		trackingThread.start();
	}

	public static void stop() {
		isTracking = false;
		try { if(trackingThread != null) trackingThread.join(); }
		catch (InterruptedException e) { e.printStackTrace(); }
	}

	private static boolean near(List<Double> p1, List<Double> p2) {
		double dx = p2.get(0) - p1.get(0);
		double dy = p2.get(1) - p1.get(1);
		double dz = p2.get(2) - p1.get(2);
		double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
		return dist < 1000.0;
	}

	public static void tracking(String target) {
		Map<String, List<Double>> positions;
		synchronized (Globals.lock) {
			positions = Globals.positions.entrySet().stream().collect(
					Collectors.toMap(e -> e.getKey(), e -> new ArrayList<Double>(e.getValue())));
		}

		if (target.startsWith("R")) {
			if (!positions.containsKey(target)) return;
			if (!Objects.isNull(lastTargetPosition) && lastTargetPosition.equals(positions.get(target))) return;
			moveDirectly(positions, target);
			lastTargetPosition = positions.get(target);
			return;
		}
		if (Globals.RobotVars.isUsing3DSensor) {
			if (target.startsWith("H")) {
				List<String> targets = new ArrayList<>(positions.keySet());
				List<String> humanTargets = targets.stream().filter(t -> t.startsWith("H")).collect(Collectors.toList());
				if (!humanTargets.isEmpty()) {
					for (String humanTarget : humanTargets) {
						moveDirectly(positions, humanTarget);
						lastTargetPosition = positions.get(humanTarget);
						return;
					}
				}
			}
		}
		FaceDetectResult result = Globals.RobotVars.robocam.getDetectResult();
		if (result.isDetect()) {
			moveByFaceResult(result);
			lastYawToHuman   = yaw;
			lastPitchToHuman = pitch;
			return;
		}
		CRobotUtil.Err("FaceTracker", "Any sensors did not detect humans.");
		controlDirectly(lastYawToHuman, lastPitchToHuman);
		return;
	}

	private static void moveDirectly(Map<String, List<Double>> positions, String target) {
		double [] diffs = calcDiffs(positions, target);
		double d_yaw   = diffs[0];
		double d_pitch = diffs[1];
		controlDirectly((int)d_yaw, (int)d_pitch);
	}

	private static void moveByFaceResult(FaceDetectResult result) {
		double [] diffs = calcDiffs(result);
		double d_yaw   = diffs[0];
		double d_pitch = diffs[1];
		controlByFaceResult(d_yaw, d_pitch);
	}

	private static double[] calcDiffs(Map<String, List<Double>> positions, String target) {
		//System.out.println(">>> positions: " + positions);
		//System.out.println("    target: " + target);
		double vec_x = positions.get(target).get(0) - positions.get(Globals.RobotVars.robotId).get(0);
		double vec_y = positions.get(target).get(1) - positions.get(Globals.RobotVars.robotId).get(1);
		double vec_z = positions.get(target).get(2) - positions.get(Globals.RobotVars.robotId).get(2);
		double d_yaw   = (short)((Math.toDegrees(Math.atan2(vec_y, vec_x)) * 10) - positions.get(Globals.RobotVars.robotId).get(5));
		double d_pitch = (short)((Math.toDegrees(Math.atan2(vec_z, Math.sqrt(vec_x*vec_x + vec_y*vec_y))) * 10) - positions.get(Globals.RobotVars.robotId).get(4));
		d_pitch *= (-1);
		//System.out.println(">>> x=" + vec_x + " y=" + vec_y + " z=" + vec_z + " yaw=" + d_yaw + " pitch=" + d_pitch);
		return new double[] {d_yaw, d_pitch};
	}

	private static double[] calcDiffs(FaceDetectResult result) {
	    FacePosition pos = result.getFacePosition(0);
	    Point center = pos.getCenterPoint();
		double d_x = center.getX() - (double)(width  / 2);
	    double d_y = center.getY() - (double)(height / 2);
		double d_yaw   = d_x * (480.0 / 640.0); //48degree : 640pixel
		double d_pitch = d_y * (360.0 / 480.0); //36degree : 480pixel
		return new double[] {d_yaw, d_pitch};
	}

	private static void controlByFaceResult(double d_yaw, double d_pitch) {
		// PDI control
		// out: value which is added to the current value.
		// d:   difference from the destination value.
		// d-old: variance between the current difference and the last difference.
		//        if the value is large, it means that the destination became far. So the term become large.
	    double out_yaw   = FaceTrack_P_GAIN * d_yaw   + FaceTrack_D_GAIN * (d_yaw   - old_yaw);
	    double out_pitch = FaceTrack_P_GAIN * d_pitch + FaceTrack_D_GAIN * (d_pitch - old_pitch);
	    if      (out_yaw   >  maxspeed_yaw)   { out_yaw   =  maxspeed_yaw; }
	    else if (out_yaw   < -maxspeed_yaw)   { out_yaw   = -maxspeed_yaw; }
	    if      (out_pitch >  maxspeed_pitch) { out_pitch =  maxspeed_pitch; }
	    else if (out_pitch < -maxspeed_pitch) { out_pitch = -maxspeed_pitch; }
	    yaw   = (short)((int)(((double)yaw   - out_yaw) * FaceTrack_Y_GAIN));
	    pitch = (short)((int)( (double)pitch + out_pitch));
	    old_yaw   = d_yaw;
	    old_pitch = d_pitch;
	    if      (yaw > FaceTrack_YawMax) { yaw = FaceTrack_YawMax; }
	    else if (yaw < FaceTrack_YawMin) { yaw = FaceTrack_YawMin; }
	    if      (pitch > FaceTrack_PitchMax) { pitch = FaceTrack_PitchMax; }
	    else if (pitch < FaceTrack_PitchMin) { pitch = FaceTrack_PitchMin; }
	    if (Globals.RobotVars.motion.getReadPose() != null &&
		    Globals.RobotVars.motion.getReadPose().getPose() != null &&
		    Globals.RobotVars.motion.getReadPose().getPose().containsKey(Byte.valueOf(CSotaMotion.SV_HEAD_Y))) {
	    	short current_yaw = ((Short)Globals.RobotVars.motion.getReadPose().getPose().get(Byte.valueOf(CSotaMotion.SV_HEAD_Y))).shortValue();
		    int sub = Math.abs(yaw - current_yaw);
		    int maxSub = FaceTrack_YawMax / 2;
		    if(maxSub < sub) {
		        if (0 <= yaw) { yaw = maxSub; }
		        else          { yaw = maxSub * -1; }
		    }
		}
	    //CRobotUtil.Log("FaceTracker", String.format(">>> target=%s, yaw=%d, pitch=%d", target, yaw, pitch));
	    controlDirectly(yaw, pitch);
	}

	private static void controlDirectly(int yaw, int pitch) {
		short msec = (short) calcMsec(yaw, pitch);
		play(servoIds, new Short[] {(short)yaw, (short)pitch}, msec);
	}

	private static int calcMsec(int yaw, int pitch) {
		Short[] current_degs = Globals.RobotVars.motion.getReadpos();
		int msec = (int)(Math.max(Math.abs(yaw - current_degs[5]), Math.abs(pitch - current_degs[6])) * 10.0 / 9.0); // 1sec per 90degree
		return msec > 100 ? msec : 100;
	}

	private static void play(Byte[] servoIds, Short[] values, short msec) {
		CRobotPose pose = new CRobotPose();
		pose.SetPose(servoIds, values);
		Globals.RobotVars.motion.LockServoHandle(servoIds);
		Globals.RobotVars.motion.play(pose, msec);
		CRobotUtil.wait(msec);
		Globals.RobotVars.motion.UnLockServoHandle(servoIds);
	}
}
