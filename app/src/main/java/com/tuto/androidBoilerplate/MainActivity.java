// app/src/main/java/com/tuto/androidBoilerplate/MainActivity.java
package com.tuto.androidBoilerplate;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView myWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // تعريف الـ WebView وتجهيزه
        myWebView = (WebView) findViewById(R.id.webview);
        
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true); // تفعيل الجافا سكريبت
        webSettings.setDomStorageEnabled(true); // تفعيل التخزين المحلي للمتصفح
        webSettings.setAllowFileAccess(true);

        // منع الروابط من الفتح في متصفح خارجي
        myWebView.setWebViewClient(new WebViewClient());

        // تحميل ملف index.html من مجلد assets
        myWebView.loadUrl("file:///android_asset/index.html");
    }

    // التعامل مع زر الرجوع في الهاتف للرجوع داخل صفحات الويب
    @Override
    public void onBackPressed() {
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
