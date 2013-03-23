package org.libsdl.app;

// http://www.philhassey.com/blog/category/android/page/2/

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.*;

import android.app.*;
import android.content.*;
import android.view.*;
import android.os.*;
import android.util.Log;
import android.graphics.*;
import android.text.method.*;
import android.text.*;
import android.media.*;
import android.hardware.*;
import android.content.*;
import android.util.DisplayMetrics;
import android.widget.TextView;
import android.provider.Settings;
import android.net.wifi.WifiInfo;

import java.lang.*;

import java.io.*;


/**
    SDL Activity
*/
public class SDLActivity extends Activity {

    // Main components
    private static SDLActivity mSingleton;
    private static SDLSurface mSurface;

    // Audio
    private static Thread mAudioThread;
    private static AudioTrack mAudioTrack;

    // Load the .so
    static {
        System.loadLibrary("SDL");
        System.loadLibrary("SDL_image");
        System.loadLibrary("mikmod");
        System.loadLibrary("SDL_mixer");
        System.loadLibrary("SDL_ttf");
        System.loadLibrary("main");
    }

    // Setup
    protected void onCreate(Bundle savedInstanceState) {
         Log.i("glbasic", "onCreate");
       //Log.v("SDL", "onCreate()");
        super.onCreate(savedInstanceState);
        
        // So we can call stuff from static callbacks
        mSingleton = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN); 
        
        // Get Android ID
        String AndroidId = Settings.Secure.getString(getContentResolver(),
                                                     Settings.Secure.ANDROID_ID);
        
		// Copy assets to /Files, so we can access them with fopen()
        Log.i("glbasic", "Updating media files"); 
		copyAssets();
        Log.i("glbasic", "done with updating"); 

        // Get BatteryLevel
        batteryLevel();
        
        
        //Log.w("JAVA-BatLevel-- %d",Level);
        
        java.io.File dataDir = getFilesDir();
		java.io.File chacheDir = getCacheDir();
        Log.i("glbasic", String.format("external files dir is %s", dataDir.toString()));

		String ext_storage = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).toString();
        Log.i("glbasic", String.format("external storage %s", ext_storage.toString()));
		int keyboardPresent = (getResources().getConfiguration().keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS) ? 1:0;
        setFilesPath(	dataDir.toString(),
						AndroidId.toString(),
						chacheDir.toString(),
						java.util.Locale.getDefault().getDisplayCountry(),
						keyboardPresent,
						ext_storage
						); 
        // Set up the surface
        mSurface = new SDLSurface(getApplication(), this);
        setContentView(mSurface);
        SurfaceHolder holder = mSurface.getHolder();
        holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
    }


    private void batteryLevel() {
        BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                int rawlevel = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                int level = -1;
                if (rawlevel >= 0 && scale > 0) {
                    level = (rawlevel * 100) / scale;
                }
                //hier native funktion rein
                setBatteryLevel(level);
            }
        };
        
        IntentFilter batteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryLevelReceiver, batteryLevelFilter);
    }

    // Events
    protected void onPause() {
        Log.i("glbasic", "onPause()");
        Log.i("glbasic", "glbasicOnPause(1)");
		glbasicOnPause(1); // 1=pause
        Log.i("glbasic", "super.onPause");
        super.onPause();
        // Log.i("SDLglb", "exit");
		// System.exit(0); // stupid hack - would not resume properly
        // Log.i("SDLglb", "exited");
   }

    protected void onResume() {
        Log.i("glbasic", "onResume()");
        super.onResume();
		glbasicOnPause(0); // 0=resume
    }
 
    protected void onStop() {
        Log.i("glbasic", "onStop()");
        super.onStop();
		glbasicOnPause(-1); // -1=shutdown
        Log.i("glbasic", "glbasicOnPause(-1);");
    }

    // Messages from the SDLMain thread
    static int COMMAND_CHANGE_TITLE = 1;

    // Handler for the messages
    Handler commandHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.arg1 == COMMAND_CHANGE_TITLE) {
                setTitle((String)msg.obj);
            }
        }
    };

    // Send a message from the SDLMain thread
    void sendCommand(int command, Object data) {
        Message msg = commandHandler.obtainMessage();
        msg.arg1 = command;
        msg.obj = data;
        commandHandler.sendMessage(msg);
    }

    // C functions we call
    public static native void nativeInit();
    public static native void nativeQuit();
    public static native void onNativeResize(int x, int y, int format);
    public static native void onNativeKeyDown(int keycode);
    public static native void onNativeKeyUp(int keycode);
    public static native void onNativeTouch(int action, float x, 
                                            float y, float p);
    public static native void onNativeAccel(float x, float y, float z);
    public static native void nativeRunAudioThread();
    public static native void setFilesPath(String path, String devid, String cache, String lang, int bKeyboard, String extStoragePubDir);
	public static native void glbasicOnPause(int istatus);
    public static native void setBatteryLevel(int level);

	public static native void glbmultimouseevent(int id, int b1, int mx, int my);

    // Java functions called from C
    public static int glb_open_url(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(android.net.Uri.parse(url));
        try{
			mSingleton.startActivity(intent);
		}
		catch(ActivityNotFoundException e)
		{
			return 0;
		}
		return 1;
    }

    public static boolean createGLContext(int majorVersion, int minorVersion) {
        return mSurface.initEGL(majorVersion, minorVersion);
    }

    public static void flipBuffers() {
        mSurface.flipEGL();
    }

    public static void setActivityTitle(String title) {
        // Called from SDLMain() thread and can't directly affect the view
        mSingleton.sendCommand(COMMAND_CHANGE_TITLE, title);
    }


	public void copyAssets() {
		File lastMod = new File(getFilesDir(), "lastModified");
		if (lastMod.exists()) {
			Log.i("glbasic", "assets found, copying...");
			File apkFile = new File(this.getApplicationInfo().sourceDir);
			if (lastMod.lastModified() >= apkFile.lastModified()) {
				Log.i("glbasic", "assets still up to date, proceed to main...");
				return; // Dateien noch aktuell, Abbruch
			} else {
				Log.i("glbasic", "updating assets...");
				lastMod.setLastModified(System.currentTimeMillis());
				recursiveDelete(new File(getFilesDir(), "Media"));
				recursiveCopy("Media");
			}
		} else {
			Log.i("glbasic", "no assets found, copying...");
			try {
				lastMod.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
			recursiveCopy("Media");
		}
	}

	private void recursiveDelete(File file) {
		Log.v("glbasic", "attempting to delete: ".concat(file.getAbsolutePath()));
		if (file.isDirectory()) {
			Log.v("glbasic", file.getAbsolutePath().concat(" is a directory, deleting content..."));
			File[] content = file.listFiles();
			for (File item : content) {
				recursiveDelete(item);
			}
		}
		file.delete();
		Log.v("glbasic", file.getAbsolutePath().concat(" was deleted sucessfully"));
	}

	private void recursiveCopy(String path) {
		Log.v("glbasic", "attempting to copy asset: ".concat(path));
		try {
			String[] list = getAssets().list(path);
			if (0 != list.length) {					// path ist ein Verzeichnis
				Log.i("glbasic", path.concat(" is a directory"));
				File newDir = new File(getFilesDir().getAbsolutePath().concat(File.separator).concat(path));
				newDir.mkdir();
				Log.v("glbasic", "created directory: ".concat(newDir.getAbsolutePath()).concat(", copying content..."));
				for (String item : list) {
					if ("" != path) { 
						recursiveCopy(path.concat(File.separator).concat(item));
					} else {
						recursiveCopy(item);
					}
				}
			} else {							// path ist kein Verzeichnis (oder leer)
				Log.v("glbasic", path.concat(" is a file, reading..."));
				InputStream is = getAssets().open(path);
				int size = is.available();
				byte[] buffer = new byte[size];
				is.read(buffer);
				is.close();
				Log.v("glbasic", path.concat(" was read sucessfully"));
				File newFile = new File(getFilesDir().getAbsolutePath().concat(File.separator).concat(path));
				if (newFile.exists()) {
					newFile.delete();
				}
				newFile.createNewFile();
				Log.v("glbasic", "created file: ".concat(newFile.getAbsolutePath()));
				FileOutputStream out = new FileOutputStream(newFile);
				out.write(buffer);
				Log.v("glbasic", newFile.getAbsolutePath().concat(" was written sucessfully"));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

    // Audio
    private static Object buf;
    
    public static Object audioInit(int sampleRate, boolean is16Bit, boolean isStereo, int desiredFrames) {
        int channelConfig = isStereo ? AudioFormat.CHANNEL_CONFIGURATION_STEREO : AudioFormat.CHANNEL_CONFIGURATION_MONO;
        int audioFormat = is16Bit ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;
        int frameSize = (isStereo ? 2 : 1) * (is16Bit ? 2 : 1);
        
        Log.v("SDL", "SDL audio: wanted " + (isStereo ? "stereo" : "mono") + " " + (is16Bit ? "16-bit" : "8-bit") + " " + ((float)sampleRate / 1000f) + "kHz, " + desiredFrames + " frames buffer");
        
        // Let the user pick a larger buffer if they really want -- but ye
        // gods they probably shouldn't, the minimums are horrifyingly high
        // latency already
        desiredFrames = Math.max(desiredFrames, (AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat) + frameSize - 1) / frameSize);
        
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                channelConfig, audioFormat, desiredFrames * frameSize, AudioTrack.MODE_STREAM);
        
        audioStartThread();
        
        Log.v("SDL", "SDL audio: got " + ((mAudioTrack.getChannelCount() >= 2) ? "stereo" : "mono") + " " + ((mAudioTrack.getAudioFormat() == AudioFormat.ENCODING_PCM_16BIT) ? "16-bit" : "8-bit") + " " + ((float)mAudioTrack.getSampleRate() / 1000f) + "kHz, " + desiredFrames + " frames buffer");
        
        if (is16Bit) {
            buf = new short[desiredFrames * (isStereo ? 2 : 1)];
        } else {
            buf = new byte[desiredFrames * (isStereo ? 2 : 1)]; 
        }
        return buf;
    }
    
    public static void audioStartThread() {
        mAudioThread = new Thread(new Runnable() {
            public void run() {
                mAudioTrack.play();
                nativeRunAudioThread();
            }
        });
        
        // I'd take REALTIME if I could get it!
        mAudioThread.setPriority(Thread.MAX_PRIORITY);
        mAudioThread.start();
    }
    
    public static void audioWriteShortBuffer(short[] buffer) {
        for (int i = 0; i < buffer.length; ) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {
                    // Nom nom
                }
            } else {
                Log.w("SDL", "SDL audio: error return from write(short)");
                return;
            }
        }
    }
    
    public static void audioWriteByteBuffer(byte[] buffer) {
        for (int i = 0; i < buffer.length; ) {
            int result = mAudioTrack.write(buffer, i, buffer.length - i);
            if (result > 0) {
                i += result;
            } else if (result == 0) {
                try {
                    Thread.sleep(1);
                } catch(InterruptedException e) {
                    // Nom nom
                }
            } else {
                Log.w("SDL", "SDL audio: error return from write(short)");
                return;
            }
        }
    }

    public static void audioQuit() {
        if (mAudioThread != null) {
            try {
                mAudioThread.join();
            } catch(Exception e) {
                Log.v("SDL", "Problem stopping audio thread: " + e);
            }
            mAudioThread = null;

            //Log.v("SDL", "Finished waiting for audio thread");
        }

        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack = null;
        }
    }
}

/*
    Simple nativeInit() runnable
*/
class SDLMain implements Runnable {
    public void run() {
        // Runs SDL_main()
        SDLActivity.nativeInit();

        //Log.v("SDL", "SDL thread terminated");
    }
}


/*
    SDLSurface. This is what we draw on, so we need to know when it's created
    in order to do anything useful. 

    Because of this, that's where we set up the SDL thread
*/
class SDLSurface extends SurfaceView implements SurfaceHolder.Callback, 
    View.OnKeyListener, View.OnTouchListener, SensorEventListener  {

    // This is what SDL runs in. It invokes SDL_main(), eventually
    private Thread mSDLThread;    
	private static SDLActivity mActivity;
    
    // EGL private objects
    private EGLContext  mEGLContext;
    private EGLSurface  mEGLSurface;
    private EGLDisplay  mEGLDisplay;

    // Sensors
    private static SensorManager mSensorManager;

    // Startup    
    public SDLSurface(Context context, SDLActivity act) {
		super(context);

		this.getHolder().setType(SurfaceHolder.SURFACE_TYPE_HARDWARE);


        mActivity=act;
        getHolder().addCallback(this); 
    
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setOnKeyListener(this); 
        setOnTouchListener(this);   

        mSensorManager = (SensorManager)context.getSystemService("sensor");  
    }

    // Called when we have a valid drawing surface
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v("SDL", "surfaceCreated()");

        enableSensor(Sensor.TYPE_ACCELEROMETER, true);
    }

    // Called when we lose the surface
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v("SDL", "surfaceDestroyed()");

        // Send a quit message to the application
        SDLActivity.nativeQuit();

        // Now wait for the SDL thread to quit
        if (mSDLThread != null) {
            try {
                mSDLThread.join();
            } catch(Exception e) {
                Log.w("SDL", "Problem stopping thread: " + e);
            }
            mSDLThread = null;

            //Log.v("SDL", "Finished waiting for SDL thread");
        }

        enableSensor(Sensor.TYPE_ACCELEROMETER, false);
    }

    // Called when the surface is resized
    public void surfaceChanged(SurfaceHolder holder,
                               int format, int width, int height) {
        //Log.v("SDL", "surfaceChanged()");

        int sdlFormat = 0x85151002; // SDL_PIXELFORMAT_RGB565 by default
        switch (format) {
        case PixelFormat.A_8:
            Log.i("SDL", "pixel format A_8");
            break;
        case PixelFormat.LA_88:
            Log.i("SDL", "pixel format LA_88");
            break;
        case PixelFormat.L_8:
            Log.i("SDL", "pixel format L_8");
            break;
        case PixelFormat.RGBA_4444:
            Log.i("SDL", "pixel format RGBA_4444");
            sdlFormat = 0x85421002; // SDL_PIXELFORMAT_RGBA4444
            break;
        case PixelFormat.RGBA_5551:
            Log.i("SDL", "pixel format RGBA_5551");
            sdlFormat = 0x85441002; // SDL_PIXELFORMAT_RGBA5551
            break;
        case PixelFormat.RGBA_8888:
            Log.i("SDL", "pixel format RGBA_8888");
            sdlFormat = 0x86462004; // SDL_PIXELFORMAT_RGBA8888
            break;
        case PixelFormat.RGBX_8888:
            Log.i("SDL", "pixel format RGBX_8888");
            sdlFormat = 0x86262004; // SDL_PIXELFORMAT_RGBX8888
            break;
        case PixelFormat.RGB_332:
            Log.i("SDL", "pixel format RGB_332");
            sdlFormat = 0x84110801; // SDL_PIXELFORMAT_RGB332
            break;
        case PixelFormat.RGB_565:
            Log.i("SDL", "pixel format RGB_565");
            sdlFormat = 0x85151002; // SDL_PIXELFORMAT_RGB565
            break;
        case PixelFormat.RGB_888:
            Log.i("SDL", "pixel format RGB_888");
            // Not sure this is right, maybe SDL_PIXELFORMAT_RGB24 instead?
            sdlFormat = 0x86161804; // SDL_PIXELFORMAT_RGB888
            break;
        default:
            Log.i("SDL", "pixel format unknown " + format);
            break;
        }
        SDLActivity.onNativeResize(width, height, sdlFormat);

        // Now start up the C app thread
        if (mSDLThread == null) {
            mSDLThread = new Thread(new SDLMain(), "SDLThread"); 
            mSDLThread.start();       
        }
    }

    // unused
    public void onDraw(Canvas canvas) {}





	public EGLConfig gf_sdl_chooseConfig(EGL10 egl, EGLDisplay display)
	{
		//Querying number of configurations
		int[] num_conf = new int[1];
		egl.eglGetConfigs(display, null, 0, num_conf);  //if configuration array is null it still returns the number of configurations
		int configurations = num_conf[0];

		//Querying actual configurations
		EGLConfig[] conf = new EGLConfig[configurations];
		egl.eglGetConfigs(display, conf, configurations, num_conf);

		EGLConfig result = null;

		int[] value = new int[1];
		for(int i = 0; i < configurations; i++)
		{
			result = better_egl_config(result, conf[i], egl, display);
		}

		egl.eglGetConfigAttrib(display, result, EGL10.EGL_RED_SIZE, value);
		int R = value[0];
		egl.eglGetConfigAttrib(display, result, EGL10.EGL_GREEN_SIZE, value);
		int G = value[0];
		egl.eglGetConfigAttrib(display, result, EGL10.EGL_BLUE_SIZE, value);
		int B = value[0];
		egl.eglGetConfigAttrib(display, result, EGL10.EGL_ALPHA_SIZE, value);
		int A = value[0];
		egl.eglGetConfigAttrib(display, result, EGL10.EGL_DEPTH_SIZE, value);
		int Z = value[0];
		Log.i("SDL", "Chosen EGLConfig: RGBA Z: "+ R+" "+G+" "+B+" "+A+" Z="+Z);

		return result;
	}

	/**
	* Returns the best of the two EGLConfig passed according to depth and colours
	* @param a The first candidate
	* @param b The second candidate
	* @return The chosen candidate
	*/
	private EGLConfig better_egl_config(EGLConfig a, EGLConfig b, EGL10 egl, EGLDisplay display)
	{
		if(a == null) return b;

		EGLConfig result = null;

		int[] value = new int[1];

		// MUST have OpenGL|ES 1.x
		egl.eglGetConfigAttrib(display, a, EGL10.EGL_RENDERABLE_TYPE, value);
		int EGL_OPENGL_ES_BIT = 1;
		int EGL_OPENGL_ES2_BIT = 4;
		int gl_type = value[0];
		if(gl_type != EGL_OPENGL_ES_BIT)
			return b;
		

		egl.eglGetConfigAttrib(display, a, EGL10.EGL_DEPTH_SIZE, value);
		int depthA = value[0];

		egl.eglGetConfigAttrib(display, b, EGL10.EGL_DEPTH_SIZE, value);
		int depthB = value[0];

		egl.eglGetConfigAttrib(display, a, EGL10.EGL_GREEN_SIZE, value);
		int greenA = value[0];

		egl.eglGetConfigAttrib(display, b, EGL10.EGL_GREEN_SIZE, value);
		int greenB = value[0];


		egl.eglGetConfigAttrib(display, a, EGL10.EGL_ALPHA_SIZE, value);
		int alphaA = value[0];

		egl.eglGetConfigAttrib(display, b, EGL10.EGL_ALPHA_SIZE, value);
		int alphaB = value[0];

		int totA = depthA + greenA*256 + alphaA;
		int totB = depthB + greenB*256 + alphaB;
		if(totA > totB)
			return a;
		if(totA < totB)
			return b;

		// All the same? We urgently need a Z buffer, instead of alpha
		totA = depthA;
		totB = depthB;
		if(totA > totB)
			return a;
		if(totA < totB)
			return b;
		return b; // no care
	}


    // EGL functions
    public boolean initEGL(int majorVersion, int minorVersion) {
        Log.i("SDL", "Starting up OpenGL ES " + majorVersion + "." + minorVersion);

        try {
            EGL10 egl = (EGL10)EGLContext.getEGL();
            EGLDisplay dpy = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            int[] version = new int[2];
            egl.eglInitialize(dpy, version);

            EGLConfig config;
			if(true)
			{
				int EGL_OPENGL_ES_BIT = 1;
				int EGL_OPENGL_ES2_BIT = 4;
				int renderableType = 0;
				if (majorVersion == 2) {
					renderableType = EGL_OPENGL_ES2_BIT;
				} else if (majorVersion == 1) {
					renderableType = EGL_OPENGL_ES_BIT;
				}
				int[] configSpec = {
				/*
					EGL10.EGL_RED_SIZE, 8,
					EGL10.EGL_GREEN_SIZE, 8,
					EGL10.EGL_BLUE_SIZE, 8,
					EGL10.EGL_ALPHA_SIZE, 8,
				*/
					EGL10.EGL_DEPTH_SIZE,   16,
					EGL10.EGL_RENDERABLE_TYPE, renderableType,
					EGL10.EGL_NONE
				};



				EGLConfig[] configs = new EGLConfig[1];
				int[] num_config = new int[1];
				if (!egl.eglChooseConfig(dpy, configSpec, configs, 1, num_config) || num_config[0] == 0) {
					Log.e("SDL", "No EGL config available");
					return false;
				}
				config = configs[0];
			}
			else
			{
				// picks super config -> but might not be HW accelerated. :(
				config = gf_sdl_chooseConfig(egl, dpy);
			}



            EGLContext ctx = egl.eglCreateContext(dpy, config, EGL10.EGL_NO_CONTEXT, null);
            if (ctx == EGL10.EGL_NO_CONTEXT) {
                Log.e("SDL", "Couldn't create context");
                return false;
            }

            EGLSurface surface = egl.eglCreateWindowSurface(dpy, config, this, null);
            if (surface == EGL10.EGL_NO_SURFACE) {
                Log.e("SDL", "Couldn't create surface");
                return false;
            }

            if (!egl.eglMakeCurrent(dpy, surface, surface, ctx)) {
                Log.e("SDL", "Couldn't make context current");
                return false;
            }

            mEGLContext = ctx;
            mEGLDisplay = dpy;
            mEGLSurface = surface;

//			Log.i("SDL", "HW-accelersted :"+ isHardwareAccelerated());

        } catch(Exception e) {
			Log.e("SDL","Exception caused by EGL implementation");
            Log.e("SDL", e + "");
            for (StackTraceElement s : e.getStackTrace()) {
                Log.e("SDL", s.toString());
            }
        }

        return true;
    }

    // EGL buffer flip
    public void flipEGL() {
        try {
            EGL10 egl = (EGL10)EGLContext.getEGL();
            egl.eglWaitNative(EGL10.EGL_NATIVE_RENDERABLE, null);

            // drawing here
            egl.eglWaitGL();
            egl.eglSwapBuffers(mEGLDisplay, mEGLSurface);
        } catch(Exception e) {
            Log.v("SDL", "flipEGL(): " + e);
            for (StackTraceElement s : e.getStackTrace()) {
                Log.v("SDL", s.toString());
            }
        }
    }

    // Key events
    public boolean onKey(View  v, int keyCode, KeyEvent event) {
		if(keyCode==KeyEvent.KEYCODE_BACK)
		{
			if(!event.isAltPressed()) // some handle alt+back as "circle"
			{ 
				Log.v("SDL", "keycode_back w/o alt-key -> finish");
				// mActivity.glbasicOnPause(1);
				mActivity.finish();
				System.exit(0);
				return super.onKeyDown(keyCode, event);
			}
		}

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            Log.v("SDL", "key down: " + keyCode);
            SDLActivity.onNativeKeyDown(keyCode);
			return super.onKeyDown(keyCode, event);
            // return true;
        }
        else if (event.getAction() == KeyEvent.ACTION_UP) {
            //Log.v("SDL", "key up: " + keyCode);
            SDLActivity.onNativeKeyUp(keyCode);
			// super.onKeyUp(keyCode, event);
            return true;
        }
        return false;
    }

    // Touch events
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        float fx = event.getX();
        float fy = event.getY();
        float fp = event.getPressure();

        // TODO: Anything else we need to pass?        
        SDLActivity.onNativeTouch(action, fx, fy, fp);

		// GF: 2011-sep-09 multitouch GLBasic interface
        for (int i = 0; i<event.getPointerCount(); i++)
		{
            boolean masked = false;
            switch(event.getActionMasked())
			{
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_POINTER_UP:
                    masked = true;
                break;
            }
            if (masked && i != event.getActionIndex()) { continue; }
            float x = event.getX(i); float y = event.getY(i);
            /*
			float dx = 0; float dy = 0;
            if (event.getHistorySize()!=0) {
                dx = x - event.getHistoricalX(i,0);
                dy = y - event.getHistoricalY(i,0);
            }
            */
			int pid = event.getPointerId(i);
            int type = 0; // -1:up, 0:move, 1:down
            switch(event.getActionMasked())
			{
                case MotionEvent.ACTION_DOWN: type=1; break;
                case MotionEvent.ACTION_MOVE: type=0; break;
                case MotionEvent.ACTION_OUTSIDE: type=0; break;
                case MotionEvent.ACTION_POINTER_DOWN: type=1; break;
                case MotionEvent.ACTION_POINTER_UP: type=-1; break;
                case MotionEvent.ACTION_UP: type=-1; break;
                case MotionEvent.ACTION_CANCEL: type=-1; break;
            }
			SDLActivity.glbmultimouseevent(pid, type, (int)x, (int)y);

            if (masked) { break; }
        }
        return true;
    }






    // Sensor events
    public void enableSensor(int sensortype, boolean enabled) {
        // TODO: This uses getDefaultSensor - what if we have >1 accels?
        if (enabled) {
            mSensorManager.registerListener(this, 
                            mSensorManager.getDefaultSensor(sensortype), 
                            SensorManager.SENSOR_DELAY_GAME, null);
        } else {
            mSensorManager.unregisterListener(this, 
                            mSensorManager.getDefaultSensor(sensortype));
        }
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            SDLActivity.onNativeAccel(event.values[0],
                                      event.values[1],
                                      event.values[2]);
        }
    }

}

