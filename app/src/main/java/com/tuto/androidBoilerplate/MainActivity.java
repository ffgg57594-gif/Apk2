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

public class MainActivity extends Activity {

    private WebView myWebView;
    private static final String CHANNEL_ID = "app_download_channel";
    private static final int NOTIFICATION_PERMISSION_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            // إنشاء قناة الإشعارات
            createNotificationChannel();

            // طلب إذن الإشعارات تلقائياً عند فتح التطبيق لأجهزة أندرويد 13 فما فوق
            if (!hasNotificationPermission()) {
                requestNotificationPermission();
            }

            // تعريف الـ WebView وتجهيزه
            myWebView = (WebView) findViewById(R.id.webview);
            if (myWebView != null) {
                WebSettings webSettings = myWebView.getSettings();
                webSettings.setJavaScriptEnabled(true); 
                webSettings.setDomStorageEnabled(true); 
                webSettings.setAllowFileAccess(true);

                // منع الروابط من الفتح في متصفح خارجي
                myWebView.setWebViewClient(new WebViewClient());

                // --- كود تفعيل التحميل ---
                myWebView.setDownloadListener(new DownloadListener() {
                    @Override
                    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                        try {
                            // هذه الدالة (onDownloadStart) لن يتم استدعاؤها إلا بعد مرور (2-3 دقائق) 
                            // عندما ينتهي الذكاء الاصطناعي من تجهيز الملف ويرسله للتحميل.

                            if (!hasNotificationPermission()) {
                                requestNotificationPermission();
                            }

                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                            request.setMimeType(mimeType);
                            
                            String cookies = CookieManager.getInstance().getCookie(url);
                            if (cookies != null) {
                                request.addRequestHeader("cookie", cookies);
                            }
                            request.addRequestHeader("User-Agent", userAgent);
                            
                            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                            request.setDescription("جاري تنزيل الملف الخاص بك...");
                            request.setTitle(fileName);
                            request.allowScanningByMediaScanner();
                            
                            // إبقاء إشعار النظام الافتراضي (شريط التحميل والاكتمال) كما هو
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                            
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                            
                            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                            if (dm != null) {
                                dm.enqueue(request); // بدء التحميل الفعلي
                            }
                            
                            // إرسال إشعار تطبيقك المخصص "الآن فقط" ليخبره أن التجهيز انتهى وبدأ التحميل
                            showAppNotification("عرضك التقديمي جاهز! 🚀", "انتهى الذكاء الاصطناعي وبدأ تحميل الملف: " + fileName);
                            
                            Toast.makeText(getApplicationContext(), "الملف جاهز! بدأ التحميل...", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "حدث خطأ أثناء بدء التحميل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            e.printStackTrace();
                        }
                    }
                });

                // تحميل رابط موقعك
                myWebView.loadUrl("https://mohamed-arabi-powerpoint.rf.gd/"); 
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "حدث خطأ أثناء تشغيل التطبيق", Toast.LENGTH_LONG).show();
        }
    }

    // --- دالة فحص هل إذن الإشعارات ممنوح أم لا ---
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; 
    }

    // --- دالة طلب إذن الإشعارات من نظام الأندرويد ---
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
        }
    }

    // --- دالة إظهار إشعار مخصص فوري يحمل اسم وصورة تطبيقك ---
    private void showAppNotification(String title, String message) {
        try {
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
                   .setSmallIcon(R.mipmap.ic_launcher) // أيقونة تطبيقك
                   .setAutoCancel(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                builder.setShowWhen(true);
            }

            notificationManager.notify(2, builder.build()); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- دالة إنشاء قناة الإشعارات (Notification Channel) ---
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                CharSequence name = "إشعارات التطبيق";
                String description = "إشعارات جاهزية الملفات للتحميل";
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                channel.setDescription(description);
                
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // التعامل مع زر الرجوع في الهاتف
    @Override
    public void onBackPressed() {
        if (myWebView != null && myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
