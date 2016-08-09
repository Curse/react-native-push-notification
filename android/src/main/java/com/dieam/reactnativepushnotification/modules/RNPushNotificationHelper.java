package com.dieam.reactnativepushnotification.modules;


import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;

public class RNPushNotificationHelper {
    private static final long DEFAULT_VIBRATION = 200L;
    private static final String TAG = RNPushNotificationHelper.class.getSimpleName();

    private Context mContext;

    public RNPushNotificationHelper(Application context) {
        mContext = context;
        RNCursePushNotificationProcessor.setUp(context);
    }

    public Class getMainActivityClass() {
        String packageName = mContext.getPackageName();
        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private AlarmManager getAlarmManager() {
        return (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
    }

    private PendingIntent getScheduleNotificationIntent(Bundle bundle) {
        int notificationID = 0;
        // String notificationIDString = bundle.getString("id");
        // if ( notificationIDString != null ) {
        //     notificationID = Integer.parseInt(notificationIDString);
        // } else {
        //     notificationID = (int) System.currentTimeMillis();
        // }

        Intent notificationIntent = new Intent(mContext, RNPushNotificationPublisher.class);
        notificationIntent.putExtra(RNPushNotificationPublisher.NOTIFICATION_ID, notificationID);
        notificationIntent.putExtras(bundle);

        return PendingIntent.getBroadcast(mContext, notificationID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void sendNotificationScheduled(Bundle bundle) {
        Class intentClass = getMainActivityClass();
        if (intentClass == null) {
            return;
        }

        Double fireDateDouble = bundle.getDouble("fireDate", 0);
        if (fireDateDouble == 0) {
            return;
        }

        long fireDate = Math.round(fireDateDouble);
        long currentTime = System.currentTimeMillis();

        Log.i("ReactSystemNotification", "fireDate: " + fireDate + ", Now Time: " + currentTime);
        PendingIntent pendingIntent = getScheduleNotificationIntent(bundle);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getAlarmManager().setExact(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
        } else {
            getAlarmManager().set(AlarmManager.RTC_WAKEUP, fireDate, pendingIntent);
        }
    }

    public void sendNotification(Bundle bundle) {
        try {
            Class intentClass = getMainActivityClass();
            if (intentClass == null) {
                return;
            }

            if (bundle.getString("message") == null) {
                return;
            }

            RNCursePushNotificationProcessor.handleNotification(bundle, intentClass);
        } catch (Exception e) {
            Log.e(TAG, "failed to send push notification", e);
        }
    }

    public void cancelAll() {
       try {
            NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.cancelAll();

            Bundle b = new Bundle();
            b.putString("id", "0");
            getAlarmManager().cancel(getScheduleNotificationIntent(b));

            RNCursePushNotificationProcessor.clearAll();
        } catch (Exception e) {
            Log.e(TAG, "failed to cancel all", e);
        }
    }

    // Inbox Style
    private NotificationCompat.Style getInboxStyle(int conversationNumber) {
        // Set Up
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

        // Messages
        JSONArray messages = getConversationMessages(conversationNumber);
        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                if (i == 5) {
                    style.setSummaryText("+" + Integer.toString(messages.length() - 5));
                    break;
                }
                try {
                    int index = messages.length() - i - 1;
                    String newLine = messages.getString(index);
                    style.addLine(newLine);
                } catch (JSONException e) {
                    // Don't do nuffin'
                }
            }
        }

        return style;
    }

    private void addMessageToConversation(int conversationNumber, String message) {
        try {
            // Get Current Conversation
            JSONArray conversation = getConversationMessages(conversationNumber);
            if (conversation == null) {
                conversation = new JSONArray();
            }
            conversation.put(message);

            // Save
            saveConversationMessages(conversationNumber, conversation);
        } catch (Exception e) {
            //
        }
    }

    private JSONArray getConversationMessages(int conversationNumber) {
        // Get set from sharedPrefs
        SharedPreferences prefs = getNotificationSharedPreferences();
        String json = prefs.getString(Integer.toString(conversationNumber), null);
        if (json != null) {
            try {
                return new JSONArray(json);
            } catch (JSONException e) {
                return new JSONArray();
            }
        }
        return new JSONArray();
    }

    private void saveConversationMessages(int conversationNumber, JSONArray messages) {
        try {
            SharedPreferences.Editor editor = getNotificationSharedPreferences().edit();
            editor.putString(Integer.toString(conversationNumber), messages.toString());
        } catch (Exception e) {
            //
        }
    }

    private SharedPreferences getNotificationSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mContext);
    }
    
}
