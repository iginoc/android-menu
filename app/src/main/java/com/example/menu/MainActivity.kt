package com.example.menu

import android.animation.*
import android.app.*
import android.content.*
import android.content.pm.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var catPrefs: SharedPreferences
    private lateinit var linkPrefs: SharedPreferences
    private val keys = (1..12).map { "icon_${it}_pkg" }
    private val wedges = mutableListOf<WedgeImageView>()
    private var allApps: List<ResolveInfo> = emptyList()
    private var cacheReady = false
    private var currentCategoryMode: Int? = null
    private var isCollageMode = false
    
    private val appCategoryCache = mutableMapOf<String, Int>()
    internal val iconCache = mutableMapOf<String, Drawable>()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportToFile(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, i ->
            val s = i.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(s.left, s.top, s.right, s.bottom); i
        }
        prefs = getSharedPreferences("app_shortcuts", MODE_PRIVATE)
        catPrefs = getSharedPreferences("custom_categories", MODE_PRIVATE)
        linkPrefs = getSharedPreferences("links_storage", MODE_PRIVATE)
        
        isCollageMode = prefs.getBoolean("is_collage_mode", false)
        
        val ids = listOf(R.id.wedge_1, R.id.wedge_2, R.id.wedge_3, R.id.wedge_4, R.id.wedge_5, R.id.wedge_6,
                         R.id.wedge_7, R.id.wedge_8, R.id.wedge_9, R.id.wedge_10, R.id.wedge_11, R.id.wedge_12)
        for (id in ids) findViewById<WedgeImageView>(id)?.let { wedges.add(it) }
        
        findViewById<View>(R.id.center_helper)?.setOnClickListener { 
            if (currentCategoryMode != null) {
                prepareAndAnimate(null)
            } else {
                finishAndRemoveTask()
            }
        }

        findViewById<View>(R.id.clock)?.setOnLongClickListener {
            isCollageMode = !isCollageMode
            prefs.edit().putBoolean("is_collage_mode", isCollageMode).apply()
            updateViewVisibility()
            true
        }
        
        if (savedInstanceState == null) {
            handleSharedIntent(intent)
        }
        loadData()
        checkLauncher()
    }

    private fun updateViewVisibility() {
        val drawingView = findViewById<DrawingView>(R.id.drawing_view)
        val catDisplay = findViewById<TextView>(R.id.category_display_name)
        
        if (isCollageMode) {
            wedges.forEach { it.visibility = View.GONE }
            catDisplay?.visibility = View.VISIBLE
            drawingView?.visibility = View.VISIBLE
            updateCollageData()
        } else {
            wedges.forEach { it.visibility = View.VISIBLE }
            catDisplay?.visibility = View.VISIBLE
            drawingView?.visibility = View.GONE
            if (currentCategoryMode != null) {
                prepareAndAnimate(currentCategoryMode)
            } else {
                setupUI()
            }
        }
    }

    private fun updateCollageData() {
        val drawingView = findViewById<DrawingView>(R.id.drawing_view) ?: return
        val catDisplay = findViewById<TextView>(R.id.category_display_name)
        catDisplay?.text = if (currentCategoryMode != null) getCatName(currentCategoryMode!!) else ""

        val items = mutableListOf<AppListItem>()
        items.add(AppListItem.Special) // La prima icona è speciale (nera)

        if (currentCategoryMode != null && currentCategoryMode != -2) {
            val catApps = allApps.filter { getCat(it.activityInfo.applicationInfo) == currentCategoryMode }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            val catLinks = getStoredLinks().filter { getLinkCat(it) == currentCategoryMode }.sorted()
            items.addAll(catApps.map { AppListItem.App(it) })
            items.addAll(catLinks.map { AppListItem.Link(it) })
        } else {
            keys.forEach { key ->
                prefs.getString(key, null)?.let { pkg ->
                    allApps.find { it.activityInfo.packageName == pkg }?.let { items.add(AppListItem.App(it)) }
                }
            }
        }
        
        drawingView.setData(items) { item ->
            when (item) {
                is AppListItem.Special -> {
                    if (currentCategoryMode != null) {
                        prepareAndAnimate(null)
                    } else {
                        showCats()
                    }
                }
                is AppListItem.App -> {
                    if (currentCategoryMode == null) {
                        val pkg = item.res.activityInfo.packageName
                        try {
                            val newCat = getCat(packageManager.getApplicationInfo(pkg, 0))
                            prepareAndAnimate(newCat)
                        } catch (e: Exception) { showAppsList() }
                    } else {
                        launch(item.res.activityInfo.packageName)
                    }
                }
                is AppListItem.Link -> openLink(item.url)
                else -> {}
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                val urlStr = sharedText.trim()
                val links = getStoredLinks().toMutableList()
                if (!links.contains(urlStr)) {
                    links.add(0, urlStr)
                    saveLinks(links)
                    Toast.makeText(this, "Link salvato in 'Link'", Toast.LENGTH_SHORT).show()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val title = fetchTitle(urlStr)
                        if (title != null) linkPrefs.edit().putString("title_$urlStr", title).apply()
                    }
                }
                intent.action = null
            }
        }
    }

    private suspend fun fetchTitle(urlStr: String): String? = withContext(Dispatchers.IO) {
        try {
            val formattedUrl = if (!urlStr.startsWith("http")) "https://$urlStr" else urlStr
            val connection = (URL(formattedUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, Gecko) Chrome/91.0.4472.124 Safari/537.36")
            }
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            val match = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            match?.groupValues?.get(1)?.trim()?.let { android.text.Html.fromHtml(it.replace(Regex("\\s+"), " "), android.text.Html.FROM_HTML_MODE_LEGACY).toString() }
        } catch (e: Exception) { null }
    }

    private fun getStoredLinks(): List<String> {
        val json = linkPrefs.getString("links_list", "[]")
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun saveLinks(links: List<String>) {
        val array = JSONArray()
        links.forEach { array.put(it) }
        linkPrefs.edit().putString("links_list", array.toString()).apply()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.Default) {
            allApps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            catPrefs.all.forEach { (k, v) -> if (k.startsWith("app_cat_") && v is Int) appCategoryCache[k.substring(8)] = v }
            
            keys.forEach { key ->
                prefs.getString(key, null)?.let { pkg ->
                    allApps.find { it.activityInfo.packageName == pkg }?.let { res ->
                        iconCache[pkg] = res.loadIcon(packageManager)
                    }
                }
            }
            
            cacheReady = true
            withContext(Dispatchers.Main) {
                if (prefs.getString(keys[0], null) == null) {
                    val s = allApps.shuffled(); val e = prefs.edit()
                    for (i in keys.indices) if (i != 5 && i < s.size) e.putString(keys[i], s[i].activityInfo.packageName)
                    e.apply()
                }
                if (isCollageMode) updateViewVisibility() else setupUI()
            }
        }
    }

    private fun prepareAndAnimate(newCatId: Int?) {
        val items = if (newCatId != null && newCatId != -2) {
            val catApps = allApps.filter { getCat(it.activityInfo.applicationInfo) == newCatId }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            val catLinks = getStoredLinks().filter { getLinkCat(it) == newCatId }.sorted()
            catApps.map { AppListItem.App(it) } + catLinks.map { AppListItem.Link(it) }
        } else null

        if (isCollageMode) {
            currentCategoryMode = newCatId
            updateViewVisibility()
        } else {
            animateTransition {
                currentCategoryMode = newCatId
                if (newCatId != null && newCatId != -2) {
                    applyCategoryItemsToWedges(items ?: emptyList())
                } else {
                    setupUI()
                }
            }
        }
    }

    private fun animateTransition(callback: () -> Unit) {
        ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 400
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val rotation = anim.animatedValue as Float
                for (i in 0..5) wedges[i].rotation = rotation
                for (i in 6..11) wedges[i].rotation = -rotation
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    for (w in wedges) w.rotation = 0f
                    callback()
                }
            })
            start()
        }
    }

    private fun setupUI() {
        val catDisplay = findViewById<TextView>(R.id.category_display_name)
        catDisplay?.text = if (currentCategoryMode != null) getCatName(currentCategoryMode!!) else ""
        
        val base = listOf(-120f, -60f, 0f, 60f, 120f, 180f)
        
        for (i in wedges.indices) {
            val w = wedges[i]; val k = keys[i]
            w.wedgeColor = Color.BLACK
            w.iconRadiusFraction = 0.7f
            w.startAngle = base[i % 6] + 30f
            
            if (i == 5) {
                w.setImageDrawable(null)
                w.setOnClickListener { showCats() }
            } else {
                val pkg = prefs.getString(k, null)
                val icon = if (pkg != null) iconCache[pkg] ?: try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null } else null
                w.setImageDrawable(icon ?: if (pkg != null) resources.getDrawable(R.drawable.icona, null) else null)
                
                w.setOnClickListener { 
                    if (pkg != null) {
                        try { 
                            val newCat = getCat(packageManager.getApplicationInfo(pkg, 0))
                            prepareAndAnimate(newCat)
                        } catch (e: Exception) { showAppsList() }
                    }
                }
                w.setOnLongClickListener { pickApp(i, k); true }
            }
        }
    }

    private fun applyCategoryItemsToWedges(items: List<AppListItem>) {
        val catDisplay = findViewById<TextView>(R.id.category_display_name)
        catDisplay?.text = if (currentCategoryMode != null) getCatName(currentCategoryMode!!) else ""
        
        for (i in wedges.indices) {
            val w = wedges[i]
            w.wedgeColor = Color.BLACK
            
            if (i == 5) {
                w.setImageDrawable(null)
                w.setOnClickListener { prepareAndAnimate(null) }
            } else {
                val itemIdx = if (i < 5) i else i - 1
                if (itemIdx < items.size) {
                    when (val item = items[itemIdx]) {
                        is AppListItem.App -> {
                            w.setImageDrawable(resToIcon(item.res))
                            w.setOnClickListener { launch(item.res.activityInfo.packageName) }
                        }
                        is AppListItem.Link -> {
                            w.setImageResource(android.R.drawable.ic_menu_share)
                            w.setOnClickListener { openLink(item.url) }
                        }
                        else -> {}
                    }
                } else {
                    w.setImageDrawable(null); w.setOnClickListener(null)
                }
                w.setOnLongClickListener(null)
            }
        }
    }

    private fun pickApp(idx: Int, k: String) {
        val d = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        d.setContentView(R.layout.apps_list_dialog)
        d.findViewById<TextView>(R.id.dialog_title)?.text = "Scegli App"
        val rv = d.findViewById<RecyclerView>(R.id.apps_recycler_view)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = AppsAdapter(allApps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.map { AppListItem.App(it) }, { item ->
            if (item is AppListItem.App) {
                val pkg = item.res.activityInfo.packageName
                prefs.edit().putString(k, pkg).apply()
                val icon = resToIcon(item.res)
                wedges[idx].setImageDrawable(icon)
                d.dismiss()
            }
        }, {})
        d.show()
    }
    
    private fun resToIcon(res: ResolveInfo): Drawable {
        val pkg = res.activityInfo.packageName
        return iconCache[pkg] ?: res.loadIcon(packageManager).also { iconCache[pkg] = it }
    }

    private fun showAppsList(filter: Int? = null, mgmt: Boolean = false) {
        if (filter == -2) {
            lifecycleScope.launch(Dispatchers.Default) {
                allApps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                catPrefs.all.forEach { (k, v) -> if (k.startsWith("app_cat_") && v is Int) appCategoryCache[k.substring(8)] = v }
                
                withContext(Dispatchers.Main) {
                    internalShowAppsList(filter, mgmt)
                }
            }
        } else {
            internalShowAppsList(filter, mgmt)
        }
    }

    private fun internalShowAppsList(filter: Int? = null, mgmt: Boolean = false) {
        val d = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        d.setContentView(R.layout.apps_list_dialog)
        d.findViewById<TextView>(R.id.dialog_title)?.text = if (filter != null) getCatName(filter) else "Applicazioni"
        val rv = d.findViewById<RecyclerView>(R.id.apps_recycler_view)
        val lm = LinearLayoutManager(this); rv.layoutManager = lm
        val allLinks = getStoredLinks()
        val items = mutableListOf<AppListItem>()
        
        if (filter == null) {
            val map = (allApps.map { it to getCat(it.activityInfo.applicationInfo) } + allLinks.map { it to getLinkCat(it) }).groupBy { it.second }
            map.keys.sorted().forEach { id ->
                items.add(AppListItem.Header(getCatName(id), id))
                map[id]!!.forEach { (obj, _) -> if (obj is ResolveInfo) items.add(AppListItem.App(obj)) else if (obj is String) items.add(AppListItem.Link(obj)) }
            }
        } else if (filter == -2) {
            allApps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.forEach { items.add(AppListItem.App(it)) }
            allLinks.sorted().forEach { items.add(AppListItem.Link(it)) }
        } else {
            allApps.filter { getCat(it.activityInfo.applicationInfo) == filter }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.forEach { items.add(AppListItem.App(it)) }
            allLinks.filter { getLinkCat(it) == filter }.sorted().forEach { items.add(AppListItem.Link(it)) }
        }
        
        rv.adapter = AppsAdapter(items, { item ->
            when (item) {
                is AppListItem.App -> if (mgmt) moveDialog(item.res) { d.dismiss(); showAppsList(filter, true) } else { launch(item.res.activityInfo.packageName); d.dismiss() }
                is AppListItem.Link -> if (mgmt) moveLinkDialog(item.url) { d.dismiss(); showAppsList(filter, true) } else { openLink(item.url); d.dismiss() }
                else -> {}
            }
        }, { d.dismiss(); showAppsList(filter, mgmt) })
        d.show()
    }

    private fun openLink(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (!url.startsWith("http")) "https://$url" else url))) }
        catch (e: Exception) { Toast.makeText(this, "Impossibile aprire il link", Toast.LENGTH_SHORT).show() }
    }

    private fun showCats() {
        val d = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        d.setContentView(R.layout.categories_list_dialog)
        val rv = d.findViewById<RecyclerView>(R.id.categories_recycler_view)
        rv.layoutManager = LinearLayoutManager(this)
        val cats = (allApps.map { getCat(it.activityInfo.applicationInfo) } + getStoredLinks().map { getLinkCat(it) }).toSet().toMutableList()
        if (!cats.contains(999)) cats.add(999)
        if (!cats.contains(-2)) cats.add(-2)
        
        rv.adapter = CategoriesAdapter(cats.sorted(), { id -> d.dismiss(); showAppsList(id, true) }, { id -> if(id != 999 && id != -2) renameDialog(id) { d.dismiss(); showCats() } })
        d.findViewById<Button>(R.id.btn_export)?.setOnClickListener { exportLauncher.launch("menu_settings.json") }
        d.findViewById<Button>(R.id.btn_import)?.setOnClickListener { importLauncher.launch(arrayOf("application/json")) }
        d.show()
    }

    private fun exportToFile(uri: Uri) {
        try {
            val json = JSONObject(); val catsJson = JSONObject(); val appsJson = JSONObject(); val linksMapJson = JSONObject(); val shortcutsJson = JSONObject(); val titlesJson = JSONObject()
            catPrefs.all.forEach { (k, v) -> if (k.startsWith("cat_")) catsJson.put(k, v) else if (k.startsWith("app_cat_")) appsJson.put(k, v) else if (k.startsWith("link_cat_")) linksMapJson.put(k, v) }
            keys.forEach { key -> prefs.getString(key, null)?.let { shortcutsJson.put(key, it) } }
            val links = getStoredLinks(); links.forEach { url -> linkPrefs.getString("title_$url", null)?.let { titlesJson.put(url, it) } }
            json.put("categories", catsJson); json.put("app_mappings", appsJson); json.put("link_mappings", linksMapJson); json.put("shortcuts", shortcutsJson); json.put("links", JSONArray(links)); json.put("link_titles", titlesJson)
            contentResolver.openOutputStream(uri)?.use { it.write(json.toString().toByteArray()) }
            Toast.makeText(this, "Esportato con successo", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Errore durante l'esportazione", Toast.LENGTH_SHORT).show() }
    }

    private fun importFromFile(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (content != null) {
                val json = JSONObject(content); val catEditor = catPrefs.edit().clear()
                if (json.has("categories")) { val cats = json.getJSONObject("categories"); cats.keys().forEach { catEditor.putString(it, cats.getString(it)) } }
                if (json.has("app_mappings")) { val apps = json.getJSONObject("app_mappings"); apps.keys().forEach { catEditor.putInt(it, apps.getInt(it)) } }
                if (json.has("link_mappings")) { val lmap = json.getJSONObject("link_mappings"); lmap.keys().forEach { catEditor.putInt(it, lmap.getInt(it)) } }
                catEditor.apply()
                val prefsEditor = prefs.edit().clear()
                if (json.has("shortcuts")) { val sh = json.getJSONObject("shortcuts"); sh.keys().forEach { prefsEditor.putString(it, sh.getString(it)) } }
                prefsEditor.apply()
                val linkEditor = linkPrefs.edit().clear()
                if (json.has("links")) { val lArray = json.getJSONArray("links"); val lList = (0 until lArray.length()).map { lArray.getString(it) }; linkEditor.putString("links_list", JSONArray(lList).toString()) }
                if (json.has("link_titles")) { val titles = json.getJSONObject("link_titles"); titles.keys().forEach { linkEditor.putString("title_$it", titles.getString(it)) } }
                linkEditor.apply()
                Toast.makeText(this, "Importato con successo. Riavvio...", Toast.LENGTH_SHORT).show()
                Handler(Looper.getMainLooper()).postDelayed({ finish(); startActivity(intent) }, 1000)
            }
        } catch (e: Exception) { Toast.makeText(this, "Errore durante l'importazione", Toast.LENGTH_SHORT).show() }
    }

    private fun renameDialog(id: Int, cb: () -> Unit) {
        val et = EditText(this).apply { setText(getCatName(id)) }
        AlertDialog.Builder(this).setTitle("Rinomina").setView(et).setPositiveButton("OK") { _, _ -> catPrefs.edit().putString("cat_$id", et.text.toString()).apply(); cb() }.show()
    }

    private fun moveDialog(res: ResolveInfo, cb: () -> Unit) {
        val cats = listOf(0,1,2,3,4,5,6,7,-1, 999)
        AlertDialog.Builder(this).setTitle("Sposta").setItems(cats.map { getCatName(it) }.toTypedArray()) { _, w -> 
            val pkg = res.activityInfo.packageName
            val newCat = cats[w]
            catPrefs.edit().putInt("app_cat_$pkg", newCat).apply()
            appCategoryCache[pkg] = newCat
            cb() 
        }.show()
    }

    private fun moveLinkDialog(url: String, cb: () -> Unit) {
        val cats = listOf(0,1,2,3,4,5,6,7,-1, 999)
        AlertDialog.Builder(this).setTitle("Sposta Link").setItems(cats.map { getCatName(it) }.toTypedArray()) { _, w -> 
            catPrefs.edit().putInt("link_cat_$url", cats[w]).apply()
            cb() 
        }.show()
    }

    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { try { startActivity(it); finishAndRemoveTask() } catch (e: Exception) {} }
    }

    private fun checkLauncher() {
        val r = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        if (r == null || packageName != r.activityInfo.packageName) {
            AlertDialog.Builder(this).setTitle("Launcher").setMessage("Imposta predefinito?").setPositiveButton("Sì") { _, _ -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }.setNegativeButton("No", null).show()
        }
    }

    private fun getCatName(id: Int): String {
        if (id == -2) return "Tutte"
        if (id == 999) return "Link"
        catPrefs.getString("cat_$id", null)?.let { return it }
        return if (Build.VERSION.SDK_INT >= 26) when(id) {
            0 -> "Giochi"; 1 -> "Audio"; 2 -> "Video"; 3 -> "Immagini"; 4 -> "Social"; 5 -> "News"; 6 -> "Mappe"; 7 -> "Produttività"; else -> "Altro"
        } else "App"
    }

    private fun getCat(ai: ApplicationInfo) = appCategoryCache[ai.packageName] ?: catPrefs.getInt("app_cat_${ai.packageName}", if (Build.VERSION.SDK_INT >= 26) ai.category else -1)
    private fun getLinkCat(url: String) = catPrefs.getInt("link_cat_$url", 999)

    sealed class AppListItem { 
        object Special : AppListItem()
        data class Header(val title: String, val id: Int) : AppListItem()
        data class App(val res: ResolveInfo) : AppListItem()
        data class Link(val url: String) : AppListItem()
    }

    inner class AppsAdapter(private val list: List<AppListItem>, private val onClick: (AppListItem) -> Unit, private val onRefresh: () -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(p: Int) = when(list[p]) { is AppListItem.Header -> 0; is AppListItem.App -> 1; is AppListItem.Link -> 2; is AppListItem.Special -> 3 }
        override fun onCreateViewHolder(parent: ViewGroup, t: Int) = when(t) {
            0 -> HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.header_item, parent, false))
            else -> AppVH(LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false))
        }
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val i = list[p]
            if (h is HeaderVH && i is AppListItem.Header) h.title.text = i.title
            else if (h is AppVH) {
                if (i is AppListItem.App) {
                    h.name.text = i.res.loadLabel(packageManager); h.icon.setImageDrawable(resToIcon(i.res)); 
                    h.itemView.setOnClickListener { onClick(i) }
                    h.itemView.setOnLongClickListener { launch(i.res.activityInfo.packageName); true }
                } else if (i is AppListItem.Link) {
                    h.name.text = linkPrefs.getString("title_${i.url}", i.url); h.icon.setImageResource(android.R.drawable.ic_menu_share); h.itemView.setOnClickListener { onClick(i) }
                    h.itemView.setOnLongClickListener {
                        AlertDialog.Builder(this@MainActivity).setTitle("Elimina?").setPositiveButton("Sì") { _, _ ->
                            val newList = getStoredLinks().toMutableList(); newList.remove(i.url); saveLinks(newList)
                            catPrefs.edit().remove("link_cat_${i.url}").apply(); linkPrefs.edit().remove("title_${i.url}").apply(); onRefresh()
                        }.setNegativeButton("No", null).show(); true
                    }
                }
            }
        }
        override fun getItemCount() = list.size
    }

    inner class CategoriesAdapter(private val list: List<Int>, private val onClick: (Int) -> Unit, private val onLongClick: (Int) -> Unit) : RecyclerView.Adapter<CategoriesAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.category_item, p, false))
        override fun onBindViewHolder(h: VH, p: Int) { val id = list[p]; h.name.text = getCatName(id); h.itemView.setOnClickListener { onClick(id) }; h.itemView.setOnLongClickListener { onLongClick(id); true } }
        override fun getItemCount() = list.size
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.category_name) }
    }
    class HeaderVH(v: View) : RecyclerView.ViewHolder(v) { val title: TextView = v.findViewById(R.id.header_title) }
    class AppVH(v: View) : RecyclerView.ViewHolder(v) { val icon: ImageView = v.findViewById(R.id.app_icon); val name: TextView = v.findViewById(R.id.app_name) }
}
