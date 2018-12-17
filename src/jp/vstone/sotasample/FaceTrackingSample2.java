package jp.vstone.sotasample;

import java.awt.Color;

import jp.vstone.RobotLib.CRobotMem;
import jp.vstone.RobotLib.CRobotPose;
import jp.vstone.RobotLib.CRobotUtil;
import jp.vstone.RobotLib.CSotaMotion;
import jp.vstone.camera.CRoboCamera;
import jp.vstone.camera.FaceDetectResult;

/**
 * フェイストラッキングをして、笑顔であれば写真を撮るサンプル
 * @author Vstone
 *
 */
public class FaceTrackingSample2 {

	static final String TAG = "FaceTrackingSample";
	static final int SMILE_POINT = 45;

	public static void main(String args[]){
		CRobotUtil.Log(TAG, "Start " + TAG);

		CRobotPose pose;
		//VSMDと通信ソケット・メモリアクセス用クラス
		CRobotMem mem = new CRobotMem();
		//Sota用モーション制御クラス
		CSotaMotion motion = new CSotaMotion(mem);

		CRoboCamera cam = new CRoboCamera("/dev/video0", motion);

		if(mem.Connect()){
			//Sota仕様にVSMDを初期化
			motion.InitRobot_Sota();
			CRobotUtil.Log(TAG, "Rev. " + mem.FirmwareRev.get());
			//サーボモータを現在位置でトルクOnにする
			CRobotUtil.Log(TAG, "Servo On");
			motion.ServoOn();
			//すべての軸を動作
			pose = new CRobotPose();
			pose.SetPose(new Byte[] {1   ,2   ,3   ,4   ,5   ,6   ,7   ,8}	//id
					,  new Short[]{0   ,-900,0   ,900 ,0   ,0   ,0   ,0}				//target pos
					);
			//LEDを点灯（左目：赤、右目：赤、口：Max、電源ボタン：赤）
			pose.setLED_Sota(Color.BLUE, Color.BLUE, 255, Color.GREEN);
			motion.play(pose, 500);
			CRobotUtil.wait(500);
			//笑顔推定有効
			cam.setEnableSmileDetect(true);
			//顔検索有効
			cam.setEnableFaceSearch(true);
			//フェイストラッキング開始
			cam.StartFaceTraking();
			//cam.StartFaceDetect();
			int photcnt = 0;
			while(true){
				FaceDetectResult result = cam.getDetectResult();
				if(result.isDetect()){
					CRobotUtil.Log(TAG, "[Detect] x = " + result.getCenterPoint(0).x + ", y = " + result.getCenterPoint(0).y);
					pose.setLED_Sota(Color.GREEN, Color.GREEN, 255, Color.GREEN);
					motion.play(pose, 500);
				}
				else{
					CRobotUtil.Log(TAG, "[Not Detect]");
					pose.setLED_Sota(Color.BLUE, Color.BLUE, 255, Color.GREEN);
					motion.play(pose, 500);
				}
				CRobotUtil.wait(500);
			}
		}
		motion.ServoOff();
	}
}
