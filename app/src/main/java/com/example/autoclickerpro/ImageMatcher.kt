package com.example.autoclickerpro

import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MinMaxLocResult
import org.opencv.imgproc.Imgproc

/**
 * فاز ۳ — تشخیص تصویری با OpenCV.
 *
 * این کلاس تنها یک کار می‌کنه: گرفتن آخرین فریم صفحه (از ScreenCaptureService)
 * و یک تصویر الگو (template که کاربر قبلاً کراپ کرده)، و پیدا کردن اینکه آیا
 * الگو در فریم فعلی حضور داره یا نه — با Imgproc.matchTemplate.
 *
 * چرا matchTemplate و نه چیز پیچیده‌تر (feature matching و امثالش)؟ چون برای
 * "پیدا کردن یک آیکون/دکمه‌ی ثابت در صفحه" که چرخش/مقیاس نداره (که حالت رایج
 * دکمه‌های UI هست)، matchTemplate هم سریع‌تره و هم برای این کار کافیه.
 */
object ImageMatcher {
    private const val TAG = "ImageMatcher"

    @Volatile
    private var loaded = false

    /** باید قبل از اولین استفاده صدا زده بشه (مثلاً در Application.onCreate یا اولین بار لازم). */
    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        loaded = OpenCVLoader.initLocal()
        if (!loaded) {
            Log.e(TAG, "بارگذاری OpenCV ناموفق بود")
        }
        return loaded
    }

    data class MatchResult(val center: PointF, val confidence: Double)

    /**
     * دنبال [template] داخل [screenFrame] می‌گرده.
     * اگه بهترین تطابق >= threshold باشه، مرکز محل match (به مختصات همون
     * screenFrame که مستقیماً معادل مختصات صفحه‌ست) برگردونده می‌شه؛ وگرنه null.
     */
    fun findTemplate(screenFrame: Bitmap, template: Bitmap, threshold: Double): MatchResult? {
        if (!ensureLoaded()) return null
        if (template.width > screenFrame.width || template.height > screenFrame.height) {
            Log.w(TAG, "الگو از خودِ فریم صفحه بزرگ‌تره")
            return null
        }

        val sceneMat = Mat()
        val templateMat = Mat()
        val resultMat = Mat()
        try {
            Utils.bitmapToMat(screenFrame, sceneMat)
            Utils.bitmapToMat(template, templateMat)

            // برای سرعت و مقاومت در برابر تفاوت‌های جزئی رنگ (فشرده‌سازی صفحه و
            // غیره)، هر دو تصویر رو به grayscale تبدیل می‌کنیم.
            Imgproc.cvtColor(sceneMat, sceneMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(templateMat, templateMat, Imgproc.COLOR_RGBA2GRAY)

            val resultCols = sceneMat.cols() - templateMat.cols() + 1
            val resultRows = sceneMat.rows() - templateMat.rows() + 1
            if (resultCols <= 0 || resultRows <= 0) return null
            resultMat.create(resultRows, resultCols, CvType.CV_32FC1)

            // TM_CCOEFF_NORMED: خروجی بین -1 و 1 نرمالایز شده، مقدار نزدیک ۱
            // یعنی تطابق تقریباً کامل. نسبت به روشنایی کلی صحنه حساسیت کمی داره
            // که برای صفحه‌ی گوشی (که روشنایی زیاد تغییر نمی‌کنه) مناسبه.
            Imgproc.matchTemplate(sceneMat, templateMat, resultMat, Imgproc.TM_CCOEFF_NORMED)

            val mmr: MinMaxLocResult = Core.minMaxLoc(resultMat)
            val confidence = mmr.maxVal
            if (confidence < threshold) return null

            val topLeft = mmr.maxLoc
            val centerX = (topLeft.x + templateMat.cols() / 2.0).toFloat()
            val centerY = (topLeft.y + templateMat.rows() / 2.0).toFloat()
            return MatchResult(PointF(centerX, centerY), confidence)
        } catch (t: Throwable) {
            Log.e(TAG, "خطا در matchTemplate", t)
            return null
        } finally {
            sceneMat.release()
            templateMat.release()
            resultMat.release()
        }
    }

    /** نتیجه‌ی یک تطابق در میان چند الگوی اولویت‌بندی‌شده (فاز ۹). */
    data class PriorityMatchResult(val center: PointF, val confidence: Double, val matchedIndex: Int, val matchedPath: String)

    /**
     * فاز ۹ — چند الگوی تصویری با اولویت: [templates] به ترتیب اولویت (ایندکس
     * ۰ = بالاترین اولویت) چک می‌شن؛ اولین موردی که با [threshold] تطابق پیدا
     * کنه برگردونده می‌شه و باقی الگوها اصلاً چک نمی‌شن (برای سرعت). مثال
     * کاربردی: اول دنبال دکمه‌ی «رد کردن تبلیغ» بگرد، اگه نبود دنبال دکمه‌ی
     * اصلیِ بازی — نه برعکس.
     */
    fun findFirstMatchingTemplate(
        screenFrame: Bitmap,
        templates: List<Pair<String, Bitmap>>,
        threshold: Double
    ): PriorityMatchResult? {
        for ((index, entry) in templates.withIndex()) {
            val (path, bitmap) = entry
            val match = findTemplate(screenFrame, bitmap, threshold)
            if (match != null) {
                return PriorityMatchResult(match.center, match.confidence, index, path)
            }
        }
        return null
    }
}
