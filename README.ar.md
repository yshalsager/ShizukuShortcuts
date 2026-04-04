# اختصارات شيزوكو

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84)](https://developer.android.com/)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-2ea44f)](https://developer.android.com/about/versions/android-8.0)
[![Shizuku](https://img.shields.io/badge/powered%20by-Shizuku-4f46e5)](https://github.com/RikkaApps/Shizuku)

اختصارات صغيرة، ويدجت للشاشة الرئيسية، وإجراءات shell مخصصة على أندرويد عبر Shizuku.

الإجراءات المدمجة الحالية:

- فتح الإشعارات
- فتح الإعدادات السريعة

يمكن تشغيلها مباشرة من الشاشة الرئيسية المختصرة عبر `جرّب` أو تثبيتها عبر `ثبّت` أو إضافتها كويدجت، كما يمكن إضافة إجراءات shell محلية مخصصة مثل `cmd statusbar expand-notifications` من دون بادئة `adb shell`.

> [!CAUTION]
> تم تطوير هذا المشروع مع اعتماد كبير على مساعدات الذكاء الاصطناعي، بما في ذلك OpenAI Codex.

## لقطات الشاشة

<table>
  <thead>
    <tr>
      <th align="left">English</th>
      <th align="left">Arabic (RTL)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>
        <div><strong>Home</strong> (actions + status)</div>
        <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01_home.png" alt="Home (EN)" style="max-width: 100%; height: auto;" />
      </td>
      <td>
        <div><strong>Home</strong> (الإجراءات والحالة)</div>
        <img src="fastlane/metadata/android/ar/images/phoneScreenshots/01_home.png" alt="Home (AR)" style="max-width: 100%; height: auto;" />
      </td>
    </tr>
  </tbody>
</table>

## ماذا يفعل هذا التطبيق؟

- يفتح لوحة الإشعارات عبر `cmd statusbar expand-notifications`
- يفتح الإعدادات السريعة عبر `cmd statusbar expand-settings`
- يستخدم fallback لـ `service call statusbar 1` عند الحاجة لفتح الإشعارات على بعض الرومات الأقدم
- يتيح إضافة إجراءات shell محلية مخصصة تعمل عبر نفس خدمة مستخدم Shizuku
- يحتفظ بالاختصارات الثابتة للإجراءات المدمجة وينشر اختصارات ديناميكية للإجراءات المخصصة في قائمة الضغط المطول
- يدعم الاختصارات المثبّتة لكل من الإجراءات المدمجة والمخصصة
- يدعم ويدجت موحّداً للشاشة الرئيسية: يمكن ضبط كل ويدجت على إجراء واحد (مدمج أو مخصص)
- يمرر نقرات الويدجت عبر `ShortcutDispatchActivity` ويعرض إعادة ضبط إذا حُذف الإجراء المخصص المرتبط
- يعرض حالة Shizuku وحالة الإذن على شكل شرائح حالة مختصرة
- يتيح `جرّب` و`عدّل` و`ثبّت` و`حذف` للإجراءات المخصصة من الشاشة الرئيسية
- يدعم الألوان الديناميكية على Android 12+ مع ألوان بديلة على الإصدارات الأقدم
- يدعم الإنجليزية والعربية وواجهات RTL
- يعتمد على إعدادات لغة التطبيق في أندرويد، وليس على منتقي لغة داخل التطبيق

## المتطلبات

- Android `minSdk 26`
- تثبيت Shizuku وتشغيله
- منح إذن Shizuku لهذا التطبيق

## المعمارية

المكونات الأساسية:

- `MainActivity`: شاشة رئيسية مختصرة مبنية بـ Compose مع شرائح حالة وتعليمات داخلية وصفوف إجراءات
- `ShortcutDispatchActivity`: Activity شفافة لاختصارات المشغّل
- `AppShizukuManager`: حالة البايندر وطلب الإذن وربط خدمة المستخدم
- `PrivilegedStatusBarService`: خدمة مستخدم Shizuku على شكل Binder
- `AppCustomActionsRepository`: تخزين محلي للإجراءات المخصصة داخل SharedPreferences بصيغة JSON واحدة
- `ActionCatalog` و `DynamicShortcutSync`: دمج كل الإجراءات ونشر الاختصارات الديناميكية للمخصصة
- `ActionPerformer`: تنفيذ الأوامر مع منطق الـ fallback
- `ActionWidgetProvider` و`ActionWidgetConfigureActivity` و`ActionWidgetRenderer` و`WidgetBindingsRepository`: دورة حياة الويدجت وواجهة الاختيار والرسم وربط كل ويدجت بإجراء

مسار التنفيذ:

1. يضغط المستخدم `جرّب` أو يشغّل اختصاراً ثابتاً أو ديناميكياً أو مثبّتاً أو يضغط ويدجتاً مضبوطاً
2. يتحقق التطبيق من Shizuku ومن الإذن
3. يربط التطبيق خدمة المستخدم عبر Shizuku
4. تنفذ خدمة المستخدم أمراً مدمجاً بصيغة argv أو أمر shell مخصص عبر `sh -c`
5. ينتهي التنفيذ بصمت أو تظهر رسالة قصيرة عند الفشل

تفاصيل التنفيذ موجودة في [docs/implementation-walkthrough.md](/Users/yshalsager/tmp/research/shizuku-shortcuts/docs/implementation-walkthrough.md).

## البناء

هذا المشروع يستخدم [mise](https://mise.jdx.dev/) لإدارة الأدوات.

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests
./gradlew :app:testDebugUnitTest

# Build Android test APK
./gradlew :app:assembleDebugAndroidTest
```

## Fastlane و metadata

يتضمن المستودع:

- `fastlane/metadata/android/en-US`
- `fastlane/metadata/android/ar`
- لقطات الشاشة ضمن `fastlane/metadata/android/*/images/phoneScreenshots`
- مسارات fastlane للتحقق من metadata والتقاط الصور والرفع إلى Google Play

أوامر مفيدة:

```bash
# Install fastlane
bundle install

# Validate fastlane metadata
bundle exec fastlane android validate_metadata

# Capture screenshots
bundle exec fastlane android capture_screenshots
```

## CI

يوجد في المستودع:

- `ci.yml`: اختبارات وحدات وبناء debug وrelease والتحقق من metadata
- `screenshots.yml`: التقاط لقطات الشاشة على محاكي
- `release.yml`: بناء APK للإصدار وإرفاقه مع GitHub Releases

## الرخصة

GPL-3.0-only. راجع [LICENSE](/Users/yshalsager/tmp/research/shizuku-shortcuts/LICENSE).
