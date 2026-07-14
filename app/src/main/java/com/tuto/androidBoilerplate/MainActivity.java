package com.drarabi.ppt;

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
    private boolean isNotificationSent = false;

    // --- جسر التواصل الذكي ---
    public class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void onFileReady() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isNotificationSent) {
                        isNotificationSent = true;
                        showAppNotification("عرضك التقديمي جاهز! 🚀", "انتهى الذكاء الاصطناعي من إعداد ملفك. يمكنك الضغط لتحميله الآن.");
                        Toast.makeText(getApplicationContext(), "ملفك جاهز للتحميل الآن!", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. ربط الواجهة أولاً لضمان عدم ظهور شاشة بيضاء
        setContentView(R.layout.activity_main);

        // 2. إنشاء قناة الإشعارات
        createNotificationChannel();

        // 3. طلب إذن الإشعارات
        if (!hasNotificationPermission()) {
            requestNotificationPermission();
        }

        // 4. تجهيز الـ WebView بشكل آمن
        myWebView = (WebView) findViewById(R.id.webview);
        
        if (myWebView != null) {
            WebSettings webSettings = myWebView.getSettings();
            webSettings.setJavaScriptEnabled(true); 
            webSettings.setDomStorageEnabled(true); 
            webSettings.setAllowFileAccess(true);

            // ربط الجسر البرمجي مع صفحة الويب
            myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");

            myWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    isNotificationSent = false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    injectNotificationScript();
                }
            });

            myWebView.setDownloadListener(new DownloadListener() {
                @Override
                public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                    try {
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
                        request.setDescription("جاري تنزيل ملف الباوربوينت الخاص بك...");
                        request.setTitle(fileName);
                        request.allowScanningByMediaScanner();
                        
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                        
                        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        if (dm != null) {
                            dm.enqueue(request); 
                        }
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "حدث خطأ أثناء بدء التحميل: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }
                }
            });

            // 5. تحميل رابط موقعك في النهاية لضمان عمل كل شيء
            myWebView.loadUrl("https://mohamed-arabi-powerpoint.rf.gd/"); 
        }
    }

    private void injectNotificationScript() {
        if (myWebView == null) return;
        String js = "javascript:(function() {" +
                "function checkReady() {" +
                "    var elements = document.querySelectorAll('a, button, div, span, p');" +
                "    for (var i = 0; i < elements.length; i++) {" +
                "        var text = elements[i].innerText || elements[i].textContent;" +
                "        if ((text.includes('تحميل') || text.toLowerCase().includes('download') || text.includes('جاهز')) " +
                "            && (elements[i].tagName === 'A' || elements[i].tagName === 'BUTTON' || elements[i].classList.contains('btn') || elements[i].id.includes('download') || elements[i].className.includes('download'))) {" +
                "            if (elements[i].offsetWidth > 0 && elements[i].offsetHeight > 0) {" +
                "                AndroidInterface.onFileReady();" +
                "                return true;" +
                "            }" +
                "        }" +
                "    }" +
                "    return false;" +
                "}" +
                "if (!checkReady()) {" +
                "    var observer = new MutationObserver(function(mutations) {" +
                "        if (checkReady()) {" +
                "            observer.disconnect();" +
                "        }" +
                "    });" +
                "    observer.observe(document.body, { childList: true, subtree: true });" +
                "}" +
                "})()";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            myWebView.evaluateJavascript(js, null);
        } else {
            myWebView.loadUrl(js);
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; 
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
        }
    }

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
                   .setSmallIcon(R.mipmap.ic_launcher) 
                   .setAutoCancel(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                builder.setShowWhen(true);
            }

            notificationManager.notify(2, builder.build()); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    @Override
    public void onBackPressed() {
        if (myWebView != null && myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
                            }
