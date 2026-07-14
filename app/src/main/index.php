<?php
// ==========================================
//  الخلفية - Backend (PHP Logic)
// ==========================================
if (isset($_GET['api']) && $_GET['api'] == '1') {
    header('Content-Type: text/plain; charset=utf-8');
    header('Cache-Control: no-cache');
    header('X-Accel-Buffering: no'); 
    set_time_limit(0); 
    
    while (ob_get_level() > 0) ob_end_flush();

    function emit_log($msg, $level = 'info') {
        echo json_encode(['type' => 'log', 'level' => $level, 'msg' => $msg]) . "\n";
        flush();
    }
    function emit_done($url) {
        echo json_encode(['type' => 'done', 'url' => $url]) . "\n";
        flush();
    }
    function emit_auth($apiKey) {
        echo json_encode(['type' => 'auth', 'apiKey' => $apiKey]) . "\n";
        flush();
    }
    function emit_error($msg) {
        echo json_encode(['type' => 'error', 'msg' => $msg]) . "\n";
        flush();
        exit;
    }

    function curl_request($url, $method = 'GET', $data = null, $headers_arr = [], $is_form = false) {
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_ENCODING, ""); 
        
        $curl_headers = [];
        foreach ($headers_arr as $key => $val) {
            $curl_headers[] = "$key: $val";
        }
        curl_setopt($ch, CURLOPT_HTTPHEADER, $curl_headers);

        if ($method === 'POST') {
            curl_setopt($ch, CURLOPT_POST, true);
            if ($data !== null) {
                if ($is_form) {
                    curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query($data));
                } else {
                    curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($data));
                }
            }
        }

        $result = curl_exec($ch);
        $httpcode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        
        return ['code' => $httpcode, 'body' => $result];
    }
    
    $mailHeaders = [
        "Content-Type" => "application/json"
    ];
    
    $poHeaders = [
        'User-Agent' => 'Mozilla/5.0 (Linux; Android 13; RMX3191 Build/SP1A.210812.016) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.7727.137 Mobile Safari/537.36',
        'Accept-Encoding' => 'gzip, deflate, br, zstd',
        'Content-Type' => 'application/json',
        'sec-ch-ua-platform' => '"Android"',
        'sec-ch-ua' => '"Android WebView";v="147", "Not.A/Brand";v="8", "Chromium";v="147"',
        'sec-ch-ua-mobile' => '?1',
        'origin' => 'https://presenton.ai',
        'x-requested-with' => 'mark.via.gp',
        'sec-fetch-site' => 'same-site',
        'sec-fetch-mode' => 'cors',
        'sec-fetch-dest' => 'empty',
        'referer' => 'https://presenton.ai/',
        'accept-language' => 'ar-EG,ar;q=0.9,en-EG;q=0.8,en-US;q=0.7,en;q=0.6',
        'priority' => 'u=1, i'
    ];

    $input = json_decode(file_get_contents('php://input'), true);
    if (!$input) emit_error("لم يتم استلام بيانات صحيحة من المتصفح.");

    $payloadConfig = [
        "content" => $input['content'] ?? "Create a professional PowerPoint.",
        "n_slides" => (int)($input['n_slides'] ?? 5),
        "language" => $input['language'] ?? "Arabic",
        "tone" => $input['tone'] ?? "professional",
        "verbosity" => $input['verbosity'] ?? "standard",
        "theme" => $input['theme'] ?? "professional-blue",
        "standard_template" => $input['template'] ?? "modern",
        "image_type" => $input['image_type'] ?? "stock",
        "export_as" => $input['export_as'] ?? "pptx"
    ];

    $apiKey = $input['api_key'] ?? '';

    try {
        if (empty($apiKey)) {
            emit_log("🔧 لم يتم العثور على جلسة محفوظة. جاري تهيئة الإيميل المؤقت...", "info");
            $mailUrl = "https://api.mail.tm";
            
            $res = curl_request("$mailUrl/domains", 'GET', null, $mailHeaders);
            $domainsData = json_decode($res['body'], true);
            
            if (empty($domainsData['hydra:member'])) {
                emit_error("❌ لا يوجد دومينات - الكود: {$res['code']}");
            }
            
            $domain = $domainsData['hydra:member'][0]['domain'];
            $username = substr(str_shuffle("abcdefghijklmnopqrstuvwxyz0123456789"), 0, 12);
            $email = "{$username}@{$domain}";
            $password = "Pass" . rand(1000, 9999) . "!";

            $accountData = ["address" => $email, "password" => $password];
            $res = curl_request("$mailUrl/accounts", 'POST', $accountData, $mailHeaders);
            if ($res['code'] !== 201) emit_error("فشل إنشاء البريد المؤقت. الرمز: {$res['code']}");
            
            emit_log("✅ إيميل مؤقت: $email", "success");

            $res = curl_request("$mailUrl/token", 'POST', $accountData, $mailHeaders);
            if ($res['code'] !== 200) emit_error("فشل الحصول على توكن البريد.");
            $mailToken = json_decode($res['body'], true)['token'];

            emit_log("\n📝 جاري التسجيل في المنصة...", "info");
            $regPayload = [
                "email" => $email, "password" => "Gmail1234",
                "is_active" => true, "is_superuser" => false, "is_verified" => false
            ];
            $res = curl_request("https://api.presenton.ai/api/v1/auth/register", 'POST', $regPayload, $poHeaders);
            
            emit_log("\n🔐 طلب إرسال كود التحقق...", "info");
            $res = curl_request("https://api.presenton.ai/api/v1/auth/request-verify-token", 'POST', ["email" => $email], $poHeaders);
            
            emit_log("\n⏳ بانتظار وصول رسالة التحقق...", "warning");
            $mailAuthHeaders = ["Authorization" => "Bearer $mailToken"];
            
            $startTime = time();
            $messageData = null;
            while (time() - $startTime < 60) {
                $res = curl_request("$mailUrl/messages", 'GET', null, $mailAuthHeaders);
                $msgs = json_decode($res['body'], true);
                if (!empty($msgs['hydra:member'])) {
                    $msgId = $msgs['hydra:member'][0]['id'];
                    $resMsg = curl_request("$mailUrl/messages/$msgId", 'GET', null, $mailAuthHeaders);
                    $messageData = json_decode($resMsg['body'], true);
                    break;
                }
                sleep(3);
            }

            if (!$messageData) emit_error("انتهى الوقت ولم تصل رسالة التحقق.");

            $text = $messageData['text'] ?? '';
            if (preg_match('/token=([A-Za-z0-9\-_\.]+)/', $text, $matches)) {
                $verifyToken = $matches[1];
                
                emit_log("\n🔐 جاري تفعيل الحساب...", "info");
                $res = curl_request("https://api.presenton.ai/api/v1/auth/verify", 'POST', ["token" => $verifyToken], $poHeaders);
                
                emit_log("\n🔑 تسجيل الدخول...", "info");
                $loginData = ["username" => $email, "password" => "Gmail1234"];
                $poLoginHeaders = $poHeaders;
                $poLoginHeaders['Content-Type'] = 'application/x-www-form-urlencoded';
                
                $res = curl_request("https://api.presenton.ai/api/v1/auth/jwt/login", 'POST', $loginData, $poLoginHeaders, true);
                if ($res['code'] !== 200) emit_error("فشل تسجيل الدخول: {$res['body']}");
                
                $jwtToken = json_decode($res['body'], true)['access_token'];

                emit_log("\n⚙️ جاري إنشاء مفتاح API...", "info");
                $apiKeyHeaders = $poHeaders;
                $apiKeyHeaders['Authorization'] = "Bearer $jwtToken";
                
                $res = curl_request("https://api.presenton.ai/api/v1/auth/token/create", 'POST', null, $apiKeyHeaders);
                $apiKey = json_decode($res['body'], true)['token'] ?? '';
                if (!$apiKey) emit_error("فشل الحصول على مفتاح API.");
                
                emit_log("🎉 تم استخراج الـ API Key بنجاح. سيتم حفظه لاستخدامه لاحقاً.", "success");
                emit_auth($apiKey);

            } else {
                emit_error("لم يتم العثور على توكن في رسالة البريد.");
            }
        } else {
            emit_log("♻️ تم العثور على جلسة اتصال سابقة. سيتم تخطي التسجيل والبدء مباشرة...", "success");
        }

        emit_log("\n🚀 بدء توليد الملف بالإعدادات المحددة...", "info");
        $genHeaders = [
            "Authorization" => "Bearer $apiKey",
            "Content-Type" => "application/json"
        ];
        
        $res = curl_request("https://api.presenton.ai/api/v3/presentation/generate/async", 'POST', $payloadConfig, $genHeaders);
        
        // التحقق من صلاحية التوكن أو نفاد الرصيد
        if ($res['code'] == 401 || $res['code'] == 403) {
            emit_error("EXPIRED_TOKEN: انتهت صلاحية الجلسة المحفوظة.");
        } elseif ($res['code'] !== 200 && $res['code'] !== 201 && $res['code'] !== 202) {
             emit_error("INSUFFICIENT_CREDITS: {$res['body']}");
        }
        
        $taskData = json_decode($res['body'], true);
        $taskId = $taskData['id'] ?? null;
        if (!$taskId) emit_error("لم يتم العثور على معرف المهمة. الرد: {$res['body']}");
        emit_log("✅ تم إرسال طلب التوليد بنجاح!", "success");

        emit_log("\n⏳ يتم الآن المعالجة في الخلفية بصمت...", "warning");

        while (true) {
            $res = curl_request("https://api.presenton.ai/api/v3/async-task/status/$taskId", 'GET', null, $genHeaders);
            $statusData = json_decode($res['body'], true);
            $status = $statusData['status'] ?? 'unknown';

            if ($status === 'completed') {
                $downloadUrl = '';
                $possible_keys = ['url', 'download_url', 'file_url', 'file', 'link'];
                if (isset($statusData['data']) && is_array($statusData['data'])) {
                    foreach ($possible_keys as $k) {
                        if (!empty($statusData['data'][$k])) { $downloadUrl = $statusData['data'][$k]; break; }
                    }
                }

                if (empty($downloadUrl)) {
                    $json_str = json_encode($statusData['data'] ?? $statusData, JSON_UNESCAPED_SLASHES);
                    if (preg_match('/"(https?:\/\/[^"]+\.(?:pptx|pdf)[^"]*)"/i', $json_str, $matches)) {
                        $downloadUrl = $matches[1];
                    } 
                    elseif (preg_match('/"(https?:\/\/[^"]+)"/i', $json_str, $matches)) {
                        $downloadUrl = $matches[1];
                    }
                }

                if (!empty($downloadUrl)) {
                    emit_done($downloadUrl);
                } else {
                    emit_log(json_encode($statusData, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES), "info");
                    emit_error("العملية اكتملت! ابحث عن الرابط في النص المعروض.");
                }
                break;
            } elseif ($status === 'error' || $status === 'failed') {
                emit_error("فشل الإنشاء من المنصة: " . ($statusData['error'] ?? 'خطأ مجهول'));
                break;
            }

            sleep(5);
        }

    } catch (Exception $e) {
        emit_error($e->getMessage());
    }

    exit;
}
// ==========================================
//  الواجهة - Frontend (HTML/JS)
// ==========================================
?>
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <meta name="keywords" content="إنشاء عروض تقديمية, ذكاء اصطناعي, باوربوينت, تصميم عروض, برزنتيشن بالذكاء الاصطناعي, AI presentation maker, powerpoint generator, create slides with AI, pptx ai, pdf ai, دكتور محمد عربي">
    <meta name="description" content="أداة احترافية لإنشاء العروض التقديمية (باوربوينت) باستخدام الذكاء الاصطناعي في ثوانٍ.">
    <title>مُنشئ العروض التقديمية بالذكاء الاصطناعي</title>
    
    <meta name="theme-color" content="#0B0F19">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
    <meta name="apple-mobile-web-app-title" content="عروض AI">
    <meta name="mobile-web-app-capable" content="yes">
    <link rel="apple-touch-icon" href="https://cdn-icons-png.flaticon.com/512/888/888874.png">
    
    <link rel="manifest" href='data:application/manifest+json;charset=utf-8,%7B%22name%22%3A%22مُنشئ%20العروض%20الذكي%22%2C%22short_name%22%3A%22عروض%20AI%22%2C%22description%22%3A%22أداة%20د.محمد%20عربي%20لإنشاء%20العروض%20التقديمية%20(PowerPoint%20%26%20PDF)%20باستخدام%20الذكاء%20الاصطناعي.%22%2C%22start_url%22%3A%22%2Findex.php%22%2C%22display%22%3A%22standalone%22%2C%22background_color%22%3A%22%230B0F19%22%2C%22theme_color%22%3A%22%236366f1%22%2C%22orientation%22%3A%22portrait%22%2C%22icons%22%3A%5B%7B%22src%22%3A%22https%3A%2F%2Fcdn-icons-png.flaticon.com%2F512%2F888%2F888874.png%22%2C%22sizes%22%3A%22192x192%22%2C%22type%22%3A%22image%2Fpng%22%7D%2C%7B%22src%22%3A%22https%3A%2F%2Fcdn-icons-png.flaticon.com%2F512%2F888%2F888874.png%22%2C%22sizes%22%3A%22512x512%22%2C%22type%22%3A%22image%2Fpng%22%7D%5D%7D'>

    <script src="https://cdn.tailwindcss.com"></script>
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    
    <script>
        tailwind.config = {
            theme: {
                extend: {
                    colors: {
                        base: '#0B0F19',
                        panel: 'rgba(17, 24, 39, 0.65)',
                        primary: '#6366f1',
                        secondary: '#a855f7'
                    },
                    animation: {
                        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
                        'float': 'float 6s ease-in-out infinite',
                        'shine': 'shine 3s linear infinite',
                    },
                    keyframes: {
                        float: {
                            '0%, 100%': { transform: 'translateY(0)' },
                            '50%': { transform: 'translateY(-10px)' },
                        },
                        shine: {
                            to: { backgroundPosition: '200% center' }
                        }
                    }
                }
            }
        }
    </script>
    
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Tajawal:wght@400;500;700;900&display=swap');
        
        body { 
            font-family: 'Tajawal', sans-serif; 
            background-color: #0B0F19; 
            color: #e2e8f0; 
            background-image: 
                radial-gradient(circle at 15% 50%, rgba(99, 102, 241, 0.12), transparent 25%),
                radial-gradient(circle at 85% 30%, rgba(168, 85, 247, 0.12), transparent 25%);
            background-attachment: fixed;
        }

        .glass-panel { 
            background: rgba(15, 23, 42, 0.6); 
            backdrop-filter: blur(16px); 
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid rgba(255, 255, 255, 0.08); 
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
        }

        /* تحسين المدخلات لتكون دائماً بيضاء وواضحة */
        .input-premium {
            background: rgba(30, 41, 59, 0.8) !important;
            border: 1px solid rgba(255, 255, 255, 0.2);
            color: #ffffff !important;
            transition: all 0.3s ease;
        }
        .input-premium::placeholder {
            color: #94a3b8 !important; /* لون النص التوضيحي (الرمادي) */
        }
        .input-premium:focus {
            border-color: #a855f7;
            box-shadow: 0 0 0 2px rgba(168, 85, 247, 0.2);
            background: rgba(30, 41, 59, 1) !important;
            outline: none;
        }
        select.input-premium option { 
            background-color: #0f172a; 
            color: #ffffff; 
        }

        .terminal { font-family: 'Courier New', Courier, monospace; direction: ltr; text-align: left; }
        .terminal-log { margin: 0; padding: 3px 0; word-break: break-all; line-height: 1.4;}
        .log-success { color: #4ade80; }
        .log-error { color: #f87171; font-weight: bold;}
        .log-info { color: #93c5fd; }
        .log-warning { color: #fde047; }
        
        #console::-webkit-scrollbar { width: 6px; }
        #console::-webkit-scrollbar-track { background: transparent; }
        #console::-webkit-scrollbar-thumb { background: #475569; border-radius: 4px; }

        .shiny-text {
            background: linear-gradient(90deg, #e2e8f0 0%, #a855f7 30%, #3b82f6 50%, #a855f7 70%, #e2e8f0 100%);
            background-size: 200% auto;
            color: transparent;
            -webkit-background-clip: text;
            background-clip: text;
        }

        .btn-glow {
            background: linear-gradient(135deg, #6366f1 0%, #a855f7 100%);
            box-shadow: 0 0 20px rgba(168, 85, 247, 0.4);
            transition: all 0.3s ease;
        }
        .btn-glow:hover:not(:disabled) {
            transform: translateY(-2px);
            box-shadow: 0 0 30px rgba(168, 85, 247, 0.6);
        }
        .btn-glow:disabled {
            background: #475569;
            box-shadow: none;
            cursor: not-allowed;
            transform: none;
        }

        .settings-group {
            background: rgba(255, 255, 255, 0.02);
            border-radius: 12px;
            padding: 16px;
            border: 1px solid rgba(255,255,255,0.03);
        }
    </style>
</head>
<body class="min-h-screen p-4 md:p-6 lg:p-8 flex flex-col items-center justify-center relative overflow-x-hidden">

    <div class="max-w-4xl w-full flex flex-col gap-6 z-10 my-auto">
        
        <div class="glass-panel rounded-3xl p-6 md:p-8 relative overflow-hidden">
            <div class="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-blue-500 via-purple-500 to-pink-500"></div>

            <div class="flex flex-col md:flex-row items-center md:justify-between gap-4 mb-8 text-center md:text-right border-b border-white/10 pb-6">
                <div class="flex items-center gap-4 flex-col md:flex-row">
                    <div class="w-16 h-16 rounded-2xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center shadow-lg shadow-purple-500/30 animate-float">
                        <i class="fa-solid fa-brain text-3xl text-white"></i>
                    </div>
                    <div>
                        <h1 class="text-2xl md:text-3xl font-black text-white tracking-wide">مُنشئ العروض الذكي <span class="text-xs bg-purple-600/30 text-purple-300 px-2 py-1 rounded-full ml-2 border border-purple-500/30 align-middle">PRO</span></h1>
                        <p class="text-slate-400 mt-1 text-sm">قم بتوليد شرائح احترافية في ثوانٍ معدودة باستخدام الذكاء الاصطناعي</p>
                    </div>
                </div>
            </div>

            <form id="genForm" class="space-y-6">
                
                <div class="settings-group">
                    <h2 class="text-sm font-bold text-indigo-300 mb-3 flex items-center gap-2"><i class="fa-solid fa-pen-nib"></i> المحتوى الأساسي</h2>
                    <div class="space-y-4">
                        <div>
                            <label class="block text-sm text-slate-300 mb-1.5 ml-1">موضوع العرض (Prompt)</label>
                            <textarea id="topic" rows="2" class="w-full input-premium rounded-xl p-4 !text-white text-base leading-relaxed placeholder-slate-400" required placeholder="مثال: إنشاء عرض تقديمي من 5 شرائح عن تأثير الذكاء الاصطناعي على التعليم..."></textarea>
                        </div>
                        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                            <div>
                                <label class="block text-sm text-slate-300 mb-1.5 ml-1"><i class="fa-solid fa-layer-group text-slate-500 ml-1"></i> عدد الشرائح</label>
                                <input type="number" id="slides" value="5" min="1" max="50" oninput="if(this.value > 50) this.value = 50;" class="w-full input-premium !text-white rounded-xl p-3" required>
                            </div>
                            <div>
                                <label class="block text-sm text-slate-300 mb-1.5 ml-1"><i class="fa-solid fa-language text-slate-500 ml-1"></i> لغة العرض</label>
                                <select id="language" class="w-full input-premium !text-white rounded-xl p-3">
                                    <option value="Arabic" selected>العربية (Arabic)</option>
                                    <option value="English">الإنجليزية (English)</option>
                                    <option value="French">الفرنسية (French)</option>
                                </select>
                            </div>
                            <div>
                                <label class="block text-sm text-slate-300 mb-1.5 ml-1"><i class="fa-solid fa-comment-dots text-slate-500 ml-1"></i> نبرة النص (Tone)</label>
                                <select id="tone" class="w-full input-premium !text-white rounded-xl p-3">
                                    <option value="default">افتراضي (Default)</option>
                                    <option value="professional" selected>احترافي (Professional)</option>
                                    <option value="educational">تعليمي (Educational)</option>
                                    <option value="sales_pitch">عرض مبيعات (Sales Pitch)</option>
                                    <option value="casual">غير رسمي (Casual)</option>
                                    <option value="funny">فكاهي (Funny)</option>
                                </select>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="settings-group">
                    <h2 class="text-sm font-bold text-purple-300 mb-3 flex items-center gap-2"><i class="fa-solid fa-palette"></i> المظهر والتصميم</h2>
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label class="block text-sm text-slate-300 mb-1.5 ml-1">ثيم الألوان (Theme)</label>
                            <select id="theme" class="w-full input-premium !text-white rounded-xl p-3">
                                <option value="professional-blue" selected>أزرق احترافي</option>
                                <option value="professional-dark">داكن احترافي</option>
                                <option value="edge-yellow">حواف صفراء</option>
                                <option value="mint-blue">أزرق نعناعي</option>
                                <option value="light-rose">وردي فاتح</option>
                            </select>
                        </div>
                        <div>
                            <label class="block text-sm text-slate-300 mb-1.5 ml-1">القالب (Template)</label>
                            <select id="template" class="w-full input-premium !text-white rounded-xl p-3">
                                <option value="modern" selected>حديث (Modern)</option>
                                <option value="standard">قياسي (Standard)</option>
                                <option value="general">عام (General)</option>
                                <option value="swift">سريع (Swift)</option>
                            </select>
                        </div>
                        <div>
                            <label class="block text-sm text-slate-300 mb-1.5 ml-1">كثافة النص (Verbosity)</label>
                            <select id="verbosity" class="w-full input-premium !text-white rounded-xl p-3">
                                <option value="standard" selected>متوازن (Standard)</option>
                                <option value="concise">مختصر (Concise)</option>
                                <option value="text-heavy">مفصل (Text-Heavy)</option>
                            </select>
                        </div>
                    </div>
                </div>

                <div class="settings-group border-l-2 border-l-pink-500">
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label class="block text-sm text-slate-300 mb-1.5 ml-1"><i class="fa-regular fa-image text-pink-400 ml-1"></i> نوع الصور المرفقة</label>
                            <select id="image_type" class="w-full input-premium !text-white rounded-xl p-3">
                                <option value="stock" selected>صور جاهزة حقيقية (Stock Images)</option>
                                <option value="ai-generated">توليد بالذكاء الاصطناعي (AI Art)</option>
                            </select>
                        </div>
                        <div>
                            <label class="block text-sm text-slate-300 mb-1.5 ml-1"><i class="fa-solid fa-file-export text-pink-400 ml-1"></i> صيغة الملف (Export)</label>
                            <select id="export_as" class="w-full input-premium !text-white rounded-xl p-3 font-bold">
                                <option value="pptx" selected>باوربوينت (.PPTX)</option>
                                <option value="pdf">ملف بي دي إف (.PDF)</option>
                            </select>
                        </div>
                    </div>
                </div>

                <div class="mt-8">
                    <button type="submit" id="startBtn" class="w-full btn-glow text-white font-bold text-lg py-4 rounded-xl flex justify-center items-center gap-3">
                        <i class="fa-solid fa-wand-magic-sparkles text-xl"></i>
                        <span>البدء في بناء العرض التقديمي</span>
                    </button>
                    
                    <div id="dynamicMessage" class="hidden text-center mt-4 text-purple-300 font-medium transition-opacity duration-500 text-sm tracking-wide"></div>
                </div>

            </form>

            <div id="resultContainer" class="mt-6 hidden transition-all duration-500">
                <div class="p-6 bg-gradient-to-r from-emerald-900/40 to-teal-900/40 border border-emerald-500/50 rounded-2xl text-center shadow-[0_0_20px_rgba(16,185,129,0.15)] relative overflow-hidden">
                    <div class="absolute -top-10 -right-10 w-20 h-20 bg-emerald-500/20 rounded-full blur-2xl"></div>
                    
                    <div class="w-16 h-16 bg-emerald-500/20 rounded-full flex items-center justify-center mx-auto mb-4 border border-emerald-500/50">
                        <i class="fa-solid fa-check text-3xl text-emerald-400"></i>
                    </div>
                    
                    <h3 class="text-emerald-300 font-bold mb-5 text-xl">تهانينا! ملفك جاهز للتحميل</h3>
                    
                    <a id="downloadLink" href="#" target="_blank" class="inline-flex items-center justify-center w-full sm:w-auto gap-3 bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-3 px-10 rounded-xl transition-all shadow-lg hover:shadow-emerald-500/50 hover:-translate-y-1">
                        <i class="fa-solid fa-cloud-arrow-down text-xl"></i> <span id="downloadText">تحميل الملف الآن</span>
                    </a>
                </div>
            </div>
        </div>

        <div class="glass-panel rounded-2xl shadow-xl overflow-hidden border border-slate-700">
            <div id="terminalToggle" class="cursor-pointer flex items-center justify-between p-3 bg-slate-800/80 hover:bg-slate-700/80 transition select-none">
                <div class="flex gap-2 items-center px-2">
                    <div class="w-3 h-3 rounded-full bg-[#ff5f56] border border-[#e0443e]"></div>
                    <div class="w-3 h-3 rounded-full bg-[#ffbd2e] border border-[#dea123]"></div>
                    <div class="w-3 h-3 rounded-full bg-[#27c93f] border border-[#1aab29]"></div>
                    <span class="text-xs font-mono text-slate-400 ml-3">server-logs.sh</span>
                </div>
                <div class="flex items-center gap-2 text-slate-400 text-xs font-bold px-2">
                    <span id="terminalStatusText">عرض السجلات</span>
                    <i id="toggleIcon" class="fa-solid fa-chevron-down"></i>
                </div>
            </div>
            
            <div id="terminalBody" class="hidden border-t border-slate-700/50">
                <div id="console" class="terminal bg-[#0d1117] p-5 h-[280px] overflow-y-auto text-xs sm:text-sm whitespace-pre-wrap">
                    <p class="text-slate-600 font-mono">root@presenton-ai:~# systemctl status engine</p>
                    <p class="text-emerald-500/80 font-mono mt-1 mb-3">● engine.service - AI Presentation Generator is running</p>
                    <p class="text-slate-500">> في انتظار الأوامر لبدء المعالجة...</p>
                </div>
            </div>
        </div>

        <div class="text-center mt-2 mb-8">
            <h3 class="shiny-text animate-shine text-xl md:text-2xl font-black tracking-widest drop-shadow-lg">
                <i class="fa-solid fa-crown text-yellow-500 text-lg mr-2 drop-shadow-md"></i> مع تحيات د/محمد عربى
            </h3>
        </div>

    </div>

    <script>
        const consoleEl = document.getElementById('console');
        const startBtn = document.getElementById('startBtn');
        const form = document.getElementById('genForm');
        const resultContainer = document.getElementById('resultContainer');
        const downloadLink = document.getElementById('downloadLink');
        const downloadText = document.getElementById('downloadText');
        const dynamicMessage = document.getElementById('dynamicMessage');
        
        const terminalToggle = document.getElementById('terminalToggle');
        const terminalBody = document.getElementById('terminalBody');
        const toggleIcon = document.getElementById('toggleIcon');
        const terminalStatusText = document.getElementById('terminalStatusText');

        const inputIds = ['topic', 'slides', 'language', 'tone', 'theme', 'template', 'verbosity', 'image_type', 'export_as'];

        window.addEventListener('DOMContentLoaded', () => {
            inputIds.forEach(id => {
                const savedVal = localStorage.getItem('po_' + id);
                if (savedVal) document.getElementById(id).value = savedVal;
            });
        });

        const waitingMessagesList = [
            "🧠 يتم تحليل الموضوع واستخراج النقاط الرئيسية...",
            "⏳ عذراً على الإطالة، الذكاء الاصطناعي يبني العرض...",
            "🎨 يتم تنسيق الشرائح واختيار الألوان المناسبة...",
            "🖼️ يتم دمج الصور والنصوص وتجهيز الملف النهائي...",
            "🚀 أوشكنا على الانتهاء، شكراً لانتظارك..."
        ];
        let messageInterval;

        terminalToggle.addEventListener('click', () => {
            terminalBody.classList.toggle('hidden');
            if(terminalBody.classList.contains('hidden')){
                toggleIcon.classList.replace('fa-chevron-up', 'fa-chevron-down');
                terminalStatusText.textContent = "عرض السجلات";
            } else {
                toggleIcon.classList.replace('fa-chevron-down', 'fa-chevron-up');
                terminalStatusText.textContent = "إخفاء السجلات";
            }
        });

        function log(message, type = 'info') {
            const p = document.createElement('p');
            p.className = `terminal-log log-${type}`;
            const time = new Date().toLocaleTimeString('en-US', { hour12: false });
            
            let prefix = "•";
            if(type === 'success') prefix = "✔";
            if(type === 'error') prefix = "✖";
            if(type === 'warning') prefix = "⚠";

            p.textContent = `[${time}] ${prefix} ${message}`;
            consoleEl.appendChild(p);
            consoleEl.scrollTop = consoleEl.scrollHeight;
        }

        async function startGeneration() {
            inputIds.forEach(id => {
                localStorage.setItem('po_' + id, document.getElementById(id).value);
            });

            startBtn.disabled = true;
            startBtn.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin text-xl"></i> <span>جاري إنشاء العرض الاحترافي...</span>';
            consoleEl.innerHTML = ''; 
            resultContainer.classList.add('hidden');

            dynamicMessage.classList.remove('hidden');
            let msgIndex = 0;
            dynamicMessage.textContent = waitingMessagesList[msgIndex];
            messageInterval = setInterval(() => {
                msgIndex = (msgIndex + 1) % waitingMessagesList.length;
                dynamicMessage.style.opacity = 0; 
                setTimeout(() => {
                    dynamicMessage.textContent = waitingMessagesList[msgIndex];
                    dynamicMessage.style.opacity = 1;
                }, 300);
            }, 6000);

            const exportType = document.getElementById('export_as').value;
            const cachedApiKey = localStorage.getItem('po_api_key');

            const payloadConfig = {
                content: document.getElementById('topic').value,
                n_slides: document.getElementById('slides').value,
                language: document.getElementById('language').value,
                tone: document.getElementById('tone').value,
                theme: document.getElementById('theme').value,
                template: document.getElementById('template').value,
                verbosity: document.getElementById('verbosity').value,
                image_type: document.getElementById('image_type').value,
                export_as: exportType,
                api_key: cachedApiKey 
            };

            try {
                const response = await fetch('?api=1', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payloadConfig)
                });

                if (!response.ok) throw new Error("تعذر الاتصال بالخادم الرئيسي (Backend).");

                const reader = response.body.getReader();
                const decoder = new TextDecoder('utf-8');

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;
                    
                    const chunk = decoder.decode(value, { stream: true });
                    const lines = chunk.split('\n');
                    
                    for (let line of lines) {
                        if (!line.trim()) continue;
                        try {
                            const data = JSON.parse(line);
                            if (data.type === 'log') {
                                log(data.msg, data.level);
                            } 
                            else if (data.type === 'auth') {
                                localStorage.setItem('po_api_key', data.apiKey);
                            }
                            else if (data.type === 'done') {
                                log(`\n✅ اكتملت العملية بنجاح!`, "success");
                                downloadLink.href = data.url;
                                downloadText.textContent = `تحميل ملف (${exportType.toUpperCase()})`;
                                resultContainer.classList.remove('hidden');
                                clearInterval(messageInterval);
                                dynamicMessage.classList.add('hidden');
                            } 
                            else if (data.type === 'error') {
                                if(data.msg.includes('EXPIRED_TOKEN') || data.msg.includes('INSUFFICIENT_CREDITS') || data.msg.includes('Not enough credits')) {
                                    localStorage.removeItem('po_api_key'); 
                                    log("⚠ الجلسة انتهت أو نفد الرصيد... جاري تجديد الجلسة تلقائياً...", "warning");
                                    
                                    clearInterval(messageInterval);
                                    dynamicMessage.textContent = "جاري إنشاء حساب جديد لإنهاء العملية...";
                                    
                                    setTimeout(() => {
                                        startGeneration();
                                    }, 1000);
                                    
                                    return; 
                                } else {
                                    log(`❌ خطأ من الخادم: ${data.msg}`, "error");
                                    dynamicMessage.textContent = "حدث خطأ غير متوقع. يرجى مراجعة سجلات النظام (Terminal).";
                                    clearInterval(messageInterval);
                                    dynamicMessage.classList.replace('text-purple-300', 'text-red-400');
                                }
                            }
                        } catch (err) {
                            console.error("خطأ تقني في قراءة الاستجابة:", err, line);
                        }
                    }
                }

            } catch (error) {
                log(`\n❌ توقف النظام: ${error.message}`, "error");
                clearInterval(messageInterval);
                dynamicMessage.classList.add('hidden');
            } finally {
                if (!localStorage.getItem('po_api_key') === null || document.getElementById('dynamicMessage').textContent !== "جاري إنشاء حساب جديد لإنهاء العملية...") {
                    startBtn.disabled = false;
                    startBtn.innerHTML = '<i class="fa-solid fa-wand-magic-sparkles text-xl"></i> <span>البدء في بناء العرض التقديمي</span>';
                    startBtn.classList.remove('bg-slate-600');
                }
            }
        }

        form.addEventListener('submit', (e) => {
            e.preventDefault();
            startGeneration();
        });
    </script>
</body>
</html>