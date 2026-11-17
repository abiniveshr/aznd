package com.ashrdev.aznd.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

enum class ThemeKind { WALLPAPER, TEMPLATE, MONO }

data class ThemeLook(
    val cardBorder: Float,
    val topBarBorder: Float,
    val bottomBarBorder: Float,
    val fieldBorder: Float,
    val outlinedButtonBorder: Float,
    val filledButtonBorder: Float,
    val cardBorderLine: Boolean,
    val topBarBorderLine: Boolean,
    val bottomBarBorderLine: Boolean,
    val fieldBorderLine: Boolean,
    val outlinedButtonBorderLine: Boolean,
    val filledButtonBorderLine: Boolean,
    val borderTaper: TaperLine
)

data class ThemeDef(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val kind: ThemeKind,
    val design: Palette,
    val variable: Boolean,
    val refBase: Int,
    val tintGreys: Boolean,
    val excluded: Set<ThemeColor>,
    val look: ThemeLook
)

private class LibraryState(
    val overrides: Map<String, ThemeDef>,
    val users: List<ThemeDef>,
    val bases: Map<String, Int>,
    val shades: Map<String, ContrastShade>,
    val active: String
)

object ThemeLibrary {
    const val ID_WALLPAPER = "wallpaper"
    const val ID_CARBON = "carbon"
    const val ID_MONO = "mono"

    private const val PREFS = "aznd_theme_library"
    private const val KEY = "library"
    private const val SAVE_DELAY_MS = 300L
    private const val OPAQUE = -0x1000000

    private val shipped: List<ThemeDef> = ThemePresets.builtIns

    private var overrides: Map<String, ThemeDef> = emptyMap()
    private var users: List<ThemeDef> = emptyList()
    private var bases: Map<String, Int> = emptyMap()
    private var shades: Map<String, ContrastShade> = emptyMap()
    private var activeId: String = ID_CARBON
    private var saved: ThemeDef = shipped.first { it.id == ID_CARBON }

    private val _themes = MutableStateFlow(shipped)
    val themes: StateFlow<List<ThemeDef>> = _themes.asStateFlow()

    private val _working = MutableStateFlow(saved)
    val working: StateFlow<ThemeDef> = _working.asStateFlow()

    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private var prefs: SharedPreferences? = null
    private val handler = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { flush() }
    private val saver = Executors.newSingleThreadExecutor { r ->
        Thread(r, "theme-library-save").apply { isDaemon = true }
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { text ->
            runCatching { stateFromJson(JSONObject(text)) }.getOrNull()?.let {
                overrides = it.overrides
                users = it.users
                bases = it.bases
                shades = it.shades
                activeId = it.active
            }
        }
        refreshThemes()
        startup(ThemeStore.settings.value, ThemeStore.startupJson)
    }

    private fun startup(s: ThemeSettings, raw: JSONObject?) {
        val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (raw == null) {
            activate(if (dynamicOk) ID_WALLPAPER else ID_CARBON)
            adoptActive(s)
            persist()
            return
        }
        if (!raw.has("themeId")) {
            val contrast = raw.optJSONObject("contrast")
            when {
                contrast != null -> {
                    val color = parseColor(contrast.optString("color")) ?: shipped.first { it.id == ID_MONO }.refBase
                    bases = bases + (ID_MONO to (color or OPAQUE))
                    shades = shades + (ID_MONO to shadeNamed(contrast.optString("shade")))
                    activate(ID_MONO)
                }
                s.useDynamicColor && dynamicOk -> activate(ID_WALLPAPER)
                else -> {
                    val id = newId()
                    val def = legacyDef(s, id, "My theme")
                    users = users + def
                    bases = bases + (id to def.refBase)
                    refreshThemes()
                    activate(id)
                }
            }
            adoptActive(s)
            persist()
            return
        }
        val wanted = if (defOf(s.themeId) != null) s.themeId else ID_CARBON
        s.baseColor?.let { bases = bases + (wanted to it) }
        shades = shades + (wanted to s.shade)
        activate(wanted)
        adoptActive(s)
    }

    private fun adoptActive(s: ThemeSettings) {
        ThemeStore.adopt(ThemeDerive.apply(s, _working.value, baseOf(activeId), shadeOf(activeId)))
    }

    private fun legacyDef(s: ThemeSettings, id: String, name: String) = ThemeDef(
        id = id,
        name = name,
        builtIn = false,
        kind = ThemeKind.TEMPLATE,
        design = s.dark,
        variable = false,
        refBase = s.dark.button.toArgb(),
        tintGreys = true,
        excluded = emptySet(),
        look = ThemeDerive.look(s)
    )

    private fun defOf(id: String): ThemeDef? =
        overrides[id] ?: shipped.firstOrNull { it.id == id } ?: users.firstOrNull { it.id == id }

    private fun refreshThemes() {
        _themes.value = shipped.map { overrides[it.id] ?: it } + users
    }

    private fun activate(id: String) {
        val def = defOf(id) ?: shipped.first { it.id == ID_CARBON }
        activeId = def.id
        saved = def
        _working.value = def
        _dirty.value = false
    }

    private fun newId(): String {
        var stamp = System.currentTimeMillis()
        while (defOf("user_$stamp") != null) stamp++
        return "user_$stamp"
    }

    fun baseOf(id: String): Int = bases[id] ?: defOf(id)?.refBase ?: shipped.first { it.id == ID_CARBON }.refBase

    fun shadeOf(id: String): ContrastShade = shades[id] ?: ContrastShade.ALPHA

    fun isModified(id: String): Boolean = overrides.containsKey(id)

    private fun publish() {
        val def = _working.value
        val base = baseOf(activeId)
        val shade = shadeOf(activeId)
        ThemeStore.update { ThemeDerive.apply(it, def, base, shade) }
    }

    private fun changed(next: ThemeDef) {
        _working.value = next
        _dirty.value = next != saved
        publish()
        persist()
    }

    fun select(id: String) {
        if (defOf(id) == null) return
        activate(id)
        publish()
        persist()
    }

    fun setBase(color: Int) {
        bases = bases + (activeId to color)
        publish()
        persist()
    }

    fun setBaseFor(id: String, color: Int) {
        if (defOf(id) == null) return
        bases = bases + (id to color)
        if (id == activeId) {
            publish()
            persist()
        } else {
            select(id)
        }
    }

    fun setShade(shade: ContrastShade) {
        shades = shades + (activeId to shade)
        publish()
        persist()
    }

    private fun shownDark(def: ThemeDef): Palette = ThemeDerive.dark(def, baseOf(activeId), shadeOf(activeId))

    private fun grey(argb: Int): Int =
        ColorEngine.smartGrey(ColorEngine.lightness(argb), ColorEngine.alphaOf(argb))

    private fun greyFollowing(def: ThemeDef): ThemeDef {
        if (def.kind != ThemeKind.TEMPLATE || !def.variable || !def.tintGreys) return def
        var design = def.design
        for (e in ThemeColor.entries) {
            if (e in def.excluded) continue
            val ownBottom = e != ThemeColor.BOTTOM_BAR || design.bottomBar != Color.Unspecified
            if (ownBottom) design = design.withColor(e, Color(grey(design.colorOf(e).toArgb())))
            design.fills[e]?.let { design = design.withFill(e, ThemeDerive.mapFill(it) { c -> grey(c) }) }
        }
        return def.copy(design = design)
    }

    fun setElementColor(element: ThemeColor, shown: Int) {
        val def = _working.value
        if (def.kind != ThemeKind.TEMPLATE) return
        val follows = def.variable && element !in def.excluded
        val next = when {
            follows && !def.tintGreys -> {
                val sp = shownDark(def)
                val frozen = def.design.withColor(element, sp.colorOf(element)).withFill(element, sp.fillOf(element))
                def.copy(excluded = def.excluded + element, design = frozen.withColor(element, Color(shown)))
            }
            follows -> def.copy(design = def.design.withColor(element, Color(grey(shown))))
            else -> def.copy(design = def.design.withColor(element, Color(shown)))
        }
        changed(next)
    }

    fun setElementFill(element: ThemeColor, shown: FillStyle) {
        val def = _working.value
        if (def.kind != ThemeKind.TEMPLATE || !element.fillable) return
        val follows = def.variable && element !in def.excluded
        val next = when {
            follows && !def.tintGreys -> {
                val sp = shownDark(def)
                val frozen = def.design.withColor(element, sp.colorOf(element))
                def.copy(excluded = def.excluded + element, design = frozen.withFill(element, shown))
            }
            follows -> def.copy(design = def.design.withFill(element, ThemeDerive.mapFill(shown) { grey(it) }))
            else -> def.copy(design = def.design.withFill(element, shown))
        }
        changed(next)
    }

    fun setFollow(element: ThemeColor, follow: Boolean) {
        val def = _working.value
        if (def.kind != ThemeKind.TEMPLATE) return
        val following = element !in def.excluded
        if (follow == following) return
        if (!def.variable) {
            changed(def.copy(excluded = if (follow) def.excluded - element else def.excluded + element))
            return
        }
        val sp = shownDark(def)
        val color = sp.colorOf(element)
        val fill = sp.fillOf(element)
        val next = if (!follow) {
            def.copy(
                design = def.design.withColor(element, color).withFill(element, fill),
                excluded = def.excluded + element
            )
        } else {
            val base = baseOf(activeId)
            val convert: (Int) -> Int = { c ->
                if (def.tintGreys) grey(c) else ColorEngine.unrecolor(c, base, def.refBase, false)
            }
            def.copy(
                design = def.design.withColor(element, Color(convert(color.toArgb()))).withFill(element, ThemeDerive.mapFill(fill, convert)),
                excluded = def.excluded - element
            )
        }
        changed(next)
    }

    fun setVariable(on: Boolean) {
        val def = _working.value
        if (def.builtIn || def.kind != ThemeKind.TEMPLATE || def.variable == on) return
        val next = if (on) {
            greyFollowing(def.copy(variable = true, tintGreys = true, refBase = baseOf(activeId)))
        } else {
            def.copy(design = shownDark(def), variable = false, excluded = emptySet())
        }
        changed(next)
    }

    fun editLook(transform: (ThemeLook) -> ThemeLook) {
        val def = _working.value
        changed(def.copy(look = transform(def.look)))
    }

    fun save() {
        val def = _working.value
        if (def.builtIn) {
            val original = shipped.firstOrNull { it.id == def.id } ?: return
            overrides = if (original == def) overrides - def.id else overrides + (def.id to def)
        } else {
            users = users.map { if (it.id == def.id) def else it }
        }
        saved = def
        _dirty.value = false
        refreshThemes()
        persist()
    }

    fun saveAsNew(name: String): String {
        val id = newId()
        val def = _working.value.copy(id = id, name = name, builtIn = false)
        bases = bases + (id to baseOf(activeId))
        shades = shades + (id to shadeOf(activeId))
        users = users + def
        refreshThemes()
        activate(id)
        publish()
        persist()
        return id
    }

    fun discard() {
        _working.value = saved
        _dirty.value = false
        publish()
    }

    fun resetBuiltIn() {
        val original = shipped.firstOrNull { it.id == activeId } ?: return
        overrides = overrides - original.id
        refreshThemes()
        activate(original.id)
        publish()
        persist()
    }

    fun create(name: String): String {
        val id = newId()
        val look = shipped.first { it.id == ID_CARBON }.look
        fun g(l: Double) = Color(ColorEngine.smartGrey(l))
        val design = Palette(
            background = g(0.14),
            card = g(0.20),
            topBar = g(0.17),
            text = g(0.95),
            button = g(0.70),
            highlight = g(0.30),
            outline = g(0.55),
            bottomBar = g(0.17)
        )
        val def = ThemeDef(
            id = id,
            name = name,
            builtIn = false,
            kind = ThemeKind.TEMPLATE,
            design = design,
            variable = false,
            refBase = shipped.first { it.id == ID_CARBON }.refBase,
            tintGreys = true,
            excluded = emptySet(),
            look = look
        )
        users = users + def
        bases = bases + (id to def.refBase)
        refreshThemes()
        select(id)
        return id
    }

    fun rename(id: String, name: String) {
        if (users.none { it.id == id }) return
        users = users.map { if (it.id == id) it.copy(name = name) else it }
        if (id == activeId) {
            saved = saved.copy(name = name)
            _working.value = _working.value.copy(name = name)
            _dirty.value = _working.value != saved
        }
        refreshThemes()
        persist()
    }

    fun delete(id: String) {
        if (users.none { it.id == id }) return
        users = users.filter { it.id != id }
        bases = bases - id
        shades = shades - id
        refreshThemes()
        if (id == activeId) select(ID_CARBON) else persist()
    }

    fun exportJson(): String {
        val def = _working.value
        return JSONObject()
            .put("format", "aznd-theme")
            .put("version", 3)
            .put("theme", def.toJson())
            .put("base", hex(baseOf(activeId)))
            .put("shade", shadeOf(activeId).name)
            .toString(2)
    }

    fun importJson(text: String): String {
        val o = JSONObject(text)
        require(o.optString("format") == "aznd-theme") { "Not an aznd theme file" }
        val id = newId()
        val def: ThemeDef
        val base: Int
        val shade: ContrastShade
        if (o.has("theme")) {
            val t = defFromJson(o.getJSONObject("theme"), id)
            def = t.copy(id = id, builtIn = false)
            base = parseColor(o.optString("base"))?.or(OPAQUE) ?: def.refBase
            shade = shadeNamed(o.optString("shade"))
        } else {
            def = legacyDef(themeFromJson(text), id, "Imported theme")
            base = def.refBase
            shade = ContrastShade.ALPHA
        }
        users = users + def
        bases = bases + (id to base)
        shades = shades + (id to shade)
        refreshThemes()
        select(id)
        return id
    }

    fun shown(def: ThemeDef, base: Int, shade: ContrastShade, dark: Boolean): Palette =
        ThemeDerive.palette(def, base, shade, dark)

    private fun persist() {
        if (prefs == null) return
        handler.removeCallbacks(saveTask)
        handler.postDelayed(saveTask, SAVE_DELAY_MS)
    }

    private fun flush() {
        val p = prefs ?: return
        val state = LibraryState(overrides, users, bases, shades, activeId)
        saver.execute { p.edit().putString(KEY, stateToJson(state).toString()).apply() }
    }

    private fun hex(argb: Int): String = String.format(Locale.US, "#%06X", 0xFFFFFF and argb)

    private fun parseColor(text: String?): Int? {
        if (text.isNullOrEmpty()) return null
        return runCatching { android.graphics.Color.parseColor(text) }.getOrNull()
    }

    private fun shadeNamed(name: String?): ContrastShade =
        ContrastShade.entries.firstOrNull { it.name == name } ?: ContrastShade.ALPHA

    private fun stateToJson(s: LibraryState): JSONObject = JSONObject().apply {
        put("active", s.active)
        put("overrides", JSONArray().also { a -> s.overrides.values.forEach { a.put(it.toJson()) } })
        put("themes", JSONArray().also { a -> s.users.forEach { a.put(it.toJson()) } })
        put("bases", JSONObject().also { o -> s.bases.forEach { (k, v) -> o.put(k, hex(v)) } })
        put("shades", JSONObject().also { o -> s.shades.forEach { (k, v) -> o.put(k, v.name) } })
    }

    private fun stateFromJson(o: JSONObject): LibraryState {
        val overrides = LinkedHashMap<String, ThemeDef>()
        o.optJSONArray("overrides")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.optJSONObject(i) ?: continue
                val id = t.optString("id")
                val original = shipped.firstOrNull { it.id == id } ?: continue
                overrides[id] = defFromJson(t, id).copy(builtIn = true, kind = original.kind)
            }
        }
        val users = ArrayList<ThemeDef>()
        o.optJSONArray("themes")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.optJSONObject(i) ?: continue
                val id = t.optString("id")
                if (id.isEmpty() || shipped.any { it.id == id } || users.any { it.id == id }) continue
                users += defFromJson(t, id).copy(builtIn = false)
            }
        }
        val bases = HashMap<String, Int>()
        o.optJSONObject("bases")?.let { b ->
            b.keys().forEach { k -> parseColor(b.optString(k))?.let { bases[k] = it or OPAQUE } }
        }
        val shades = HashMap<String, ContrastShade>()
        o.optJSONObject("shades")?.let { b ->
            b.keys().forEach { k -> shades[k] = shadeNamed(b.optString(k)) }
        }
        return LibraryState(overrides, users, bases, shades, o.optString("active", ID_CARBON))
    }

    private fun ThemeLook.toJson(): JSONObject = JSONObject().apply {
        put("cardBorder", cardBorder.toDouble())
        put("topBarBorder", topBarBorder.toDouble())
        put("bottomBarBorder", bottomBarBorder.toDouble())
        put("fieldBorder", fieldBorder.toDouble())
        put("outlinedButtonBorder", outlinedButtonBorder.toDouble())
        put("filledButtonBorder", filledButtonBorder.toDouble())
        put("cardBorderLine", cardBorderLine)
        put("topBarBorderLine", topBarBorderLine)
        put("bottomBarBorderLine", bottomBarBorderLine)
        put("fieldBorderLine", fieldBorderLine)
        put("outlinedButtonBorderLine", outlinedButtonBorderLine)
        put("filledButtonBorderLine", filledButtonBorderLine)
        put("borderTaper", borderTaper.toJson())
    }

    private fun lookFromJson(o: JSONObject?, fb: ThemeLook): ThemeLook {
        if (o == null) return fb
        fun width(key: String, d: Float) = o.optDouble(key, d.toDouble()).toFloat().coerceIn(0f, MAX_BORDER_DP)
        return ThemeLook(
            cardBorder = width("cardBorder", fb.cardBorder),
            topBarBorder = width("topBarBorder", fb.topBarBorder),
            bottomBarBorder = width("bottomBarBorder", fb.bottomBarBorder),
            fieldBorder = width("fieldBorder", fb.fieldBorder),
            outlinedButtonBorder = width("outlinedButtonBorder", fb.outlinedButtonBorder),
            filledButtonBorder = width("filledButtonBorder", fb.filledButtonBorder),
            cardBorderLine = o.optBoolean("cardBorderLine", fb.cardBorderLine),
            topBarBorderLine = o.optBoolean("topBarBorderLine", fb.topBarBorderLine),
            bottomBarBorderLine = o.optBoolean("bottomBarBorderLine", fb.bottomBarBorderLine),
            fieldBorderLine = o.optBoolean("fieldBorderLine", fb.fieldBorderLine),
            outlinedButtonBorderLine = o.optBoolean("outlinedButtonBorderLine", fb.outlinedButtonBorderLine),
            filledButtonBorderLine = o.optBoolean("filledButtonBorderLine", fb.filledButtonBorderLine),
            borderTaper = if (o.has("borderTaper")) taperLineFromJson(o.optJSONObject("borderTaper")) else fb.borderTaper
        )
    }

    private fun ThemeDef.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("builtIn", builtIn)
        put("kind", kind.name)
        put("design", design.toJson())
        put("variable", variable)
        put("refBase", hex(refBase))
        put("tintGreys", tintGreys)
        put("excluded", JSONArray().also { a -> excluded.forEach { a.put(it.name) } })
        put("look", look.toJson())
    }

    private fun defFromJson(o: JSONObject, id: String): ThemeDef {
        val carbon = shipped.first { it.id == ID_CARBON }
        val excluded = HashSet<ThemeColor>()
        o.optJSONArray("excluded")?.let { a ->
            for (i in 0 until a.length()) ThemeColor.entries.firstOrNull { it.name == a.optString(i) }?.let { excluded += it }
        }
        return greyFollowing(ThemeDef(
            id = id,
            name = o.optString("name", "Theme").ifEmpty { "Theme" },
            builtIn = o.optBoolean("builtIn", false),
            kind = ThemeKind.entries.firstOrNull { it.name == o.optString("kind") } ?: ThemeKind.TEMPLATE,
            design = paletteFrom(o.optJSONObject("design"), DefaultDarkPalette),
            variable = o.optBoolean("variable", false),
            refBase = (parseColor(o.optString("refBase")) ?: carbon.refBase) or OPAQUE,
            tintGreys = o.optBoolean("tintGreys", true),
            excluded = excluded,
            look = lookFromJson(o.optJSONObject("look"), carbon.look)
        ))
    }
}
