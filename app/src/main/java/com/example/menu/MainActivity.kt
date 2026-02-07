package com.example.menu

import android.app.*
import android.content.*
import android.content.pm.*
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var catPrefs: SharedPreferences
    private val keys = (1..12).map { "icon_${it}_pkg" }
    private val wedges = mutableListOf<WedgeImageView>()
    private var allApps: List<ResolveInfo> = emptyList()
    private var cacheReady = false

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
        val ids = listOf(R.id.wedge_1, R.id.wedge_2, R.id.wedge_3, R.id.wedge_4, R.id.wedge_5, R.id.wedge_6,
                         R.id.wedge_7, R.id.wedge_8, R.id.wedge_9, R.id.wedge_10, R.id.wedge_11, R.id.wedge_12)
        for (id in ids) findViewById<WedgeImageView>(id)?.let { wedges.add(it) }
        findViewById<View>(R.id.center_helper)?.setOnClickListener { finishAndRemoveTask() }
        loadData()
        checkLauncher()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.Default) {
            allApps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            cacheReady = true
            withContext(Dispatchers.Main) {
                if (prefs.getString(keys[0], null) == null) {
                    val s = allApps.shuffled(); val e = prefs.edit()
                    for (i in keys.indices) if (i != 5 && i < s.size) e.putString(keys[i], s[i].activityInfo.packageName)
                    e.apply()
                }
                setupUI()
            }
        }
    }

    private fun setupUI() {
        val base = listOf(-120f, -60f, 0f, 60f, 120f, 180f)
        val colors = listOf(Color.parseColor("#660000FF"), Color.parseColor("#66FF0000"), Color.parseColor("#6600FF00"))
        for (i in wedges.indices) {
            val w = wedges[i]; val k = keys[i]
            w.wedgeColor = colors[i % 3]
            if (i < 6) { w.startAngle = base[i]; w.iconRadiusFraction = 0.6f }
            else { w.startAngle = base[i - 6] + 30f; w.iconRadiusFraction = 0.85f }
            if (i == 5) {
                w.setImageResource(R.drawable.icona)
                w.setOnClickListener { showCats() }
            } else {
                val p = prefs.getString(k, null)
                if (p != null) {
                    try { w.setImageDrawable(packageManager.getApplicationIcon(p)) } catch (e: Exception) { w.setImageResource(R.drawable.icona) }
                }
                w.setOnClickListener { 
                    val pkg = prefs.getString(k, null)
                    if (pkg != null) {
                        try { showAppsList(getCat(packageManager.getApplicationInfo(pkg, 0))) } catch (e: Exception) { showAppsList() }
                    }
                }
                w.setOnLongClickListener { pickApp(i, k); true }
            }
        }
    }

    private fun pickApp(idx: Int, k: String) {
        val d = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        d.setContentView(R.layout.apps_list_dialog)
        val rv = d.findViewById<RecyclerView>(R.id.apps_recycler_view)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = AppsAdapter(allApps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.map { AppListItem.App(it) }) { res ->
            val pkg = res.activityInfo.packageName
            prefs.edit().putString(k, pkg).apply()
            try { wedges[idx].setImageDrawable(packageManager.getApplicationIcon(pkg)) } catch (e: Exception) {}
            d.dismiss()
        }
        d.show()
    }

    private fun showAppsList(filter: Int? = null, mgmt: Boolean = false) {
        val d = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        d.setContentView(R.layout.apps_list_dialog)
        val rv = d.findViewById<RecyclerView>(R.id.apps_recycler_view)
        val lm = LinearLayoutManager(this); rv.layoutManager = lm
        val ql = d.findViewById<LinearLayout>(R.id.category_quick_links)
        val apps = if (filter != null) allApps.filter { getCat(it.activityInfo.applicationInfo) == filter } else allApps
        val items = mutableListOf<AppListItem>()
        val posMap = mutableMapOf<Int, Int>()
        if (filter == null) {
            val map = apps.groupBy { getCat(it.activityInfo.applicationInfo) }
            map.keys.sorted().forEach { id ->
                posMap[id] = items.size
                items.add(AppListItem.Header(getCatName(id), id))
                map[id]!!.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.forEach { items.add(AppListItem.App(it)) }
            }
            posMap.keys.sorted().forEach { id ->
                ql?.addView(TextView(this).apply { text = getCatName(id); setPadding(24,12,24,12); setTextColor(resources.getColor(android.R.color.darker_gray, null))
                    setOnClickListener { lm.scrollToPositionWithOffset(posMap[id] ?: 0, 0) } })
            }
            ql?.addView(TextView(this).apply { text = "Impostazioni"; setPadding(24,12,24,12); setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
                setOnClickListener { d.dismiss(); showCats() } })
        } else apps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.forEach { items.add(AppListItem.App(it)) }
        rv.adapter = AppsAdapter(items) { res -> if (mgmt) moveDialog(res) { d.dismiss(); showAppsList(filter, true) } else { launch(res.activityInfo.packageName); d.dismiss() } }
        d.show()
    }

    private fun showCats() {
        val d = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        d.setContentView(R.layout.categories_list_dialog)
        val rv = d.findViewById<RecyclerView>(R.id.categories_recycler_view)
        rv.layoutManager = LinearLayoutManager(this)
        val cats = allApps.map { getCat(it.activityInfo.applicationInfo) }.toSet().toList().sorted()
        rv.adapter = CategoriesAdapter(cats, { id -> d.dismiss(); showAppsList(id, true) }, { id -> renameDialog(id) { d.dismiss(); showCats() } })
        d.show()
    }

    private fun renameDialog(id: Int, cb: () -> Unit) {
        val et = EditText(this).apply { setText(getCatName(id)) }
        AlertDialog.Builder(this).setTitle("Rinomina").setView(et).setPositiveButton("OK") { _, _ -> catPrefs.edit().putString("cat_$id", et.text.toString()).apply(); cb() }.show()
    }

    private fun moveDialog(res: ResolveInfo, cb: () -> Unit) {
        val cats = listOf(0,1,2,3,4,5,6,7,-1)
        AlertDialog.Builder(this).setTitle("Sposta").setItems(cats.map { getCatName(it) }.toTypedArray()) { _, w -> catPrefs.edit().putInt("app_cat_${res.activityInfo.packageName}", cats[w]).apply(); cb() }.show()
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) try { startActivity(intent); finishAndRemoveTask() } catch (e: Exception) {}
    }

    private fun checkLauncher() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        if (r == null || packageName != r.activityInfo.packageName) {
            AlertDialog.Builder(this).setTitle("Launcher").setMessage("Imposta predefinito?").setPositiveButton("Sì") { _, _ -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }.setNegativeButton("No", null).show()
        }
    }

    private fun getCatName(id: Int): String {
        catPrefs.getString("cat_$id", null)?.let { return it }
        return if (Build.VERSION.SDK_INT >= 26) when(id) {
            0 -> "Giochi"; 1 -> "Audio"; 2 -> "Video"; 3 -> "Immagini"; 4 -> "Social"; 5 -> "News"; 6 -> "Mappe"; 7 -> "Produttività"; else -> "Altro"
        } else "App"
    }

    private fun getCat(ai: ApplicationInfo) = catPrefs.getInt("app_cat_${ai.packageName}", if (Build.VERSION.SDK_INT >= 26) ai.category else -1)

    sealed class AppListItem { data class Header(val title: String, val id: Int) : AppListItem(); data class App(val res: ResolveInfo) : AppListItem() }
    inner class AppsAdapter(private val list: List<AppListItem>, private val onClick: (ResolveInfo) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(p: Int) = if (list[p] is AppListItem.Header) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, t: Int) = if (t == 0) HeaderVH(LayoutInflater.from(parent.context).inflate(R.layout.header_item, parent, false)) else AppVH(LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false))
        override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
            val i = list[p]
            if (h is HeaderVH && i is AppListItem.Header) h.title.text = i.title
            else if (h is AppVH && i is AppListItem.App) { h.name.text = i.res.loadLabel(packageManager); h.icon.setImageDrawable(i.res.loadIcon(packageManager)); h.itemView.setOnClickListener { onClick(i.res) } }
        }
        override fun getItemCount() = list.size
        inner class HeaderVH(v: View) : RecyclerView.ViewHolder(v) { val title: TextView = v.findViewById(R.id.header_title) }
        inner class AppVH(v: View) : RecyclerView.ViewHolder(v) { val icon: ImageView = v.findViewById(R.id.app_icon); val name: TextView = v.findViewById(R.id.app_name) }
    }
    inner class CategoriesAdapter(private val list: List<Int>, private val onClick: (Int) -> Unit, private val onLongClick: (Int) -> Unit) : RecyclerView.Adapter<CategoriesAdapter.VH>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.category_item, p, false))
        override fun onBindViewHolder(h: VH, p: Int) { val id = list[p]; h.name.text = getCatName(id); h.itemView.setOnClickListener { onClick(id) }; h.itemView.setOnLongClickListener { onLongClick(id); true } }
        override fun getItemCount() = list.size
        inner class VH(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.category_name) }
    }
}