package com.alcatrazstudios.apps.androidtracker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.alcatrazstudios.apps.androidtracker.services.CallRecordingsSaveService;
import com.alcatrazstudios.apps.androidtracker.services.FilesToUploadService;
import com.alcatrazstudios.apps.androidtracker.services.UploadAudioService;

public class UploadAudioReceiver extends WakefulBroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
//        startWakefulService(context,new Intent(context, CallRecordingsSaveService.class));
        Intent uploadIntent = new Intent(context, FilesToUploadService.class);
        startWakefulService(context, uploadIntent);
        try {
            wait(1000L * 60 * 2); // Wait for 2 minutes
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Intent intentAudio = new Intent(context, UploadAudioService.class);
        startWakefulService(context,intentAudio);
    }
}
