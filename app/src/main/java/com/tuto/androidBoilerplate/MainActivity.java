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

        // ---------------------------------------------------------
        // التعديل هنا:
        // لا يمكن تشغيل PHP محلياً من مجلد assets.
        // يجب رفع السكربت على سيرفر خارجي واستدعاء الرابط كالتالي:
        // ---------------------------------------------------------
        myWebView.loadUrl("https://mohamed-arabi-powerpoint.rf.gd"); 
        
        // ملاحظة: إذا كان ملف الـ PHP لا يحتوي على أي أكواد PHP فعلية 
        // وهو عبارة عن HTML فقط، فقم بتغيير امتداده إلى .html واستخدم السطر القديم:
        // myWebView.loadUrl("file:///android_asset/index.html");
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
