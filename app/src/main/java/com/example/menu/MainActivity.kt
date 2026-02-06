package com.example.menu

import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var categoryPrefs: SharedPreferences
    
    // Estese a 6 tasti
    private val iconKeys = listOf("icon_1_pkg", "icon_2_pkg", "icon_3_pkg", "icon_4_pkg", "icon_5_pkg", "icon_6_pkg")
    private val defaultPackages = listOf(
        "com.whatsapp", 
        "de.volkswagen.mapsandmore", 
        "org.prowl.torque", 
        "com.android.settings",
        "com.android.settings", // Default per tasto 5
        "com.android.settings"  // Default per tasto 6
    )
    
    private val wedgeViews by lazy {
        listOf<WedgeImageView>(
            findViewById(R.id.wedge_1), findViewById(R.id.wedge_2), findViewById(R.id.wedge_3),
            findViewById(R.id.wedge_4), findViewById(R.id.wedge_5), findViewById(R.id.wedge_6)
        )
    }

    private lateinit var gestureDetector: GestureDetector
    
    private var allInstalledApps: List<ResolveInfo> = emptyList()
    private var isAppCacheReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        prefs = getSharedPreferences("app_shortcuts", Context.MODE_PRIVATE)
        categoryPrefs = getSharedPreferences("custom_categories", Context.MODE_PRIVATE)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e1.y - e2.y > 100 && abs(velocityY) > 100) {
                    showAppsList(isManagementMode = false)
                    return true
                }
                return false
            }
        })

        loadAppsToCache()
        setupClickListeners()
        setupWedges()
        checkDefaultLauncher()
    }

    private fun loadAppsToCache() {
        lifecycleScope.launch(Dispatchers.Default) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            allInstalledApps = packageManager.queryIntentActivities(mainIntent, 0)
            isAppCacheReady = true
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun setupWedges() {
        val startAngles = listOf(-120f, -60f, 0f, 60f, 120f, 180f)
        
        for (i in wedgeViews.indices) {
            val wedge = wedgeViews[i]
            wedge.startAngle = startAngles[i]
            
            val pkg = prefs.getString(iconKeys[i], defaultPackages[i])!!
            
            try {
                wedge.setImageDrawable(packageManager.getApplicationIcon(pkg))
            } catch (e: Exception) {
                wedge.setImageResource(R.drawable.icona)
            }
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.center_helper).setOnClickListener {
            finishAndRemoveTask()
        }

        // Ora tutti e 6 i tasti gestiscono l'avvio delle app e la personalizzazione
        for (i in wedgeViews.indices) {
            val wedge = wedgeViews[i]
            val key = iconKeys[i]
            
            wedge.setOnClickListener { 
                val packageName = prefs.getString(key, defaultPackages[i])!!
                launchApp(packageName, "App") 
            }
            wedge.setOnLongClickListener { 
                showAppPickerFor(i, key)
                true 
            }
        }
    }

    private fun checkDefaultLauncher() {
        if (!isDefaultLauncher()) {
            AlertDialog.Builder(this)
                .setTitle("Launcher Predefinito")
                .setMessage("Vuoi impostare questa app come launcher predefinito?")
                .setPositiveButton("Sì") { _, _ ->
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo != null && packageName == resolveInfo.activityInfo.packageName
    }

    private fun getEffectiveCategory(appInfo: ApplicationInfo): Int {
        return categoryPrefs.getInt("app_cat_${appInfo.packageName}", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appInfo.category else -1)
    }

    private fun showAppsList(filterCategory: Int? = null, isManagementMode: Boolean = false) {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.apps_list_dialog)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.apps_recycler_view)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        val quickLinksContainer = dialog.findViewById<LinearLayout>(R.id.category_quick_links)

        val apps = if (isAppCacheReady) allInstalledApps else {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(mainIntent, 0)
        }

        var filteredApps = apps
        if (filterCategory != null) {
            filteredApps = apps.filter { getEffectiveCategory(it.activityInfo.applicationInfo) == filterCategory }
        }

        val categoryPositions = mutableMapOf<Int, Int>()
        val groupedApps = mutableListOf<AppListItem>()

        if (filterCategory == null) {
            val categoriesMap = mutableMapOf<Int, MutableList<ResolveInfo>>()
            for (app in filteredApps) {
                val cat = getEffectiveCategory(app.activityInfo.applicationInfo)
                categoriesMap.getOrPut(cat) { mutableListOf() }.add(app)
            }
            categoriesMap.keys.sorted().forEach { catId ->
                categoryPositions[catId] = groupedApps.size
                groupedApps.add(AppListItem.Header(getCategoryName(catId), catId))
                categoriesMap[catId]!!.sortBy { it.loadLabel(packageManager).toString().lowercase() }
                for (app in categoriesMap[catId]!!) groupedApps.add(AppListItem.App(app))
            }
        } else {
            val sortedFiltered = filteredApps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            sortedFiltered.forEach { groupedApps.add(AppListItem.App(it)) }
            quickLinksContainer.visibility = View.GONE
        }

        if (filterCategory == null) {
            categoryPositions.keys.sorted().forEach { catId ->
                val textView = TextView(this).apply {
                    text = getCategoryName(catId)
                    setPadding(24, 12, 24, 12)
                    setTextColor(resources.getColor(android.R.color.darker_gray, null))
                    setOnClickListener { 
                        layoutManager.scrollToPositionWithOffset(categoryPositions[catId] ?: 0, 0)
                    }
                }
                quickLinksContainer.addView(textView)
            }
            
            val settingsView = TextView(this).apply {
                text = "Impostazioni"
                setPadding(24, 12, 24, 12)
                setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
                setOnClickListener {
                    dialog.dismiss()
                    showCategoriesList()
                }
            }
            quickLinksContainer.addView(settingsView)
        }
        
        recyclerView.adapter = AppsAdapter(groupedApps) { resolveInfo ->
            if (isManagementMode) {
                showMoveToCategoryDialog(resolveInfo) { dialog.dismiss(); showAppsList(filterCategory, true) }
            } else {
                launchApp(resolveInfo.activityInfo.packageName, resolveInfo.loadLabel(packageManager).toString())
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showCategoriesList() {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.categories_list_dialog)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.categories_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val apps = if (isAppCacheReady) allInstalledApps else {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(mainIntent, 0)
        }
        
        val categoriesSet = apps.map { getEffectiveCategory(it.activityInfo.applicationInfo) }.toSet()
        val sortedCategories = categoriesSet.toList().sorted()

        recyclerView.adapter = CategoriesAdapter(sortedCategories, 
            onClick = { id -> dialog.dismiss(); showAppsList(id, isManagementMode = true) },
            onLongClick = { id -> showRenameCategoryDialog(id) { dialog.dismiss(); showCategoriesList() } }
        )
        dialog.show()
    }

    private fun showRenameCategoryDialog(categoryId: Int, onComplete: () -> Unit) {
        val editText = EditText(this).apply { setText(getCategoryName(categoryId)) }
        AlertDialog.Builder(this)
            .setTitle("Rinomina Categoria")
            .setView(editText)
            .setPositiveButton("Salva") { _, _ ->
                categoryPrefs.edit().putString("cat_name_$categoryId", editText.text.toString()).apply()
                onComplete()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showMoveToCategoryDialog(resolveInfo: ResolveInfo, onComplete: () -> Unit) {
        val categories = listOf(
            ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_PRODUCTIVITY, -1
        ).distinct()
        
        val names = categories.map { getCategoryName(it) }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Sposta in...")
            .setItems(names) { _, which ->
                categoryPrefs.edit().putInt("app_cat_${resolveInfo.activityInfo.packageName}", categories[which]).apply()
                onComplete()
            }
            .show()
    }

    private fun getCategoryName(category: Int): String {
        val customName = categoryPrefs.getString("cat_name_$category", null)
        if (customName != null) return customName
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (category) {
                ApplicationInfo.CATEGORY_GAME -> "Giochi"
                ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                ApplicationInfo.CATEGORY_VIDEO -> "Video"
                ApplicationInfo.CATEGORY_IMAGE -> "Immagini"
                ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                ApplicationInfo.CATEGORY_NEWS -> "News"
                ApplicationInfo.CATEGORY_MAPS -> "Mappe"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produttività"
                else -> "Altro"
            }
        } else "Applicazioni"
    }

    private fun launchApp(packageName: String, appName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            try { startActivity(intent); finishAndRemoveTask() } 
            catch (e: Exception) { Toast.makeText(this, "$appName non installato", Toast.LENGTH_SHORT).show() }
        } else Toast.makeText(this, "$appName non installato", Toast.LENGTH_SHORT).show()
    }

    private fun loadIconDrawables() {
        for (i in iconKeys.indices) {
            val pkg = prefs.getString(iconKeys[i], defaultPackages[i])!!
            try { 
                val icon = packageManager.getApplicationIcon(pkg)
                wedgeViews[i].setImageDrawable(icon)
            } catch (e: Exception) { 
                wedgeViews[i].setImageResource(R.drawable.icona) 
            }
        }
    }

    private fun showAppPickerFor(index: Int, key: String) {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.apps_list_dialog)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.apps_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val apps = if (isAppCacheReady) allInstalledApps else {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(mainIntent, 0)
        }
        
        val sortedApps = apps.sortedBy { it.loadLabel(packageManager).toString() }
        
        recyclerView.adapter = AppsAdapter(sortedApps.map { AppListItem.App(it) }) { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            prefs.edit().putString(key, pkg).apply()
            try { 
                val icon = packageManager.getApplicationIcon(pkg)
                wedgeViews[index].setImageDrawable(icon)
            } catch (e: Exception) { 
                wedgeViews[index].setImageResource(R.drawable.icona)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    sealed class AppListItem {
        data class Header(val title: String, val id: Int) : AppListItem()
        data class App(val resolveInfo: ResolveInfo) : AppListItem()
    }

    inner class AppsAdapter(private val items: List<AppListItem>, private val onClick: (ResolveInfo) -> Unit) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int) = if (items[position] is AppListItem.Header) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (viewType == 0) 
            HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_item, parent, false))
            else AppViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is HeaderViewHolder && item is AppListItem.Header) holder.title.text = item.title
            else if (holder is AppViewHolder && item is AppListItem.App) {
                holder.name.text = item.resolveInfo.loadLabel(packageManager)
                holder.icon.setImageDrawable(item.resolveInfo.loadIcon(packageManager))
                holder.itemView.setOnClickListener { onClick(item.resolveInfo) }
            }
        }
        override fun getItemCount() = items.size
        inner class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) { val title: TextView = v.findViewById(R.id.header_title) }
        inner class AppViewHolder(v: View) : RecyclerView.ViewHolder(v) { val icon: ImageView = v.findViewById(R.id.app_icon); val name: TextView = v.findViewById(R.id.app_name) }
    }

    inner class CategoriesAdapter(private val categories: List<Int>, private val onClick: (Int) -> Unit, private val onLongClick: (Int) -> Unit) :
        RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = ViewHolder(LayoutInflater.from(p.context).inflate(R.layout.category_item, p, false))
        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val id = categories[p]
            h.name.text = getCategoryName(id)
            h.itemView.setOnClickListener { onClick(id) }
            h.itemView.setOnLongClickListener { onLongClick(id); true }
        }
        override fun getItemCount() = categories.size
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) { val name: TextView = v.findViewById(R.id.category_name) }
    }
}