package com.softwarrior.rutrackerdownloader;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class DownloadService extends Service {
    private NotificationManager mNM;
    private Intent mInvokeIntent;
    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;
    
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }
        
        @Override
        public void handleMessage(Message msg) {
            Bundle arguments = (Bundle)msg.obj;
        
            String txt = arguments.getString("name");
            
            Log.i(RutrackerDownloaderApp.TAG, "Message: " + msg + ", " + arguments.getString("name"));
        
            if ((msg.arg2&Service.START_FLAG_REDELIVERY) == 0) {
                txt = "New cmd #" + msg.arg1 + ": " + txt;
            } else {
                txt = "Re-delivered #" + msg.arg1 + ": " + txt;
            }
            
            showNotification(txt);
        
            // Normally we would do some work here...  for our sample, we will
            // just sleep for 5 seconds.
            long endTime = System.currentTimeMillis() + 5*1000;
            while (System.currentTimeMillis() < endTime) {
                synchronized (this) {
                    try {
                        wait(endTime - System.currentTimeMillis());
                    } catch (Exception e) {
                    }
                }
            }
        
            hideNotification();
            
            Log.i(RutrackerDownloaderApp.TAG, "Done with #" + msg.arg1);
            stopSelf(msg.arg1);
        }

    };
    
    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        Toast.makeText(this, R.string.service_created,Toast.LENGTH_SHORT).show();
        
        // This is who should be launched if the user selects our persistent
        // notification.
        mInvokeIntent = new Intent(this, Controller.class);

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("DownloadService", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(RutrackerDownloaderApp.TAG, "Starting #" + startId + ": " + intent.getExtras());
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.arg2 = flags;
        msg.obj = intent.getExtras();
        mServiceHandler.sendMessage(msg);
        Log.i("ServiceStartArguments", "Sending: " + msg);
        
        // For the start fail button, we will simulate the process dying
        // for some reason in onStartCommand().
        if (intent.getBooleanExtra("fail", false)) {
            // Don't do this if we are in a retry... the system will
            // eventually give up if we keep crashing.
            if ((flags&START_FLAG_RETRY) == 0) {
                // Since the process hasn't finished handling the command,
                // it will be restarted with the command again, regardless of
                // whether we return START_REDELIVER_INTENT.
                Process.killProcess(Process.myPid());
            }
        }
        
        // Normally we would consistently return one kind of result...
        // however, here we will select between these two, so you can see
        // how they impact the behavior.  Try killing the process while it
        // is in the middle of executing the different commands.
        return intent.getBooleanExtra("redeliver", false)
                ? START_REDELIVER_INTENT : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        mServiceLooper.quit();

        hideNotification();

        // Tell the user we stopped.
        Toast.makeText(DownloadService.this, R.string.service_destroyed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification(String text) {
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.stat_sample, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, Controller.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.service_label),
                       text, contentIntent);

        // We show this for as long as our service is processing a command.
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        
        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.service_created, notification);
    }
    
    private void hideNotification() {
        mNM.cancel(R.string.service_created);
    }
    
    // ----------------------------------------------------------------------
    public static class Controller extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setContentView(R.layout.service);

            // Watch for button clicks.
            Button button = (Button)findViewById(R.id.start1);
            button.setOnClickListener(mStart1Listener);
            button = (Button)findViewById(R.id.start2);
            button.setOnClickListener(mStart2Listener);
            button = (Button)findViewById(R.id.start3);
            button.setOnClickListener(mStart3Listener);
            button = (Button)findViewById(R.id.startfail);
            button.setOnClickListener(mStartFailListener);
            button = (Button)findViewById(R.id.kill);
            button.setOnClickListener(mKillListener);
        }

        private OnClickListener mStart1Listener = new OnClickListener() {
            public void onClick(View v) {
                startService(new Intent(Controller.this,DownloadService.class).putExtra("name", "One"));
            }
        };

        private OnClickListener mStart2Listener = new OnClickListener() {
            public void onClick(View v) {
                startService(new Intent(Controller.this,DownloadService.class).putExtra("name", "Two"));
            }
        };

        private OnClickListener mStart3Listener = new OnClickListener() {
            public void onClick(View v) {
                startService(new Intent(Controller.this, DownloadService.class) .putExtra("name", "Three").putExtra("redeliver", true));
            }
        };

        private OnClickListener mStartFailListener = new OnClickListener() {
            public void onClick(View v) {
                startService(new Intent(Controller.this,DownloadService.class).putExtra("name", "Failure").putExtra("fail", true));
            }
        };

        private OnClickListener mKillListener = new OnClickListener() {
            public void onClick(View v) {
                // This is to simulate the service being killed while it is
                // running in the background.
                Process.killProcess(Process.myPid());
            }
        };
    }
}

