package com.example.autoclickerpro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * فاز ۸ — تاریخچه‌ی اجرای قوانین (چه واقعی، چه در حالت آزمایشی). هر بار
 * ClickAccessibilityService.performAction اجرا بشه، یک ردیف اینجا اضافه
 * می‌شه. برای اینکه SharedPreferences بی‌نهایت بزرگ نشه، فقط MAX_ENTRIES
 * ردیفِ آخر نگه‌داشته می‌شه (قدیمی‌ترها خودکار حذف می‌شن).
 *
 * باگ کارایی رفع‌شده: برخلاف ScenarioStore/RecordingStore (که یک لیستِ
 * درون‌حافظه‌ای رو یک‌بار می‌سازن و نگه می‌دارن)، این کلاس قبلاً هیچ کشی
 * نداشت — هر add() کل آرایه‌ی JSON رو از SharedPreferences می‌خوند، پارس
 * می‌کرد، یک ردیف اضافه می‌کرد، و کل آرایه رو (تا ۳۰۰ ردیف) دوباره
 * serialize و بازنویسی می‌کرد. چون add() از performAction — یعنی روی ترد
 * اصلی، دقیقاً همون لحظه‌ی هر کلیک/سوایپ واقعی یا آزمایشی — صدا زده می‌شه،
 * برای سناریوهایی با فاصله‌ی حلقه‌ی کم (مثلاً زیر ۵۰۰ میلی‌ثانیه) و چند
 * قانون فعال، این read+parse+write مکرر می‌تونست یک سربار کوچک ولی
 * پیوسته روی ترد UI بذاره. حالا (درست مثل بقیه‌ی store ها) یک لیستِ
 * درون‌حافظه‌ای یک‌بار در اولین استفاده لود می‌شه و add() فقط همون رو
 * append می‌کنه؛ نوشتن روی دیسک هنوز هر بار انجام می‌شه (چون تاریخچه باید
 * بین اجراهای اپ هم بمونه) ولی دیگه نیازی به خوندن/پارسِ دوباره‌ی هرچیزی
 * از SharedPreferences در هر add() نیست.
 */
object RunLogStore {
    private const val PREFS = "autoclicker_prefs"
    private const val KEY_LOG = "run_log_json"
    private const val MAX_ENTRIES = 300

    private val entries = mutableListOf<RunLogEntry>()
    private var initialized = false

    @Synchronized
    private fun ensureInit(context: Context) {
        if (initialized) return
        initialized = true
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LOG, null) ?: return
        runCatching {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                entries.add(entryFromJson(arr.getJSONObject(i)))
            }
        }
    }

    @Synchronized
    fun add(context: Context, entry: RunLogEntry) {
        ensureInit(context)
        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            // فقط MAX_ENTRIES تای آخر نگه داشته می‌شه؛ قدیمی‌ترها از ابتدای لیست حذف می‌شن.
            repeat(entries.size - MAX_ENTRIES) { entries.removeAt(0) }
        }
        persist(context)
    }

    @Synchronized
    fun getAll(context: Context): List<RunLogEntry> {
        ensureInit(context)
        return entries.toList()
    }

    @Synchronized
    fun clear(context: Context) {
        ensureInit(context)
        entries.clear()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOG).apply()
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        for (e in entries) arr.put(entryToJson(e))
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LOG, arr.toString()).apply()
    }

    private fun entryToJson(e: RunLogEntry): JSONObject = JSONObject().apply {
        put("timestampMs", e.timestampMs)
        put("ruleLabel", e.ruleLabel)
        put("matchType", e.matchType.name)
        put("actionType", e.actionType.name)
        put("x", e.x.toDouble())
        put("y", e.y.toDouble())
        put("dryRun", e.dryRun)
    }

    private fun entryFromJson(obj: JSONObject): RunLogEntry = RunLogEntry(
        timestampMs = obj.getLong("timestampMs"),
        ruleLabel = obj.getString("ruleLabel"),
        matchType = MatchType.valueOf(obj.getString("matchType")),
        actionType = ActionType.valueOf(obj.getString("actionType")),
        x = obj.getDouble("x").toFloat(),
        y = obj.getDouble("y").toFloat(),
        dryRun = obj.optBoolean("dryRun", false)
    )
}
