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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var categoryPrefs: SharedPreferences
    private val iconKeys = listOf("icon_1_pkg", "icon_2_pkg", "icon_3_pkg", "icon_4_pkg", "icon_5_pkg", "icon_6_pkg")
    private val defaultPackages = listOf(
        "com.whatsapp", "de.volkswagen.mapsandmore", "org.prowl.torque",
        "com.android.settings", "com.android.settings", "com.android.settings"
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
    private var recognizer: DigitalInkRecognizer? = null

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

        setupGestureDetector()
        setupDrawingRecognition()
        loadAppsToCache()
        setupClickListeners()
        setupWedges()
        checkDefaultLauncher()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null) {
                    val screenHeight = resources.displayMetrics.heightPixels
                    val bottomThreshold = screenHeight * 0.9
                    if (e1.y > bottomThreshold && e1.y - e2.y > 100 && abs(velocityY) > 100) {
                        showAppsList(isManagementMode = false)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupDrawingRecognition() {
        val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("it-IT") ?: return
        val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
        val remoteModelManager = RemoteModelManager.getInstance()
        recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build())
        remoteModelManager.download(model, DownloadConditions.Builder().build())
        findViewById<DrawingView>(R.id.drawing_view).onStrokeFinished = { ink -> recognizeInk(ink) }
    }

    private fun recognizeInk(ink: Ink) {
        recognizer?.recognize(ink)?.addOnSuccessListener { result ->
            if (result.candidates.isNotEmpty()) {
                val text = result.candidates[0].text
                if (text.isNotEmpty()) showAppsList(initialLetter = text[0].lowercaseChar())
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun loadAppsToCache() {
        lifecycleScope.launch(Dispatchers.Default) {
            allInstalledApps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            isAppCacheReady = true
        }
    }

    private fun setupWedges() {
        val startAngles = listOf(-120f, -60f, 0f, 60f, 120f, 180f)
        for (i in wedgeViews.indices) {
            wedgeViews[i].startAngle = startAngles[i]
            val pkg = prefs.getString(iconKeys[i], defaultPackages[i])!!
            try { wedgeViews[i].setImageDrawable(packageManager.getApplicationIcon(pkg)) } 
            catch (e: Exception) { wedgeViews[i].setImageResource(R.drawable.icona) }
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.center_helper).setOnClickListener { finishAndRemoveTask() }
        for (i in iconKeys.indices) {
            val wedge = wedgeViews[i]
            val key = iconKeys[i]
            wedge.setOnClickListener { launchApp(prefs.getString(key, defaultPackages[i])!!, "App") }
            wedge.setOnLongClickListener { showAppPickerFor(i, key); true }
        }
    }

    private fun checkDefaultLauncher() {
        if (!isDefaultLauncher()) {
            AlertDialog.Builder(this).setTitle("Launcher Predefinito").setMessage("Vuoi impostare questa app come launcher predefinito?")
                .setPositiveButton("Sì") { _, _ -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                .setNegativeButton("No", null).show()
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

    private fun showAppsList(filterCategory: Int? = null, isManagementMode: Boolean = false, initialLetter: Char? = null) {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.apps_list_dialog)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.apps_recycler_view)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        val quickLinksContainer = dialog.findViewById<LinearLayout>(R.id.category_quick_links)

        val apps = if (isAppCacheReady) allInstalledApps else packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        var filteredApps = if (filterCategory != null) apps.filter { getEffectiveCategory(it.activityInfo.applicationInfo) == filterCategory } else apps

        val categoryPositions = mutableMapOf<Int, Int>()
        val groupedApps = mutableListOf<AppListItem>()

        if (filterCategory == null) {
            val categoriesMap = mutableMapOf<Int, MutableList<ResolveInfo>>()
            for (app in filteredApps) categoriesMap.getOrPut(getEffectiveCategory(app.activityInfo.applicationInfo)) { mutableListOf() }.add(app)
            categoriesMap.keys.sorted().forEach { catId ->
                categoryPositions[catId] = groupedApps.size
                groupedApps.add(AppListItem.Header(getCategoryName(catId), catId))
                categoriesMap[catId]!!.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.forEach { groupedApps.add(AppListItem.App(it)) }
            }
        } else {
            filteredApps.sortedBy { it.loadLabel(packageManager).toString().lowercase() }.forEach { groupedApps.add(AppListItem.App(it)) }
            quickLinksContainer.visibility = View.GONE
        }

        if (filterCategory == null) {
            categoryPositions.keys.sorted().forEach { catId ->
                quickLinksContainer.addView(TextView(this).apply {
                    text = getCategoryName(catId); setPadding(24, 12, 24, 12); setTextColor(resources.getColor(android.R.color.darker_gray, null))
                    setOnClickListener { layoutManager.scrollToPositionWithOffset(categoryPositions[catId] ?: 0, 0) }
                })
            }
            quickLinksContainer.addView(TextView(this).apply {
                text = "Impostazioni"; setPadding(24, 12, 24, 12); setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
                setOnClickListener { dialog.dismiss(); showCategoriesList() }
            })
        }
        
        recyclerView.adapter = AppsAdapter(groupedApps) { resolveInfo ->
            if (isManagementMode) showMoveToCategoryDialog(resolveInfo) { dialog.dismiss(); showAppsList(filterCategory, true) }
            else { launchApp(resolveInfo.activityInfo.packageName, resolveInfo.loadLabel(packageManager).toString()); dialog.dismiss() }
        }

        if (initialLetter != null) {
            val pos = groupedApps.indexOfFirst { it is AppListItem.App && it.resolveInfo.loadLabel(packageManager).toString().lowercase().startsWith(initialLetter) }
            if (pos != -1) layoutManager.scrollToPositionWithOffset(pos, 0)
        }
        dialog.show()
    }

    private fun showCategoriesList() {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.categories_list_dialog)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.categories_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val apps = if (isAppCacheReady) allInstalledApps else packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val categoriesSet = apps.map { getEffectiveCategory(it.activityInfo.applicationInfo) }.toSet()
        recyclerView.adapter = CategoriesAdapter(categoriesSet.toList().sorted(), 
            onClick = { id -> dialog.dismiss(); showAppsList(id, isManagementMode = true) },
            onLongClick = { id -> showRenameCategoryDialog(id) { dialog.dismiss(); showCategoriesList() } }
        )
        dialog.show()
    }

    private fun showRenameCategoryDialog(categoryId: Int, onComplete: () -> Unit) {
        val editText = EditText(this).apply { setText(getCategoryName(categoryId)) }
        AlertDialog.Builder(this).setTitle("Rinomina Categoria").setView(editText)
            .setPositiveButton("Salva") { _, _ -> categoryPrefs.edit().putString("cat_name_$categoryId", editText.text.toString()).apply(); onComplete() }
            .setNegativeButton("Annulla", null).show()
    }

    private fun showMoveToCategoryDialog(resolveInfo: ResolveInfo, onComplete: () -> Unit) {
        val categories = listOf(ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_PRODUCTIVITY, -1).distinct()
        val names = categories.map { getCategoryName(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Sposta in...").setItems(names) { _, which ->
            categoryPrefs.edit().putInt("app_cat_${resolveInfo.activityInfo.packageName}", categories[which]).apply(); onComplete()
        }.show()
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

    private fun showAppPickerFor(index: Int, key: String) {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.apps_list_dialog)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.apps_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val apps = if (isAppCacheReady) allInstalledApps else packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        recyclerView.adapter = AppsAdapter(apps.sortedBy { it.loadLabel(packageManager).toString() }.map { AppListItem.App(it) }) { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            prefs.edit().putString(key, pkg).apply()
            try { wedgeViews[index].setImageDrawable(packageManager.getApplicationIcon(pkg)) } 
            catch (e: Exception) { wedgeViews[index].setImageResource(R.drawable.icona) }
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