package com.example.autoclickerpro

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.autoclickerpro.databinding.ActivityRulesBinding

/**
 * صفحه‌ای برای دیدن همه‌ی قوانین ذخیره‌شده، روشن/خاموش کردن هرکدوم،
 * حذف کردن، و تنظیم فاصله‌ی زمانی حلقه‌ی بررسی.
 * ساخت قانون جدید از طریق دکمه شناور (OverlayService) انجام می‌شه، نه اینجا،
 * چون برای تشخیص المان باید همزمان اپ هدف روی صفحه باز باشه.
 */
class RulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRulesBinding
    private lateinit var adapter: RuleListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // مستقیم از ScenarioStore می‌خونیم (نه RuleStore.loopIntervalMs) چون اون
        // فقط بعد از یه فراخوانی getAll/getEnabled به‌روز می‌شه.
        binding.etInterval.setText(ScenarioStore.getLoopIntervalMs(this).toString())
        binding.btnSaveInterval.setOnClickListener {
            val ms = binding.etInterval.text.toString().toLongOrNull()
            if (ms == null || ms < 200) {
                Toast.makeText(this, "حداقل ۲۰۰ میلی‌ثانیه", Toast.LENGTH_SHORT).show()
            } else {
                RuleStore.setLoopIntervalMs(this, ms)
                Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }

        adapter = RuleListAdapter()
        binding.listRules.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        binding.tvScreenTitle.text = "قوانین سناریوی «${ScenarioStore.getActiveScenario(this).name}»"
        adapter.refresh()
    }

    private inner class RuleListAdapter : BaseAdapter() {
        private var rules: List<ClickRule> = emptyList()

        fun refresh() {
            rules = RuleStore.getAll(this@RulesActivity)
            notifyDataSetChanged()
        }

        override fun getCount() = rules.size
        override fun getItem(position: Int) = rules[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@RulesActivity)
                .inflate(R.layout.item_rule, parent, false)

            val rule = rules[position]
            val tvLabel = view.findViewById<TextView>(R.id.tvRuleLabel)
            val switchEnabled = view.findViewById<Switch>(R.id.switchEnabled)
            val btnDelete = view.findViewById<android.widget.Button>(R.id.btnDeleteRule)
            val btnMoveUp = view.findViewById<android.widget.Button>(R.id.btnMoveUp)
            val btnMoveDown = view.findViewById<android.widget.Button>(R.id.btnMoveDown)
            val btnAdvanced = view.findViewById<android.widget.Button>(R.id.btnAdvanced)

            // شماره‌ی ترتیب رو جلوی برچسب نشون می‌دیم تا اولویت اجرا در حلقه واضح باشه
            tvLabel.text = "${position + 1}. ${rule.describe()}"

            btnMoveUp.isEnabled = position > 0
            btnMoveDown.isEnabled = position < rules.size - 1
            btnMoveUp.setOnClickListener {
                RuleStore.moveUp(this@RulesActivity, rule.id)
                refresh()
            }
            btnMoveDown.setOnClickListener {
                RuleStore.moveDown(this@RulesActivity, rule.id)
                refresh()
            }

            // جلوگیری از فراخوانی listener هنگام recycle شدن view توسط ListView
            switchEnabled.setOnCheckedChangeListener(null)
            switchEnabled.isChecked = rule.enabled
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                RuleStore.setEnabled(this@RulesActivity, rule.id, isChecked)
            }

            btnDelete.setOnClickListener {
                // هر دو فایل: الگوی اصلی و همه‌ی الگوهای اضافه‌ی اولویت‌بندی‌شده
                // (فاز ۹) پاک می‌شن — نه فقط matchValue.
                TemplateStore.deleteAllTemplatesFor(rule)
                RuleStore.remove(this@RulesActivity, rule.id)
                refresh()
            }

            btnAdvanced.setOnClickListener {
                showAdvancedSettingsDialog(rule) { refresh() }
            }

            return view
        }
    }

    /**
     * فاز ۹ — دیالوگ «تنظیمات پیشرفته»ی یک قانون: زنجیره‌ی شرطی (اگه X دیدی
     * → Y، وگرنه → Z)، تاخیر تصادفی بین کلیک‌ها، «صبر تا ناپدیدی» بعد از
     * کلیک، و فیلدهای اختصاصیِ PIXEL_COLOR/OCR_TEXT (تحمل رنگ / نقطه‌ی کلیک
     * OCR). چون این فیلدها به همه‌ی انواع قانون ربط نداره، فقط فیلدهای
     * مرتبط با matchType همون قانون نشون داده می‌شن.
     *
     * View با کد ساخته می‌شه (نه یک layout XML جدا) تا این فایل خودکفا بمونه؛
     * برای یک دیالوگِ ساده‌ی مبتنی بر فرم، این ساده‌تر از اضافه‌کردن چند
     * فایل XML جدیده.
     */
    private fun showAdvancedSettingsDialog(rule: ClickRule, onSaved: () -> Unit) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(0))
        }

        fun addLabel(text: String) {
            root.addView(TextView(this@RulesActivity).apply {
                this.text = text
                setPadding(0, dp(12), 0, dp(2))
            })
        }

        // ---------- زنجیره‌ی شرطی: وگرنه کدوم قانون؟ ----------
        val otherRules = RuleStore.getAll(this).filter { it.id != rule.id }
        val elseOptions = listOf("— هیچ‌کدام (بدون زنجیره) —") + otherRules.map { it.describe() }
        addLabel("زنجیره‌ی شرطی: اگه شرط این قانون برقرار نبود، وگرنه کدوم قانون اجرا بشه؟")
        val elseSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@RulesActivity, android.R.layout.simple_spinner_dropdown_item, elseOptions)
            val currentIdx = otherRules.indexOfFirst { it.id == rule.elseRuleId }
            setSelection(if (currentIdx >= 0) currentIdx + 1 else 0)
        }
        root.addView(elseSpinner)

        // ---------- تاخیر تصادفی بین کلیک‌ها ----------
        addLabel("تاخیر تصادفی اضافه بعد از این قانون (میلی‌ثانیه) — برای طبیعی‌تر شدن الگوی کلیک:")
        val delayRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val etDelayMin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "حداقل (مثلاً ۰)"
            setText(rule.postClickDelayMinMs.toString())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val etDelayMax = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "حداکثر (مثلاً ۸۰۰)"
            setText(rule.postClickDelayMaxMs.toString())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        delayRow.addView(etDelayMin)
        delayRow.addView(etDelayMax)
        root.addView(delayRow)

        // ---------- صبر تا ناپدیدی بعد از کلیک ----------
        val cbWait = CheckBox(this).apply {
            text = "بعد از کلیک، صبر کن تا المان/الگو/رنگ/متن ناپدید بشه"
            isChecked = rule.waitForDisappearAfterClick
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(cbWait)
        val etWaitTimeout = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "سقف انتظار (میلی‌ثانیه، مثلاً ۵۰۰۰)"
            setText(rule.waitForDisappearTimeoutMs.toString())
        }
        root.addView(etWaitTimeout)

        // ---------- فیلدهای اختصاصی PIXEL_COLOR ----------
        var etColorTolerance: EditText? = null
        if (rule.matchType == MatchType.PIXEL_COLOR) {
            addLabel("تحمل اختلاف رنگ (۰ تا ۲۵۵ در هر کانال؛ رنگ هدف فعلی: ${rule.targetColorHex}):")
            etColorTolerance = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(rule.colorTolerance.toString())
            }
            root.addView(etColorTolerance)
        }

        // ---------- فیلد اختصاصی OCR_TEXT ----------
        var etOcrClickPoint: EditText? = null
        var cbOcrPreprocess: CheckBox? = null
        if (rule.matchType == MatchType.OCR_TEXT) {
            addLabel("نقطه‌ی کلیک وقتی متن پیدا شد (\"x,y\") — خالی = مرکز ناحیه‌ی OCR:")
            etOcrClickPoint = EditText(this).apply {
                hint = "مثلاً 540,1200"
                setText(rule.ocrClickPoint ?: "")
            }
            root.addView(etOcrClickPoint)

            cbOcrPreprocess = CheckBox(this).apply {
                text = "پیش‌پردازش (خاکستری‌سازی+کنتراست+بزرگ‌نمایی) — برای فونت‌های بازی/Unity توصیه می‌شه"
                isChecked = rule.ocrPreprocess
                setPadding(0, dp(12), 0, 0)
            }
            root.addView(cbOcrPreprocess)
        }

        val scroll = ScrollView(this).apply { addView(root) }

        AlertDialog.Builder(this)
            .setTitle("تنظیمات پیشرفته — ${rule.label}")
            .setView(scroll)
            .setPositiveButton("ذخیره") { _, _ ->
                val selectedIdx = elseSpinner.selectedItemPosition
                val newElseId = if (selectedIdx <= 0) null else otherRules[selectedIdx - 1].id

                val minDelay = etDelayMin.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val maxDelay = etDelayMax.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val waitTimeout = etWaitTimeout.text.toString().toLongOrNull()?.coerceAtLeast(0L)
                    ?: rule.waitForDisappearTimeoutMs

                var updated = rule.copy(
                    elseRuleId = newElseId,
                    postClickDelayMinMs = minOf(minDelay, maxDelay),
                    postClickDelayMaxMs = maxOf(minDelay, maxDelay),
                    waitForDisappearAfterClick = cbWait.isChecked,
                    waitForDisappearTimeoutMs = waitTimeout
                )
                if (etColorTolerance != null) {
                    val tol = etColorTolerance.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: rule.colorTolerance
                    updated = updated.copy(colorTolerance = tol)
                }
                if (etOcrClickPoint != null) {
                    val point = etOcrClickPoint.text.toString().trim().ifBlank { null }
                    updated = updated.copy(ocrClickPoint = point)
                }
                if (cbOcrPreprocess != null) {
                    updated = updated.copy(ocrPreprocess = cbOcrPreprocess.isChecked)
                }

                RuleStore.update(this, updated)
                Toast.makeText(this, "تنظیمات پیشرفته ذخیره شد", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }
}
