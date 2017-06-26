package com.alcatrazstudios.apps.androidtracker.Utilities;

import java.io.Serializable;

import io.realm.Realm;

/**
 * Created by irepan on 20/06/17.
 */

public interface ApplyRealmAction extends Serializable {

    void onDoRealmAction(Realm realm);
}
