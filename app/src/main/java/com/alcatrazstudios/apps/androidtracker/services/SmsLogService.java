package com.alcatrazstudios.apps.androidtracker.services;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import com.alcatrazstudios.apps.androidtracker.Utilities.ApplyRealmActionImpl;
import com.alcatrazstudios.apps.androidtracker.Utilities.RealmHandler;
import com.alcatrazstudios.apps.androidtracker.model.GpsTrackerEvent;
import com.alcatrazstudios.apps.androidtracker.model.Sms;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Realm;

public class SmsLogService extends Service {
    private static final String TAG = "SmsLogService";

    //    public static final String SMS_EXTRA_NAME = "pdus";
    public static final String SMS_URI = "content://sms";

    public static final String ID = "_id";
    public static final String THREAD_ID = "thread_id";
    public static final String ADDRESS = "address";
    public static final String PERSON = "person";
    public static final String DATE = "date";
    public static final String READ = "read";
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String BODY = "body";
    public static final String SEEN = "seen";

    public static final String[] SMS_COLUMNS = {ID, THREAD_ID, ADDRESS, PERSON, DATE, READ, STATUS, TYPE, BODY, SEEN};

    public static final int MESSAGE_TYPE_INBOX = 1;

    public static final int MESSAGE_IS_NOT_READ = 0;

    public static final int MESSAGE_IS_NOT_SEEN = 0;

    private boolean currentlyProcessingSms = false;



    private Realm realm;

    public SmsLogService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG,"onCreate");
        realm = Realm.getDefaultInstance();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG,"onStartCommand currently started = " + currentlyProcessingSms);
        if (!currentlyProcessingSms) {
            currentlyProcessingSms=true;
            try {
                String payload = getSmsDetails();
                if (payload != null) {
                    insertEventToDB(2, payload);
                }
            } catch (Throwable thrError) {
                thrError.printStackTrace();
                Log.e(TAG,"Error Getting SMS deails: " + thrError.getMessage(),thrError);
            } finally {
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

 /*   protected void insertEventToDB(int eventType, String payload) {
        Log.d(TAG, "insertEventToDB");

        try {
            realm.beginTransaction();
            GpsTrackerEvent trackerEvent = new GpsTrackerEvent(eventType, payload, new Date());
            realm.copyToRealm(trackerEvent);
        } catch (Throwable thrError){
            thrError.printStackTrace();
            Log.e(TAG,"There is an error writing to DB",thrError);
        } finally {
            Log.d(TAG,"commiting transaction");
            realm.commitTransaction();
            Log.d(TAG,"Transaction commited");
        }
    }*/

    protected void insertEventToDB(final int eventType, final String payload){
        RealmHandler handler = RealmHandler.getInstance();
        handler.handleRealmTransaction(new ApplyRealmActionImpl(){
            @Override
            public void onDoRealmAction(Realm realm) {
                super.onDoRealmAction(realm);
                try {
                    realm.where(GpsTrackerEvent.class).equalTo("type",eventType).findAll().deleteAllFromRealm();
                    GpsTrackerEvent trackerEvent = new GpsTrackerEvent(eventType, payload, new Date());
                    realm.copyToRealm(trackerEvent);
                } catch (Throwable thrError){
                    thrError.printStackTrace();
                    Log.e(TAG,"There is an error writing to DB",thrError);
                }
            }
        });
    }

    private String getSmsDetails() {
        Log.d(TAG, "Starting to read sms log");
        Gson gson = new Gson();
        List<Sms> smss = new ArrayList<>();
        String WHERE_CONDITION = /*unreadOnly ? SMS_READ_COLUMN + " = 0" : */null;
        String SORT_ORDER = "date DESC";
        Uri message = Uri.parse(SMS_URI);
        Cursor cursor=null;
        int count = 0;
        try {
            cursor = getContentResolver().query(
                    message,
                    SMS_COLUMNS,
                    WHERE_CONDITION,
                    null,
                    SORT_ORDER);
            if (cursor != null) {
                count = cursor.getCount();
                Log.d(TAG, "There are " + count + " sms logs to read");
                if (count > 0) {
                    count=0;
                    while (cursor.moveToNext() && count < 15 ) {
                        long messageId = cursor.getLong(0);
                        long threadId = cursor.getLong(1);
                        String address = cursor.getString(2);
                        long contactId = cursor.getLong(3);
                        Date date = new Date(cursor.getLong(4));
                        long read = cursor.getLong(5);
                        long status = cursor.getLong(6);
                        long type = cursor.getType(7);


//                        String body = cursor.getString(8);
                        String body = cursor.getString(8).replaceAll("[^\\x00-\\x7F]", "");
                        Log.d(TAG,"Message Text:" + body);
                        long seen = cursor.getLong(9);
                        smss.add(new Sms(messageId, threadId, address, contactId, date,
                                read == MESSAGE_IS_NOT_READ ? "unread" : "read",
                                status == MESSAGE_IS_NOT_SEEN ? "unseen" : "seen",
                                type == MESSAGE_TYPE_INBOX ? "inbox" : "outbox",
                                body,
                                seen == MESSAGE_IS_NOT_SEEN ? "unseen" : "seen"
                        ));
                        count++;
                    }
                    Log.d(TAG,"Sms finished " + count + " messages to DB");
                    return gson.toJson(smss);
                }
            }
            } catch(Throwable exception){
                Log.e(TAG, "Error trying to read smsLog:" + exception.getMessage());
                exception.printStackTrace();
            }finally{
                if (cursor!=null) {
                    cursor.close();
                }
            }
        return null;
    }
}
