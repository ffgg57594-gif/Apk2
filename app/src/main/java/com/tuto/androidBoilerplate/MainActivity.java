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
    private long downloadId = -1; // لتتبع عملية التحميل الحالية
    private boolean isReceiverRegistered = false; // لمنع الكراش عند إلغاء التسجيل
    private String lastDownloadedFileName = "presentation.pptx"; // لتخزين اسم الملف الأخير

    // مستمع لانتهاء التحميل وإطلاق إشعار التطبيق الخاص بالتفاصيل والصورة
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId != -1 && downloadId == id) {
                    // إطلاق إشعار التطبيق المخصص عند اكتمال التحميل بنجاح
                    showAppNotification("اكتمل تحميل الملف بنجاح", "تم حفظ الملف باسم: " + lastDownloadedFileName);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            // 1. إنشاء قناة الإشعارات الخاصة بالتطبيق (مطلب أساسي لأندرويد 8.0 فما فوق)
            createNotificationChannel();

            // 2. طلب إذن الإشعارات تلقائياً عند فتح التطبيق لأجهزة أندرويد 13 فما فوق
            if (!hasNotificationPermission()) {
                requestNotificationPermission();
            }

            // 3. تسجيل مستمع لانتهاء التحميل مع مراعاة حماية أندرويد 14 فما فوق (Exported Receiver)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                    registerReceiver(onDownloadComplete, 
                                     new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 
                                     Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(onDownloadComplete, 
                                     new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
                isReceiverRegistered = true;
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                    isReceiverRegistered = true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            // تعريف الـ WebView وتجهيزه
            myWebView = (WebView) findViewById(R.id.webview);
            if (myWebView != null) {
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
                        try {
                            // التحقق وطلب الإذن مرة أخرى عند محاولة التحميل
                            if (!hasNotificationPermission()) {
                                requestNotificationPermission();
                                Toast.makeText(getApplicationContext(), "يرجى السماح بالإشعارات لتلقي تنبيه عند اكتمال التحميل", Toast.LENGTH_LONG).show();
                            }

                            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                            request.setMimeType(mimeType);
                            
                            String cookies = CookieManager.getInstance().getCookie(url);
                            if (cookies != null) {
                                request.addRequestHeader("cookie", cookies);
                            }
                            request.addRequestHeader("User-Agent", userAgent);
                            
                            // تخمين وحفظ اسم الملف لعرضه لاحقاً في إشعار التطبيق المخصص
                            lastDownloadedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                            
                            request.setDescription("جاري تحميل ملف الباوربوينت الخاص بك...");
                            request.setTitle(lastDownloadedFileName);
                            request.allowScanningByMediaScanner();
                            
                            // تعديل هام جداً: إظهار شريط تقدم التحميل الفعلي للمستخدم في الإشعارات أثناء التحميل
                            // وسيختفي إشعار النظام تلقائياً بمجرد الاكتمال ليحل محله إشعار تطبيقك المخصص
                            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                            
                            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, lastDownloadedFileName);
                            
                            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                            if (dm != null) {
                                downloadId = dm.enqueue(request); // حفظ رقم التحميل لتتبعه
                            }
                            
                            Toast.makeText(getApplicationContext(), "بدأ تحميل: " + lastDownloadedFileName, Toast.LENGTH_LONG).show();
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

    // --- دالة إظهار إشعار مخصص يحمل اسم وصورة (أيقونة) التطبيق بالكامل ---
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

            // هنا يتم إعداد إشعار التطبيق المخصص بشكل كامل:
            // تم تغيير الأيقونة لتكون أيقونة تطبيقك الأساسية R.mipmap.ic_launcher لتبان بوضوح مع الاسم
            builder.setContentTitle(title)
                   .setContentText(message)
                   .setSmallIcon(R.mipmap.ic_launcher) // أيقونة تطبيقك الرسمية
                   .setAutoCancel(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                builder.setShowWhen(true);
            }

            notificationManager.notify(1, builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- دالة إنشاء قناة الإشعارات (Notification Channel) ---
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                CharSequence name = "إشعارات التحميل ميكر";
                String description = "عرض حالة تقدم واكتمال تنزيلات ملفات الباوربوينت الخاصة بك";
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // إلغاء تسجيل المستمع عند إغلاق التطبيق لتفادي تسريب الذاكرة
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(onDownloadComplete);
                isReceiverRegistered = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // التعامل مع زر الرجوع في الهاتف للرجوع داخل صفحات الويب
    @Override
    public void onBackPressed() {
        if (myWebView != null && myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
                                             }
