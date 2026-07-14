package com.tuto.androidBoilerplate;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// --- مكتبات التحميل ---
import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;

// --- مكتبات الإشعارات وصلاحياتها ---
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class MainActivity extends Activity {

    private WebView myWebView;
    private static final String CHANNEL_ID = "app_download_channel";
    private static final int NOTIFICATION_PERMISSION_CODE = 101;
    private long downloadId; // لتتبع عملية التحميل الحالية

    // مستمع لانتهاء التحميل وإطلاق إشعار التطبيق الخاص
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadId == id) {
                showAppNotification("اكتمل تحميل الملف", "تم حفظ ملف الباوربوينت بنجاح في مجلد التحميلات (Downloads).");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. إنشاء قناة الإشعارات الخاصة بالتطبيق (مطلب أساسي لأندرويد 8.0 فما فوق)
        createNotificationChannel();

        // 2. طلب إذن الإشعارات تلقائياً عند فتح التطبيق لأجهزة أندرويد 13 فما فوق
        if (!hasNotificationPermission()) {
            requestNotificationPermission();
        }

        // 3. تسجيل مستمع لانتهاء التحميل
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // تعريف الـ WebView وتجهيزه
        myWebView = (WebView) findViewById(R.id.webview);
        
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true); // تفعيل الجافا سكريبت
        webSettings.setDomStorageEnabled(true); // تفعيل التخزين المحلي للمتصفح
        webSettings.setAllowFileAccess(true);

        // منع الروابط من الفتح في متصفح خارجي
        myWebView.setWebViewClient(new WebViewClient());

        // --- كود تفعيل التحميل ---
        myWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                
                // 4. التحقق وطلب الإذن مرة أخرى عند محاولة التحميل (إذا لم يوافق المستخدم مسبقاً)
                if (!hasNotificationPermission()) {
                    requestNotificationPermission();
                    Toast.makeText(getApplicationContext(), "يرجى السماح بالإشعارات لتلقي تنبيه عند اكتمال التحميل", Toast.LENGTH_LONG).show();
                }

                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);
                
                request.setDescription("جاري تحميل ملف الباوربوينت...");
                
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                request.setTitle(fileName);
                
                request.allowScanningByMediaScanner();
                
                // إخفاء إشعار النظام الافتراضي حتى لا يتكرر الإشعار للمستخدم
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
                
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    downloadId = dm.enqueue(request); // حفظ رقم التحميل لتتبعه
                }
                
                Toast.makeText(getApplicationContext(), "بدأ تحميل ملف الباوربوينت...", Toast.LENGTH_LONG).show();
            }
        });

        // تحميل رابط موقعك
        myWebView.loadUrl("https://mohamed-arabi-powerpoint.rf.gd/"); 
    }

    // --- دالة فحص هل إذن الإشعارات ممنوح أم لا ---
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // مسموح تلقائياً في الإصدارات القديمة من أندرويد
    }

    // --- دالة طلب إذن الإشعارات من نظام الأندرويد ---
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
        }
    }

    // --- دالة إظهار إشعار مخصص يحمل اسم وأيقونة التطبيق ---
    private void showAppNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(title)
               .setContentText(message)
               .setSmallIcon(android.R.drawable.stat_sys_download_done) 
               .setAutoCancel(true)
               .setPriority(Notification.PRIORITY_HIGH);

        notificationManager.notify(1, builder.build());
    }

    // --- دالة إنشاء قناة الإشعارات (Notification Channel) ---
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "إشعارات التطبيق";
            String description = "قناة مخصصة لإشعارات تحميل الملفات داخل التطبيق";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // إلغاء تسجيل المستمع عند إغلاق التطبيق لتفادي تسريب الذاكرة
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
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
