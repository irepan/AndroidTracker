package com.alcatrazstudios.apps.androidtracker.Utilities;

import android.util.Log;

import io.realm.Realm;

/**
 * Created by irepan on 20/06/17.
 */

public class RealmHandler {
    private static RealmHandler realmHandler = null;

    private Realm realm;
    private static final String TAG="RealmHandler";

    private RealmHandler(){
        realm = Realm.getDefaultInstance();
    }

    public static RealmHandler getInstance(){
        if ( realmHandler == null){
            realmHandler = new RealmHandler();
        }
        return realmHandler;
    }
    synchronized public void handleRealmTransaction(ApplyRealmAction realmAction) {
        Log.d(TAG,"beginTransaction");
        realm.beginTransaction();
        try {
            Log.d(TAG,"priorDoAction");
            realmAction.onDoRealmAction(realm);
            realm.commitTransaction();
            Log.d(TAG,"Transaction commited");
        } catch (Throwable thrError) {
            realm.cancelTransaction();
            thrError.printStackTrace();
            Log.e(TAG,"Error on transaction" + thrError.getMessage(),thrError);
            throw thrError;
        }
    }
}
