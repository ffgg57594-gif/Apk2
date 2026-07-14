package com.tuto.androidBoilerplate;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// --- المكتبات الجديدة المطلوبة للتحميل ---
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;

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

        // --- كود تفعيل التحميل (DownloadListener) ---
        myWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                // إنشاء طلب تحميل جديد
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                
                // جلب الكوكيز في حال كان التحميل يحتاج لتسجيل دخول بالموقع
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                
                request.setDescription("جاري تحميل ملف الباوربوينت...");
                
                // تخمين اسم الملف من الرابط
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(fileName);
                
                request.allowScanningByMediaScanner();
                
                // إظهار إشعار عند بدء التحميل وعند الانتهاء منه
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                
                // حفظ الملف في مجلد التحميلات (Downloads) العام بالهاتف
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                
                // تشغيل مدير التحميلات
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                
                // رسالة للمستخدم
                Toast.makeText(getApplicationContext(), "بدأ التحميل... يرجى مراجعة شريط الإشعارات", Toast.LENGTH_LONG).show();
            }
        });
        // ---------------------------------------------

        // تحميل رابط الموقع الخاص بك
        myWebView.loadUrl("https://mohamed-arabi-powerpoint.rf.gd/"); 
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
