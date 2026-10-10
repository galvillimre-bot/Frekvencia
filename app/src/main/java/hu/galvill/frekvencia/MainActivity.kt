package hu.galvill.frekvencia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

class MainActivity : Activity() {

    private data class Preset(val f: Double, val label: String, val title: String, val mode: Mode)
    private data class Group(val title: String, val items: List<Preset>)

    private val groups = listOf(
        Group("Schumann-rezonancia", listOf(
            Preset(7.83, "Alapfrekvencia", "Schumann alapfrekvencia", Mode.BINAURAL),
            Preset(14.3, "2. felharmonikus", "Schumann 2. felharmonikus", Mode.BINAURAL),
            Preset(20.8, "3. felharmonikus", "Schumann 3. felharmonikus", Mode.BINAURAL),
            Preset(27.3, "4. felharmonikus", "Schumann 4. felharmonikus", Mode.BINAURAL),
            Preset(33.8, "5. felharmonikus", "Schumann 5. felharmonikus", Mode.BINAURAL),
        )),
        Group("Solfeggio", listOf(
            Preset(174.0, "Nyugalom", "Solfeggio – nyugalom", Mode.PURE),
            Preset(285.0, "Regeneráció", "Solfeggio – regeneráció", Mode.PURE),
            Preset(396.0, "Félelem oldása", "Solfeggio – félelem oldása", Mode.PURE),
            Preset(417.0, "Változás", "Solfeggio – változás", Mode.PURE),
            Preset(528.0, "„Szeretet” hang", "Solfeggio – „szeretet” hang", Mode.PURE),
            Preset(639.0, "Kapcsolatok", "Solfeggio – kapcsolatok", Mode.PURE),
            Preset(741.0, "Tisztánlátás", "Solfeggio – tisztánlátás", Mode.PURE),
            Preset(852.0, "Intuíció", "Solfeggio – intuíció", Mode.PURE),
            Preset(963.0, "Korona", "Solfeggio – korona", Mode.PURE),
        )),
        Group("Agyhullámok", listOf(
            Preset(2.0, "Delta · alvás", "Delta – mély alvás", Mode.BINAURAL),
            Preset(6.0, "Théta · meditáció", "Théta – meditáció", Mode.BINAURAL),
            Preset(10.0, "Alfa · ellazulás", "Alfa – ellazulás", Mode.BINAURAL),
            Preset(18.0, "Béta · éberség", "Béta – éberség", Mode.BINAURAL),
            Preset(40.0, "Gamma · fókusz", "Gamma – fókusz", Mode.ISO),
        )),
        Group("Hangolás", listOf(
            Preset(432.0, "„Verdi” A", "432 Hz – „Verdi” A hang", Mode.PURE),
            Preset(440.0, "Standard A", "440 Hz – standard A hang", Mode.PURE),
            Preset(136.1, "„Om” hang", "136,1 Hz – „Om” hang", Mode.PURE),
        )),
    )

    private val timerMinutes = intArrayOf(0, 5, 10, 15, 30, 60)
    private val timerLabels = arrayOf("Nincs", "5 perc", "10 perc", "15 perc", "30 perc", "60 perc")

    private lateinit var tvHz: TextView
    private lateinit var tvName: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvLeft: TextView
    private lateinit var tvVol: TextView
    private lateinit var tvCar: TextView
    private lateinit var btnPlay: Button
    private lateinit var rgMode: RadioGroup
    private lateinit var sbVol: SeekBar
    private lateinit var sbCar: SeekBar
    private lateinit var spTimer: Spinner
    private lateinit var etCustom: EditText
    private lateinit var scope: ScopeView

    private val presetButtons = mutableListOf<Pair<Button, Preset>>()
    private var ignoreRadio = false
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("frekvencia", MODE_PRIVATE) }

    private val ticker = object : Runnable {
        override fun run() {
            updatePlayState()
            scope.invalidate()
            ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHz = findViewById(R.id.tvHz)
        tvName = findViewById(R.id.tvName)
        tvMeta = findViewById(R.id.tvMeta)
        tvHint = findViewById(R.id.tvHint)
        tvLeft = findViewById(R.id.tvLeft)
        tvVol = findViewById(R.id.tvVol)
        tvCar = findViewById(R.id.tvCar)
        btnPlay = findViewById(R.id.btnPlay)
        rgMode = findViewById(R.id.rgMode)
        sbVol = findViewById(R.id.sbVol)
        sbCar = findViewById(R.id.sbCar)
        spTimer = findViewById(R.id.spTimer)
        etCustom = findViewById(R.id.etCustom)
        scope = findViewById(R.id.scope)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        if (!Engine.running) loadPrefs()
        buildPresets()
        setupControls()
        render()
    }

    override fun onResume() {
        super.onResume()
        ui.post(ticker)
    }

    override fun onPause() {
        ui.removeCallbacks(ticker)
        savePrefs()
        super.onPause()
    }

    // ---------- Előbeállítások ----------

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildPresets() {
        val container = findViewById<LinearLayout>(R.id.presetContainer)
        val perRow = 3
        for (g in groups) {
            val header = TextView(this).apply {
                text = g.title
                setTextColor(getColor(R.color.gold))
                textSize = 16f
                setPadding(0, dp(18), 0, dp(8))
            }
            container.addView(header)

            g.items.chunked(perRow).forEach { rowItems ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (i in 0 until perRow) {
                    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(if (i == 0) 0 else dp(4), dp(4), if (i == perRow - 1) 0 else dp(4), dp(4))
                    }
                    if (i < rowItems.size) {
                        val p = rowItems[i]
                        val b = Button(this).apply {
                            isAllCaps = false
                            text = "${fmt(p.f)} Hz\n${p.label}"
                            textSize = 13f
                            setTextColor(getColor(R.color.fg))
                            setBackgroundResource(R.drawable.preset_bg)
                            setPadding(dp(8), dp(10), dp(8), dp(10))
                            minHeight = dp(64)
                            stateListAnimator = null
                            setOnClickListener {
                                selectPreset(this)
                                applyFreq(p.f, p.title, p.mode)
                            }
                        }
                        presetButtons += b to p
                        row.addView(b, lp)
                    } else {
                        row.addView(View(this), lp)
                    }
                }
                container.addView(row)
            }
        }
        presetButtons.firstOrNull { it.second.f == Engine.freq && it.second.title == Engine.name }
            ?.let { selectPreset(it.first) }
    }

    private fun selectPreset(b: Button?) {
        presetButtons.forEach { it.first.isSelected = it.first === b }
    }

    // ---------- Kezelőszervek ----------

    private fun setupControls() {
        btnPlay.setOnClickListener {
            if (Engine.active) {
                startService(Intent(this, PlayService::class.java).setAction(PlayService.ACTION_STOP))
            } else {
                val min = timerMinutes[spTimer.selectedItemPosition]
                Engine.endAtMs = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L
                startForegroundService(Intent(this, PlayService::class.java))
            }
            ui.postDelayed({ updatePlayState(); scope.invalidate() }, 50)
        }

        rgMode.setOnCheckedChangeListener { _, id ->
            if (ignoreRadio) return@setOnCheckedChangeListener
            val m = when (id) {
                R.id.rbIso -> Mode.ISO
                R.id.rbPure -> Mode.PURE
                else -> Mode.BINAURAL
            }
            if (m == Mode.PURE && Engine.freq < 20) {
                syncRadio()
                tvHint.setTextColor(getColor(R.color.warn))
                tvHint.text = "20 Hz alatt a tiszta hang nem hallható – maradj binaurális vagy izokronikus módban."
                return@setOnCheckedChangeListener
            }
            Engine.mode = m
            render()
            notifyService()
        }

        sbVol.progress = (Engine.volume * 100).toInt()
        sbVol.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                Engine.volume = p / 100f
                tvVol.text = "HANGERŐ  $p%"
            }
        })
        tvVol.text = "HANGERŐ  ${sbVol.progress}%"

        sbCar.progress = Engine.carrier.toInt()
        sbCar.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                Engine.carrier = p.toDouble()
                render()
            }
            override fun onStopTrackingTouch(s: SeekBar?) = notifyService()
        })

        spTimer.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timerLabels)
        spTimer.setSelection(prefs.getInt("timer", 3))
        spTimer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (Engine.active) {
                    val min = timerMinutes[pos]
                    Engine.endAtMs = if (min > 0) System.currentTimeMillis() + min * 60_000L else 0L
                    updatePlayState()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val applyCustom = {
            val f = etCustom.text.toString().trim().replace(',', '.').toDoubleOrNull()
            if (f == null || f < 0.5 || f > 20000) {
                tvHint.setTextColor(getColor(R.color.warn))
                tvHint.text = "Adj meg 0,5 és 20 000 Hz közötti értéket."
            } else {
                selectPreset(null)
                applyFreq(f, "Egyéni frekvencia", null)
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(etCustom.windowToken, 0)
            }
        }
        findViewById<Button>(R.id.btnCustom).setOnClickListener { applyCustom() }
        etCustom.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { applyCustom(); true } else false
        }
    }

    private fun applyFreq(f: Double, title: String, mode: Mode?) {
        Engine.freq = f
        Engine.name = title
        if (mode != null) Engine.mode = mode
        if (f < 20 && Engine.mode == Mode.PURE) Engine.mode = Mode.BINAURAL
        render()
        notifyService()
    }

    private fun notifyService() {
        if (Engine.active) {
            startService(Intent(this, PlayService::class.java).setAction(PlayService.ACTION_UPDATE))
        }
    }

    private fun syncRadio() {
        ignoreRadio = true
        rgMode.check(
            when (Engine.mode) {
                Mode.BINAURAL -> R.id.rbBin
                Mode.ISO -> R.id.rbIso
                Mode.PURE -> R.id.rbPure
            }
        )
        ignoreRadio = false
    }

    // ---------- Megjelenítés ----------

    private fun render() {
        val f = Engine.freq
        val c = Engine.carrier
        tvHz.text = fmt(f)
        tvName.text = Engine.name
        tvCar.text = "VIVŐHANG  ${fmt(c)} Hz"
        sbCar.isEnabled = Engine.mode != Mode.PURE
        syncRadio()
        tvHint.setTextColor(getColor(R.color.muted))
        when (Engine.mode) {
            Mode.BINAURAL -> {
                tvMeta.text = "BINAURÁLIS · VIVŐ ${fmt(c)} Hz"
                tvHint.text = "Bal fül ${fmt(c)} Hz, jobb fül ${fmt(c + f)} Hz – a különbség ${fmt(f)} Hz. Fejhallgató szükséges."
            }
            Mode.ISO -> {
                tvMeta.text = "IZOKRONIKUS · VIVŐ ${fmt(c)} Hz"
                tvHint.text = "${fmt(c)} Hz-es hang, másodpercenként ${fmt(f)}-szor pulzálva. Hangszóróval is működik."
            }
            Mode.PURE -> {
                tvMeta.text = "TISZTA HANG"
                tvHint.text = "Közvetlen ${fmt(f)} Hz-es szinuszhang."
            }
        }
        updatePlayState()
    }

    private fun updatePlayState() {
        btnPlay.text = if (Engine.active) "■  LEÁLLÍTÁS" else "▶  INDÍTÁS"
        val end = Engine.endAtMs
        tvLeft.text = if (Engine.active && end > 0) {
            val s = ((end - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            "hátra %d:%02d".format(s / 60, s % 60)
        } else ""
    }

    // ---------- Beállítások mentése ----------

    private fun loadPrefs() {
        Engine.freq = prefs.getFloat("freq", 7.83f).toDouble().let { Math.round(it * 100) / 100.0 }
        Engine.name = prefs.getString("name", "Schumann alapfrekvencia") ?: "Schumann alapfrekvencia"
        Engine.mode = Mode.values().getOrElse(prefs.getInt("mode", 0)) { Mode.BINAURAL }
        Engine.volume = prefs.getFloat("vol", 0.3f)
        Engine.carrier = prefs.getInt("car", 200).toDouble()
    }

    private fun savePrefs() {
        prefs.edit()
            .putFloat("freq", Engine.freq.toFloat())
            .putString("name", Engine.name)
            .putInt("mode", Engine.mode.ordinal)
            .putFloat("vol", Engine.volume)
            .putInt("car", Engine.carrier.toInt())
            .putInt("timer", spTimer.selectedItemPosition)
            .apply()
    }

    private abstract class SimpleSeek : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}
