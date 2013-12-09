package jp.saka.elugatest;

import android.content.Context;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Build;

import java.util.Random;
import java.lang.Thread;

public class ElugaTest2 extends Activity
{
	private static AudioTrackThread mAudioTrackThread = null;
	private static AudioRecordThread mAudioRecordThread = null;
	private static AudioManager mAudioManager = null;
	private static HandlerThread mHandlerThread = null;
	private static TestHandler mTestHandler = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mAudioManager = ((AudioManager) getSystemService(Context.AUDIO_SERVICE));

		// MODE_IN_COMMUNICATION
		// STREAM_VOICE_CALL
		//Log.d("sakalog", "set MODE_IN_COMMUNICATION");
		//mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
		Log.d("sakalog", "set STREAM_VOICE_CALL");
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

		if (mHandlerThread == null) {
			mHandlerThread = new HandlerThread("saka-thread");
			mHandlerThread.start();
			mTestHandler = new TestHandler(mHandlerThread.getLooper());
		}

		Button button;

		button = (Button)findViewById(R.id.StartStopAudioTrackRecordButton);
		button.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (!mTestHandler.isRunning()) {
					mTestHandler.start();
				} else {
					mTestHandler.stop();
				}
			}
		});
	}

	private static class TestHandler extends Handler {
		private boolean mRunning = false;

		private int mDelay = 2000; //milliseconds
		private int mNum = 0;

		public TestHandler(Looper looper) {
			super(looper);
		}

		public void start() {
			synchronized (this) {
				if (!mRunning) {
					mRunning = true;
					this.sendMessageDelayed(obtainMessage(0), 0);
				}
			}
		}

		public boolean isRunning() {
			synchronized (this) {
				return mRunning;
			}
		}

		public void stop() {
			synchronized (this) {
				if (mRunning) {
					mRunning = false;
				}
			}
		}

		@Override
		public void handleMessage(Message msg) {
			synchronized (this) {
				if (mRunning) {
					boolean delay = true;
					Log.d("sakalog", "i=" + mNum);

					if (mAudioTrackThread != null && mAudioRecordThread != null) {
						mAudioTrackThread.terminate();
						mAudioTrackThread = null;
						mAudioRecordThread.terminate();
						mAudioRecordThread = null;
						delay = false;
						Log.d("sakalog", "setMode(MODE_NORMAL)");
						mAudioManager.setMode(AudioManager.MODE_NORMAL);
						Log.d("sakalog", "setSpeakerphoneOn(false)");
						mAudioManager.setSpeakerphoneOn(false);
					} else {
						Log.d("sakalog", "setMode(MODE_IN_COMMUNICATION)");
						mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
						//Log.d("sakalog", "setSpeakerphoneOn(true)");
						//mAudioManager.setSpeakerphoneOn(true);
						mAudioRecordThread = new AudioRecordThread();
						mAudioRecordThread.start();
						mAudioTrackThread = new AudioTrackThread();
						mAudioTrackThread.start();
						mAudioRecordThread.waitStart();
						mAudioTrackThread.waitStart();

						/* ELUGA-Xで音量小さい問題に対応するため */
						if (!mAudioManager.isWiredHeadsetOn()) {
							Log.d("sakalog", "setSpeakerphoneOn(true)");
							mAudioManager.setSpeakerphoneOn(true);
						}
					}

					if (delay) {
						sendMessageDelayed(obtainMessage(0), mDelay);
					} else {
						sendMessageDelayed(obtainMessage(0), 0);
					}
					mNum++;

				} else {
					if (mAudioTrackThread != null && mAudioRecordThread != null) {
						mAudioRecordThread.terminate();
						mAudioRecordThread = null;
						mAudioTrackThread.terminate();
						mAudioTrackThread = null;
						Log.d("sakalog", "setMode(MODE_NORMAL)");
						mAudioManager.setMode(AudioManager.MODE_NORMAL);
						Log.d("sakalog", "setSpeakerphoneOn(false)");
						mAudioManager.setSpeakerphoneOn(false);
					}
				}
			}
		}
	}

	static class AudioRecordThread extends Thread {

		private AudioRecord mAudioRecord = null;
		private final int mSampleRate = 16000;
		private final int mChannel = AudioFormat.CHANNEL_IN_MONO;
		private final int mFormat = AudioFormat.ENCODING_PCM_16BIT;
		private boolean mRun = false;
		private boolean mStarted = false;

		public void terminate() {
			try {
				Log.d("sakalog", "stop thread...");
				mRun = false;
				join();
				Log.d("sakalog", "stop thread ok");
			} catch (Exception e) {
				Log.d("sakalog", "stop thread exception occur");
			}
		}

		public synchronized void waitStart() {
			try {
				while (!mStarted) {
					wait();
				}
			} catch (Exception e) {
			}
		}

		public synchronized void onStarted(boolean error) {
			try {
				mStarted = true;
				notifyAll();
			} catch (Exception e) {
			}
		}

		public void run() {

			mRun = true;

			int minBufSize = AudioRecord.getMinBufferSize(mSampleRate, mChannel, mFormat);

			/* ELUGA-XはMIC、ELUGA-PはVOICE_COMMUNICATION */
			int source = 0;
			if (Build.DEVICE.startsWith("P-02E")) {
				Log.d("sakalog", "source=MIC");
				source = MediaRecorder.AudioSource.MIC;
			} else {
				Log.d("sakalog", "source=VOICE_COMMUNICATION");
				source = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
			}

			try {
				Log.d("sakalog", "new AudioRecord");
				mAudioRecord = new AudioRecord(
						source,
						mSampleRate,
						mChannel,
						mFormat,
						minBufSize);
				Log.d("sakalog", "new AudioRecord ok");

				int state = mAudioRecord.getState();
				if (state == AudioRecord.STATE_INITIALIZED) {
					Log.d("sakalog", "AudioRecord Initialized");
				} else if (state == AudioRecord.STATE_UNINITIALIZED) {
					Log.d("sakalog", "AudioRecord Uninitialized");
				} else {
					Log.d("sakalog", "AudioRecord ?");
				}

			} catch (Exception e) {
				Log.d("sakalog", "new AudioRecord exception occur");
				onStarted(true);
				return;
			}

			loop();

			try {
				Log.d("sakalog", "AudioRecord#release");
				//Thread.sleep(100);
				mAudioRecord.release();
				//Thread.sleep(300);
				Log.d("sakalog", "AudioRecord#release ok");
			} catch (Exception e) {
				Log.d("sakalog", "AudioRecord#release exception occur");
			}

		}

		private void loop() {

			byte[] buf = new byte[640];

			try {
				Log.d("sakalog", "AudioRecord#startRecording");
				mAudioRecord.startRecording();
				Log.d("sakalog", "AudioRecord#startRecording ok");
				onStarted(false);
			} catch (Exception e) {
				Log.d("sakalog", "AudioRecord#startRecording exception occur");
				onStarted(true);
				return;
			}

			while (mRun) {
				try {
					int err = mAudioRecord.read(buf, 0, buf.length);
					if (err != buf.length) {
						Log.d("sakalog", "AudioRecord#read error (" + err + ")");
					}
				} catch (Exception e) {
					Log.d("sakalog", "AudioRecord#read exception occur");
					break;
				}
			}

			try {
				Log.d("sakalog", "AudioRecord#stop");
				mAudioRecord.stop();
				Log.d("sakalog", "AudioRecord#stop ok");
			} catch (Exception e) {
				Log.d("sakalog", "AudioRecord#stop exception occur");
			}

		}
	}

	static class AudioTrackThread extends Thread {

		private AudioTrack mAudioTrack = null;
		private final int mSampleRate = 16000;
		private final int mChannel = AudioFormat.CHANNEL_OUT_MONO;
		private final int mFormat = AudioFormat.ENCODING_PCM_16BIT;
		private boolean mRun = false;
		private boolean mStarted = false;

		public void terminate() {
			try {
				Log.d("sakalog", "stop thread...");
				mRun = false;
				join();
				Log.d("sakalog", "stop thread ok");
			} catch (Exception e) {
				Log.d("sakalog", "stop thread exception occur");
			}
		}

		public synchronized void waitStart() {
			try {
				while (!mStarted) {
					wait();
				}
			} catch (Exception e) {
			}
		}

		public synchronized void onStarted(boolean error) {
			try {
				mStarted = true;
				notifyAll();
			} catch (Exception e) {
			}
		}

		public void run() {

			mRun = true;

			int minBufSize = AudioTrack.getMinBufferSize(mSampleRate, mChannel, mFormat);

			try {
				Log.d("sakalog", "new AudioTrack");
				mAudioTrack = new AudioTrack(
						AudioManager.STREAM_VOICE_CALL,
						mSampleRate,
						mChannel,
						mFormat,
						minBufSize,
						AudioTrack.MODE_STREAM);
				Log.d("sakalog", "new AudioTrack ok");

				int state = mAudioTrack.getState();
				if (state == AudioTrack.STATE_INITIALIZED) {
					Log.d("sakalog", "AudioTrack Initialized");
				} else if (state == AudioTrack.STATE_UNINITIALIZED) {
					Log.d("sakalog", "AudioTrack Uninitialized");
				} else {
					Log.d("sakalog", "AudioTrack ?");
				}

			} catch (Exception e) {
				Log.d("sakalog", "new AudioTrack exception occur");
				onStarted(true);
				return;
			}

			try {
				Log.d("sakalog", "AudioTrack#play");
				mAudioTrack.play();
				Log.d("sakalog", "AudioTrack#play ok");
				onStarted(false);

				if (true) {
					Thread loop = new Thread(new Runnable() {
						@Override
						public void run() {
							loop();
						}
					});

					loop.start();
					loop.join();
				} else {

					loop();
				}

			} catch (Exception e) {
				Log.d("sakalog", "AudioTrack#play exception occur");
				onStarted(true);
			}

			try {
				Log.d("sakalog", "AudioTrack#release");
				//Thread.sleep(100);
				mAudioTrack.release();
				//Thread.sleep(300);
				Log.d("sakalog", "AudioTrack#release ok");
			} catch (Exception e) {
				Log.d("sakalog", "AudioTrack#release exception occur");
			}

		}

		private void loop() {

			byte[] buf = new byte[640];

			Random rand = new Random();

			for (int i=0; i<buf.length; i++) {
				buf[i] = (byte)rand.nextInt(0x08);
			}

			while (mRun) {
				try {
					int err = mAudioTrack.write(buf, 0, buf.length);
					if (err != buf.length) {
						Log.d("sakalog", "AudioTrack#write error (" + err + ")");
					}
				} catch (Exception e) {
					Log.d("sakalog", "AudioTrack#write exception occur");
					break;
				}
			}

			try {
				Log.d("sakalog", "AudioTrack#stop");
				mAudioTrack.stop();
				Log.d("sakalog", "AudioTrack#stop ok");
			} catch (Exception e) {
				Log.d("sakalog", "AudioTrack#stop exception occur");
			}

		}
	}

}
