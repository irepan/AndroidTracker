package com.alcatrazstudios.apps.androidtracker.Utilities;

/**
 * Created by irepan on 28/03/17.
 */

import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class LoopjHttpClient {
    private static AsyncHttpClient client = new AsyncHttpClient();

    public static void get(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
        client.get(url, params, responseHandler);
    }

    public static void post(String url, RequestParams requestParams, AsyncHttpResponseHandler responseHandler) {
        client.post(url, requestParams, responseHandler);
    }

    public static void debugLoopJ(String TAG, String methodName,String url, RequestParams requestParams, byte[] response, cz.msebera.android.httpclient.Header[] headers, int statusCode, Throwable t) {

        Log.d(TAG, client.getUrlWithQueryString(false, url, requestParams));

        if (headers != null) {
            Log.e(TAG, methodName);
            Log.d(TAG, "Return Headers:");

            if (t != null) {
                Log.d(TAG, "Throwable:" + t);
            }

            Log.e(TAG, "StatusCode: " + statusCode);

            if (response != null) {
                Log.d(TAG, "Response: " + new String(response));
            }

        }
    }

    public static String getUrlWithQueryString(String url, RequestParams requestParams) {
        return client.getUrlWithQueryString(false, url, requestParams);
    }
}