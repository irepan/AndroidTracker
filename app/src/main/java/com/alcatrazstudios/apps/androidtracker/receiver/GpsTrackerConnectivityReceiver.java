package com.alcatrazstudios.apps.androidtracker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.alcatrazstudios.apps.androidtracker.application.GpsTrackerApplication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Created by irepan on 24/02/17.
 */

public class GpsTrackerConnectivityReceiver extends BroadcastReceiver {
    private static final String TAG="ConectivityReceiver";
    public static ConnectivityReceiverListener connectivityReceiverListener;

    public GpsTrackerConnectivityReceiver() {
        super();
    }

    @Override
    public void onReceive(Context context, Intent arg1) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null
                && activeNetwork.isConnectedOrConnecting();

        if (connectivityReceiverListener != null) {
            connectivityReceiverListener.onNetworkConnectionChanged(isConnected);
        }
    }

    public static boolean isConnected() {
        ConnectivityManager
                cm = (ConnectivityManager) GpsTrackerApplication.getInstance().getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null
                && activeNetwork.isConnectedOrConnecting();
    }
/*    public static boolean isConnected(){
        return hostAvailable("oragps.com/internet.php",80);
    }
    public static boolean hostAvailable(String host, int port) {
        try  {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            // Either we have a timeout or unreachable host or failed DNS lookup
            e.printStackTrace();
            Log.e(TAG,"Error reading web",e);
            return false;
        } catch (Throwable thrError){
            thrError.printStackTrace();
            Log.e(TAG,"Error reading web 200",thrError);
            return false;
        }
    }*/

    public interface ConnectivityReceiverListener {
        void onNetworkConnectionChanged(boolean isConnected);
    }
}
