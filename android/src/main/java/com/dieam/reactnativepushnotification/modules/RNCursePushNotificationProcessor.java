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

public class RNCursePushNotificationProcessor {
    // Statics
    private static Context mContext;
    private static Class mIntentClass;
    private static Application mApplication;
    private static final int FRIEND_REQUEST_ID = 788032682;
    private static final int INCOMING_CALL_ID = 34932491;
    private static final int TYPE_INSTANT_MESSAGE = 1;
    private static final int TYPE_GROUP_MESSAGE = 2;
    private static final int TYPE_REQUEST = 3;
    private static final int TYPE_INCOMING_CALL = 5;
    private static final int TYPE_CONVERSATION_MESSAGE = 6;

    public static void setUp(Context context) {
        mContext = context;
    }

    public static void handleNotification(Bundle bundle, Class intentClass) {
        // Set Up
        mIntentClass = intentClass;

        // Get Type
        int type = Integer.parseInt(bundle.getString("type"));
        Log.d("RNCursePushNotificationProcessor", "Type: " + Integer.toString(type));

        // Route notification based on type
        if (type == TYPE_CONVERSATION_MESSAGE || type == TYPE_GROUP_MESSAGE || type == TYPE_INSTANT_MESSAGE) {
            handleChatMessage(bundle);
        } else if (type == TYPE_REQUEST) {
            handleFriendRequest(bundle);
        } else if (type == TYPE_INCOMING_CALL) {
            handleIncomingCall(bundle);
        }
    }

    private static void handleChatMessage(Bundle bundle) {
        // Set Up
        String groupName = bundle.getString("groupTitle");
        String username = bundle.getString("username");
        String title = "Curse";
        String prefix = "";
        int notificationID = 0;

        // Get Title/Notification ID
        if (groupName != null) {
            title = groupName;
            notificationID = bundle.getString("groupID").hashCode();
            prefix = username != null ? username + ": " : "";
        } else if (username != null) {
            title = username;
            String userID = bundle.getString("friendId");
            if (userID != null) {
                notificationID = userID.hashCode();
            }
        }

        // Add Message
        String message = prefix + bundle.getString("message");
        addMessageToConversation(notificationID, message);

        // Create Inbox Style
        NotificationCompat.Style inboxStyle = getInboxStyle(notificationID);

        // Send Notification
        NotificationCompat.Builder notification = new NotificationCompat.Builder(mContext)
            .setContentTitle(title)
            .setContentText(message)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(inboxStyle)
            .setAutoCancel(bundle.getBoolean("autoCancel", true));
        sendNotification(bundle, notification, notificationID);
    }

    private static void handleFriendRequest(Bundle bundle) {
        String title = "Curse";
        int notificationID = FRIEND_REQUEST_ID;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(mContext)
            .setContentTitle(title)
            .setContentText(bundle.getString("message"))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(bundle.getBoolean("autoCancel", true));
        sendNotification(bundle, notification, notificationID);
    }

    private static void handleIncomingCall(Bundle bundle) {
        String title = "Curse";
        int notificationID = INCOMING_CALL_ID;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(mContext)
            .setContentTitle(title)
            .setContentText(bundle.getString("message"))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(bundle.getBoolean("autoCancel", true));
        sendNotification(bundle, notification, notificationID);
    } 

    private static void sendNotification(Bundle bundle, NotificationCompat.Builder notification, int notificationID) {
        // Create Intent
        Intent intent = new Intent(mContext, mIntentClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        bundle.putBoolean("userInteraction", true);
        intent.putExtra("notification", bundle);

        // Create Pending Intent
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, notificationID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Set final values
        Resources res = mContext.getResources();
        String packageName = mContext.getPackageName();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", packageName);
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);
        int smallIconResId = res.getIdentifier("ic_notification", "mipmap", packageName);
        notification.setLargeIcon(largeIconBitmap);
        notification.setSmallIcon(smallIconResId);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            notification.setCategory(NotificationCompat.CATEGORY_CALL);
        }
        notification.setContentIntent(pendingIntent);
        if (!bundle.containsKey("vibrate") || bundle.getBoolean("vibrate")) {
            long vibration = bundle.containsKey("vibration") ? (long) bundle.getDouble("vibration") : 200;
            if (vibration == 0)
                vibration = 200;
            notification.setVibrate(new long[]{0, vibration});
        }

        // Build and send notification
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification info = notification.build();
        info.defaults |= Notification.DEFAULT_LIGHTS;
        notificationManager.notify(notificationID, info);
    }

    // Shared Preferences for Message Notifications
    public static void clearAll() {
        SharedPreferences.Editor editor = getNotificationSharedPreferences().edit();
        editor.clear();
        editor.commit();
        Log.d("RNCursePushNotificationProcessor", "Cleared!");
    }

    private static NotificationCompat.Style getInboxStyle(int conversationNumber) {
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

    private static void addMessageToConversation(int conversationNumber, String message) {
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

    private static JSONArray getConversationMessages(int conversationNumber) {
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

    private static void saveConversationMessages(int conversationNumber, JSONArray messages) {
        try {
            SharedPreferences.Editor editor = getNotificationSharedPreferences().edit();
            editor.putString(Integer.toString(conversationNumber), messages.toString());
            editor.commit();
        } catch (Exception e) {
            //
        }
    }

    private static SharedPreferences getNotificationSharedPreferences() {
        return mContext.getSharedPreferences("CURSE_PREFS", 0);
    }
}
