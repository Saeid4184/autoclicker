package com.example.autoclickerpro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * فاز ۳ — ذخیره‌سازی تصاویر الگو (template) برای قوانین IMAGE.
 * فایل‌ها به‌صورت PNG در پوشه‌ی داخلی اپ (filesDir/templates) نگه‌داری می‌شن؛
 * فقط مسیر فایل در ClickRule.matchValue ذخیره می‌شه، نه خودِ تصویر (که حجم
 * SharedPreferences رو زیاد نکنه).
 */
object TemplateStore {
    private const val TAG = "TemplateStore"
    private const val DIR_NAME = "templates"

    // کش کوچیک تا هر تکرار حلقه‌ی کلیک، فایل رو دوباره از دیسک دیکد نکنیم.
    private val cache = LinkedHashMap<String, Bitmap>()
    private const val MAX_CACHE = 20

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** یک بیت‌مپ کراپ‌شده رو ذخیره می‌کنه و مسیر فایل رو برمی‌گردونه. */
    fun saveTemplate(context: Context, bitmap: Bitmap): String {
        val file = File(dir(context), "template_${UUID.randomUUID()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    @Synchronized
    fun loadTemplate(context: Context, path: String): Bitmap? {
        cache[path]?.let { return it }
        return runCatching {
            val bmp = BitmapFactory.decodeFile(path) ?: return null
            if (cache.size >= MAX_CACHE) {
                val oldestKey = cache.keys.firstOrNull()
                if (oldestKey != null) cache.remove(oldestKey)
            }
            cache[path] = bmp
            bmp
        }.onFailure { Log.e(TAG, "خطا در بارگذاری الگو از $path", it) }.getOrNull()
    }

    fun deleteTemplate(path: String) {
        cache.remove(path)
        runCatching { File(path).delete() }
    }

    /**
     * باگ رفع‌شده: تا قبل از این، حذفِ یک قانون IMAGE (یا کل سناریو) فقط
     * فایلِ rule.matchValue (الگوی اولویت ۱) رو پاک می‌کرد، نه فایل‌های
     * rule.imageTemplatePaths (الگوهای اضافه‌ی فاز ۹). یعنی هر قانون تصویری
     * که چند الگوی اولویت‌بندی‌شده داشت، با حذف شدن، فایل‌های الگوهای
     * اضافه‌اش برای همیشه در حافظه‌ی داخلی اپ باقی می‌موندن (leak) — هیچ‌وقت
     * پاک نمی‌شدن، حتی با حذف قانون/سناریو یا اونجایی که ازش استفاده می‌شد.
     * حالا همه‌جا که یک قانون IMAGE حذف می‌شه، این تابع (نه deleteTemplate
     * تنها) باید صدا زده بشه تا هم matchValue هم همه‌ی imageTemplatePaths
     * پاک بشن.
     */
    fun deleteAllTemplatesFor(rule: ClickRule) {
        if (rule.matchType != MatchType.IMAGE) return
        deleteTemplate(rule.matchValue)
        for (path in rule.imageTemplatePaths) deleteTemplate(path)
    }

    /**
     * یک فایل الگوی موجود رو با نام جدید کپی می‌کنه و مسیر فایل جدید رو
     * برمی‌گردونه (یا null اگه فایل اصلی موجود نباشه). این برای رفع یک باگ
     * لازم شد: قبلاً وقتی یک سناریو کپی می‌شد (duplicateScenario)، قوانین
     * IMAGE کپی‌شده هنوز به همون مسیر فایل تصویر سناریوی اصلی اشاره می‌کردن؛
     * پس با حذف قانون/سناریوی اصلی، فایل تصویر پاک می‌شد و قانون IMAGE در
     * سناریوی کپی‌شده هم برای همیشه از کار می‌افتاد (بدون کرش، فقط ساکت).
     */
    fun copyTemplate(context: Context, originalPath: String): String? {
        val source = File(originalPath)
        if (!source.exists()) return null
        val dest = File(dir(context), "template_${UUID.randomUUID()}.png")
        return runCatching {
            source.copyTo(dest, overwrite = true)
            dest.absolutePath
        }.onFailure { Log.e(TAG, "خطا در کپی الگو از $originalPath", it) }.getOrNull()
    }
}
