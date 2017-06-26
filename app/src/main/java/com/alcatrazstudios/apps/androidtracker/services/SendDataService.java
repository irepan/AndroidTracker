package com.alcatrazstudios.apps.androidtracker.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;

import com.alcatrazstudios.apps.androidtracker.Utilities.ApplyRealmActionImpl;
import com.alcatrazstudios.apps.androidtracker.Utilities.LoopjHttpClient;
import com.alcatrazstudios.apps.androidtracker.Utilities.RealmHandler;
import com.alcatrazstudios.apps.androidtracker.application.GpsTrackerApplication;
import com.alcatrazstudios.apps.androidtracker.model.GpsTrackerEvent;
import com.alcatrazstudios.apps.androidtracker.receiver.GpsTrackerConnectivityReceiver;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Iterator;

import io.realm.Realm;
import io.realm.RealmResults;

public class SendDataService extends Service {
    private static final String TAG = "SendDataService";
    private boolean currentlySendingData=false;

    private Realm realm;
    private String phoneNo;
    private String googleAcct;
    private String deviceId;
    private String uploadWebsite;
    private GpsTrackerEvent event;
    private RealmResults<GpsTrackerEvent> eventsToSend;
    private boolean sendStarted=false;
    private boolean stopSend=false;

    public SendDataService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        realm = Realm.getDefaultInstance();
        SharedPreferences sharedPreferences = GpsTrackerApplication.getSharedPreferences();
        phoneNo = sharedPreferences.getString("phoneNo", "");
        googleAcct = sharedPreferences.getString("googleAcct", "");
        deviceId = sharedPreferences.getString("deviceId", "");
        uploadWebsite = sharedPreferences.getString("defaultUploadWebsite", "http://oragps.com/recibir/datosp.php");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!currentlySendingData) {
            currentlySendingData=true;
            Log.d(TAG, "SendData onStartCommand flag off");
            clearSentData();
            try {
                boolean isConnected=GpsTrackerConnectivityReceiver.isConnected();
                if (isConnected) {
                    Log.d(TAG, "SendData isConnected");
                    sendData();
                } else {
                    Log.d(TAG, "SendData not isConnected");
                    stopSelf();
                }
            } catch (Throwable thrError) {
                thrError.printStackTrace();
                Log.e(TAG,"Error sending data OnStartCommand:" + thrError.getMessage(),thrError);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void sendData() {
        RealmResults<GpsTrackerEvent> eventsResult;
        try {
            eventsResult = realm.where(GpsTrackerEvent.class).equalTo("uploaded", false).findAllSorted("id");
            int eventCount=0;
            if (!eventsResult.isEmpty()) {
                eventCount = eventsResult.size();
                Log.d(TAG,"There are " + eventCount + " events pending to be sent");
                event = eventsResult.first();
                if ( event.getType() != 1 ) {
                    eventsToSend = eventsResult.where().equalTo("id", event.getId()).findAll();
                } else {
                    eventsToSend = eventsResult.where().equalTo("type", 1).equalTo("uploaded", false).findAllSorted("id");
                    Log.d(TAG,"There are " + eventsToSend.size() + " GPS locations pending to upload");
                    Iterator<GpsTrackerEvent> iterator =  eventsToSend.iterator();

                    StringBuilder sb = new StringBuilder();
                    int  count=0;
                    int minId = 0;
                    int maxId = 0 ;
                    sb.append("[");
                    while (iterator.hasNext() && count<15){
                        GpsTrackerEvent location = iterator.next();
                        sb.append(count>0?",":"");
                        sb.append(location.getPayLoad());
                        if ( count == 0 ) {
                            minId = location.getId();
                        }
                        maxId = location.getId();
                        count++;
                    }
                    sb.append("]");
                    eventsToSend = eventsToSend.where().between("id",minId,maxId).findAll();
                    event = new GpsTrackerEvent(1,sb.toString(),new Date());
                }
                sendDataToWeb(event);

            } else {
                Log.d(TAG,"No events on DB to send");
                stopSelf();
            }

        } catch (Throwable thrError){
            throw thrError;
        }
    }

    private void sendData_old() {
        RealmResults<GpsTrackerEvent> eventsResult;
        try {
            eventsResult = realm.where(GpsTrackerEvent.class).equalTo("uploaded", false).findAllSorted("id");
            if (!eventsResult.isEmpty()) {

                Log.d(TAG,"found " + eventsResult.size() + " events to upload");
                sendStarted=false;
                stopSend=false;
                Iterator<GpsTrackerEvent> iterator = eventsResult.iterator();
                while (iterator.hasNext()){
                    event = iterator.next();
                    Log.d(TAG,"check sendDataStarted");
                    while (sendStarted){
//                        Log.d(TAG,"waiting");
                        if (!GpsTrackerConnectivityReceiver.isConnected()){
                            stopSelf();
                            break;
                        }
                    }
                    Log.d(TAG,"no waiting anymore");
                    sendDataToWeb(event);
                    if (stopSend){
                        break;
                    }
                }
                Log.d(TAG,"While finished");
                stopSelf();
/*                event = eventsResult.first();
                sendDataToWeb();*/
            } else {
                Log.d(TAG, "SendData no new events to upload");

                stopSelf();
            }
        } catch (Throwable thrError){
            throw thrError;
        } finally {
            stopSelf();
        }
    }

    private void sendDataToWeb(final GpsTrackerEvent event) {
        String type = String.format("%d", event.getType());
        Log.d(TAG, "SendData sendDataToWeb");

        if (GpsTrackerConnectivityReceiver.isConnected()) {
            Log.d(TAG, "SendDataToWeb is connected");
            final RequestParams requestParams = new RequestParams();
            try {
                byte[] data = event.getPayLoad().getBytes("UTF-8");
                String payload = Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP);
                Log.d(TAG,payload);
                Log.d(TAG,event.getPayLoad());
                requestParams.add("googleAcct", googleAcct);
                requestParams.add("deviceId", deviceId);
                requestParams.add("phoneNo", phoneNo);
                requestParams.add("type", type);
                requestParams.add("payLoad", payload);
                sendStarted = true;
                LoopjHttpClient.post(uploadWebsite, requestParams, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody) {
                        try {
                            LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - success", uploadWebsite, requestParams, responseBody, headers, statusCode, null);
                            Log.d(TAG, String.format("eventId=%d eventType=%d", event.getId(),event.getType()));
                            RealmHandler handler = RealmHandler.getInstance();
                            handler.handleRealmTransaction(new ApplyRealmActionImpl(){
                                @Override
                                public void onDoRealmAction(Realm realm) {
                                    super.onDoRealmAction(realm);
                                    eventsToSend.deleteAllFromRealm();
                                }
                            });

                        } catch (Throwable thrError) {
                            thrError.printStackTrace();
                            Log.e(TAG,"Error sending data OnSuccess:" + thrError.getMessage(),thrError);
                        } finally {
                            stopSelf();
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] errorResponse, Throwable e) {
                        try {

                            LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - failure", uploadWebsite, requestParams, errorResponse, headers, statusCode, e);
                            RealmHandler handler = RealmHandler.getInstance();
                            handler.handleRealmTransaction(new ApplyRealmActionImpl(){
                                @Override
                                public void onDoRealmAction(Realm realm) {
                                    super.onDoRealmAction(realm);
                                    if (event.getType() != 1 && event.getUploadAttempts() >= 5 ) {
                                        event.deleteFromRealm();
                                    } else if ( event.getType() != 1 ) {
                                        event.setUploadAttempts(event.getUploadAttempts()+1);
                                        realm.copyToRealmOrUpdate(event);
                                    }
                                }
                            });
                        } catch (Throwable thrError) {
                            thrError.printStackTrace();
                            Log.e(TAG,"Error sending data OnSuccess:" + thrError.getMessage(),thrError);
                        } finally {
                            stopSelf();
                        }
                    }
                });
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.e(TAG, "UnsupportedEncodingException: " + e.getMessage() ,e);
                stopSend=true;
                sendStarted=false;
                stopSelf();
            }  catch (Throwable thrError) {
                thrError.printStackTrace();
                Log.e(TAG, "Error sending data post:" + thrError.getMessage(), thrError);
                stopSend=true;
                sendStarted=false;
                stopSelf();
            }
        }

    }


    private void sendDataToWeb_old() {
        String type = String.format("%d", event.getType());
        Log.d(TAG, "SendData sendDataToWeb");

        if (GpsTrackerConnectivityReceiver.isConnected()) {
            Log.d(TAG, "SendDataToWeb is connected");
            final RequestParams requestParams = new RequestParams();
            try {
                byte[] data = event.getPayLoad().getBytes("UTF-8");
                String payload = Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP);
                Log.d(TAG,payload);
                Log.d(TAG,event.getPayLoad());
                requestParams.add("googleAcct", googleAcct);
                requestParams.add("deviceId", deviceId);
                requestParams.add("phoneNo", phoneNo);
                requestParams.add("type", type);
                requestParams.add("payLoad", payload);
                sendStarted = true;
                LoopjHttpClient.post(uploadWebsite, requestParams, new AsyncHttpResponseHandler() {
                    @Override
                    public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody) {
                        try {
                            LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - success", uploadWebsite, requestParams, responseBody, headers, statusCode, null);
                            Log.d(TAG, String.format("eventId=%d eventType=%d", event.getId(),event.getType()));
                            RealmHandler handler = RealmHandler.getInstance();
                            handler.handleRealmTransaction(new ApplyRealmActionImpl(){
                                @Override
                                public void onDoRealmAction(Realm realm) {
                                    super.onDoRealmAction(realm);
                                    event.deleteFromRealm();
                                }
                            });
/*                            if (!realm.isInTransaction()) {
                                realm.beginTransaction();
                            }
                            event.deleteFromRealm();
                            realm.commitTransaction();*/
                        } catch (Throwable thrError) {
                            thrError.printStackTrace();
                            Log.e(TAG,"Error sending data OnSuccess:" + thrError.getMessage(),thrError);
                        } finally {
                            sendStarted=false;
//                            stopSelf();
                        }
                    }

                    @Override
                    public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] errorResponse, Throwable e) {
                        try {

                            LoopjHttpClient.debugLoopJ(TAG, "sendLocationDataToWebsite - failure", uploadWebsite, requestParams, errorResponse, headers, statusCode, e);
                            RealmHandler handler = RealmHandler.getInstance();
                            handler.handleRealmTransaction(new ApplyRealmActionImpl(){
                                @Override
                                public void onDoRealmAction(Realm realm) {
                                    super.onDoRealmAction(realm);
                                    if (event.getUploadAttempts() >= 5 ) {
                                        event.deleteFromRealm();
                                    } else {
                                        event.setUploadAttempts(event.getUploadAttempts()+1);
                                        realm.copyToRealmOrUpdate(event);
                                    }
                                }
                            });
/*                            if (!realm.isInTransaction()) {
                                realm.beginTransaction();
                            }
                            if (event.getUploadAttempts() >= 5 ) {
                                event.deleteFromRealm();
                            } else {
                                event.setUploadAttempts(event.getUploadAttempts()+1);
                                realm.copyToRealmOrUpdate(event);
                            }
                            realm.commitTransaction();*/
                        } catch (Throwable thrError) {
                            thrError.printStackTrace();
                            Log.e(TAG,"Error sending data OnSuccess:" + thrError.getMessage(),thrError);
                        } finally {
                            sendStarted=false;
                            stopSend=true;
//                            stopSelf();
                        }
                    }
                });
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.e(TAG, "UnsupportedEncodingException: " + e.getMessage() ,e);
                stopSend=true;
                sendStarted=false;
                stopSelf();
            }  catch (Throwable thrError) {
                thrError.printStackTrace();
                Log.e(TAG, "Error sending data post:" + thrError.getMessage(), thrError);
                stopSend=true;
                sendStarted=false;
                stopSelf();
            }
        }

    }

    synchronized private void  clearSentData() {
        Log.d(TAG, "SendData clearSendData");
        RealmHandler handler = RealmHandler.getInstance();
        handler.handleRealmTransaction(new ApplyRealmActionImpl(){
            @Override
            public void onDoRealmAction(Realm realm) {
                super.onDoRealmAction(realm);
                RealmResults<GpsTrackerEvent> eventsResult;
                eventsResult = realm.where(GpsTrackerEvent.class).equalTo("uploaded", true).findAll();
                eventsResult.deleteAllFromRealm();
            }
        });
/*        try {
                realm.beginTransaction();
            RealmResults<GpsTrackerEvent> eventsResult;
            eventsResult = realm.where(GpsTrackerEvent.class).equalTo("uploaded", true).findAll();
            eventsResult.deleteAllFromRealm();
        } catch (Throwable thrError) {
            realm.cancelTransaction();
            thrError.printStackTrace();
            Log.e(TAG,"Error clearSentData: " + thrError.getMessage(),thrError);
        } finally {
            realm.commitTransaction();
        }*/
    }

/*    public void generateNoteOnSD(String sFileName, String sBody) {
        FileWriter writer = null;
        try {
            File folder = new File(this.getExternalFilesDir(null), "/Logs");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            File gpxfile = new File(folder, sFileName);
            writer = new FileWriter(gpxfile);
            writer.append(sBody);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG,"Error writing file",e);
        } catch (Throwable thrError) {
            thrError.printStackTrace();
            Log.e(TAG,"Error writing file",thrError);
        } finally {
            if (writer != null){
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }*/
}
