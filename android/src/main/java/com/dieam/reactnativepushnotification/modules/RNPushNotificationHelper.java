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

            Resources res = mContext.getResources();
            String packageName = mContext.getPackageName();
            String subText = bundle.getString("subText");
            String title = bundle.getString("title");
            String group = bundle.getString("group");
            int notificationID = 0;


            // Build Notification
            if (title == null) {
                String groupName = bundle.getString("groupTitle");
                String username = bundle.getString("username");
                if (groupName != null) {
                    title = groupName;
                    if (username != null) {
                        subText = username;
                    }
                    group = bundle.getString("groupID");
                    notificationID = group.hashCode();
                } else if (username != null) {
                    title = username;
                    group = username;
                    String userID = bundle.getString("friendId");
                    if (userID != null) {
                        notificationID = userID.hashCode();
                    }
                } else {
                    ApplicationInfo appInfo = mContext.getApplicationInfo();
                    title = mContext.getPackageManager().getApplicationLabel(appInfo).toString();
                }
            }

            // Add to message history
            addMessageToConversation(notificationID, bundle.getString("message"));

            // Build notification
            NotificationCompat.Builder notification = new NotificationCompat.Builder(mContext)
                    .setContentTitle(title)
                    .setTicker(bundle.getString("ticker"))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(bundle.getBoolean("autoCancel", true));

            
            if (group != null) {
                notification.setGroup(group);
                notification.setStyle(getInboxStyle(notificationID));
            }
            
            notification.setContentText(bundle.getString("message"));

            String largeIcon = bundle.getString("largeIcon");
            
            if (subText != null) {
                notification.setSubText(subText);
            }

            if (bundle.containsKey("number")) {
                try {
                    int number = (int) bundle.getDouble("number");
                    notification.setNumber(number);
                } catch (Exception e) {
                    String numberAsString = bundle.getString("number");
                    if(numberAsString != null) {
                        int number = Integer.parseInt(numberAsString);
                        notification.setNumber(number);
                        Log.w(TAG, "'number' field set as a string instead of an int");
                    }
                }
            }

            int smallIconResId;
            int largeIconResId;

            String smallIcon = bundle.getString("smallIcon");

            if (smallIcon != null) {
                smallIconResId = res.getIdentifier(smallIcon, "mipmap", packageName);
            } else {
                smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
            }

            if (smallIconResId == 0) {
                smallIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);

                if (smallIconResId == 0) {
                    smallIconResId = android.R.drawable.ic_dialog_info;
                }
            }

            if (largeIcon != null) {
                largeIconResId = res.getIdentifier(largeIcon, "mipmap", packageName);
            } else {
                largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
            }

            Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

            if (largeIconResId != 0 && (largeIcon != null || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP)) {
                notification.setLargeIcon(largeIconBitmap);
            }

            notification.setSmallIcon(smallIconResId);
            String bigText = bundle.getString("bigText");

            if (bigText == null) {
                bigText = bundle.getString("message");
            }


            Intent intent = new Intent(mContext, intentClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            bundle.putBoolean("userInteraction", true);
            intent.putExtra("notification", bundle);

            if (!bundle.containsKey("playSound") || bundle.getBoolean("playSound")) {
                Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                notification.setSound(defaultSoundUri);
            }

            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                notification.setCategory(NotificationCompat.CATEGORY_CALL);

                String color = bundle.getString("color");
                if (color != null) {
                    notification.setColor(Color.parseColor(color));
                }
            }

            if (bundle.containsKey("id")) {
                try {
                    notificationID = (int) bundle.getDouble("id");
                } catch (Exception e) {
                    String notificationIDString = bundle.getString("id");

                    if (notificationIDString != null) {
                        Log.w(TAG, "'id' field set as a string instead of an int");

                        try {
                            notificationID = Integer.parseInt(notificationIDString);
                        } catch (NumberFormatException nfe) {
                            Log.w(TAG, "'id' field could not be converted to an int, ignoring it", nfe);
                        }
                    }
                }
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, notificationID, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            notification.setContentIntent(pendingIntent);

            if (!bundle.containsKey("vibrate") || bundle.getBoolean("vibrate")) {
                long vibration = bundle.containsKey("vibration") ? (long) bundle.getDouble("vibration") : DEFAULT_VIBRATION;
                if (vibration == 0)
                    vibration = DEFAULT_VIBRATION;
                notification.setVibrate(new long[]{0, vibration});
            }

            Notification info = notification.build();
            info.defaults |= Notification.DEFAULT_LIGHTS;

            if (bundle.containsKey("tag")) {
                String tag = bundle.getString("tag");
                notificationManager.notify(tag, notificationID, info);
            } else {
                notificationManager.notify(notificationID, info);
            }
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
