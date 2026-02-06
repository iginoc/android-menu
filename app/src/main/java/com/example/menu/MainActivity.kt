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
import android.view.LayoutInflater
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var categoryPrefs: SharedPreferences
    private val iconKeys = listOf("icon_1_pkg", "icon_2_pkg", "icon_3_pkg", "icon_4_pkg")
    private val defaultPackages = listOf("com.whatsapp", "de.volkswagen.mapsandmore", "org.prowl.torque", "com.google.android.apps.maps")
    private val iconViews by lazy {
        listOf<ImageView>(findViewById(R.id.icon_1), findViewById(R.id.icon_2), findViewById(R.id.icon_3), findViewById(R.id.icon_4))
    }
    private val wedgeViews by lazy {
        listOf<WedgeImageView>(
            findViewById(R.id.wedge_1), findViewById(R.id.wedge_2), findViewById(R.id.wedge_3),
            findViewById(R.id.wedge_4), findViewById(R.id.wedge_5), findViewById(R.id.wedge_6)
        )
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("car_image_uri", uri.toString()).apply()
                loadCarImage(uri)
            }
        }
    }

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

        setupClickListeners()
        loadIconDrawables()
        initCarImage()
        setupWedges()
        checkDefaultLauncher()
    }

    private fun setupWedges() {
        // Correzione angoli: ConstraintLayout 0° è Canvas 270° (-90°).
        // Lo spicchio è largo 60°, quindi per centrarlo dobbiamo togliere altri 30°.
        // 270 - 30 = 240 (o -120).
        val startAngles = listOf(-120f, -60f, 0f, 60f, 120f, 180f)
        
        for (i in wedgeViews.indices) {
            val wedge = wedgeViews[i]
            wedge.startAngle = startAngles[i]
            
            val pkg = when(i) {
                in 0..3 -> prefs.getString(iconKeys[i], defaultPackages[i])!!
                4 -> "com.android.settings"
                else -> "com.android.systemui"
            }
            
            try {
                wedge.setImageDrawable(packageManager.getApplicationIcon(pkg))
            } catch (e: Exception) {
                wedge.setImageResource(R.drawable.icona)
            }
        }
    }

    private fun checkDefaultLauncher() {
        if (!isDefaultLauncher()) {
            AlertDialog.Builder(this)
                .setTitle("Launcher Predefinito")
                .setMessage("Vuoi impostare questa app come launcher predefinito?")
                .setPositiveButton("Sì") { _, _ ->
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    startActivity(intent)
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

    private fun setupClickListeners() {
        val carImageView = findViewById<ImageView>(R.id.car_image)
        carImageView.setOnClickListener { finishAndRemoveTask() }
        carImageView.setOnLongClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }
            pickImageLauncher.launch(intent)
            true
        }

        for (i in iconKeys.indices) {
            val iconView = iconViews[i]
            val key = iconKeys[i]
            iconView.setOnClickListener { launchApp(prefs.getString(key, defaultPackages[i])!!, "App") }
            iconView.setOnLongClickListener { showAppPickerFor(i, key); true }
        }

        findViewById<ImageView>(R.id.icon_5).setOnClickListener { showAppsList(isManagementMode = false) }
        findViewById<ImageView>(R.id.icon_6).setOnClickListener { showCategoriesList() }
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

        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        var apps = packageManager.queryIntentActivities(mainIntent, 0)

        val categoryPositions = mutableMapOf<Int, Int>()
        val groupedApps = mutableListOf<AppListItem>()

        if (filterCategory == null) {
            val categoriesMap = mutableMapOf<Int, MutableList<ResolveInfo>>()
            for (app in apps) {
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
            apps = apps.filter { getEffectiveCategory(it.activityInfo.applicationInfo) == filterCategory }
            apps.sortBy { it.loadLabel(packageManager).toString().lowercase() }
            apps.forEach { groupedApps.add(AppListItem.App(it)) }
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

        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(mainIntent, 0)
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

    private fun initCarImage() {
        prefs.getString("car_image_uri", null)?.let { try { loadCarImage(Uri.parse(it)) } catch (e: Exception) {} }
    }

    private fun loadCarImage(uri: Uri) {
        findViewById<ImageView>(R.id.car_image).setImageURI(uri)
    }

    private fun loadIconDrawables() {
        for (i in iconKeys.indices) {
            val pkg = prefs.getString(iconKeys[i], defaultPackages[i])!!
            try { 
                val icon = packageManager.getApplicationIcon(pkg)
                iconViews[i].setImageDrawable(icon)
            } catch (e: Exception) { 
                iconViews[i].setImageResource(R.drawable.icona) 
            }
        }
    }

    private fun showAppPickerFor(index: Int, key: String) {
        val dialog = Dialog(this, android.R.style.Theme_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.apps_list_dialog)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.apps_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        apps.sortBy { it.loadLabel(packageManager).toString() }
        recyclerView.adapter = AppsAdapter(apps.map { AppListItem.App(it) }) { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            prefs.edit().putString(key, pkg).apply()
            try { 
                val icon = packageManager.getApplicationIcon(pkg)
                iconViews[index].setImageDrawable(icon)
                wedgeViews[index].setImageDrawable(icon)
            } catch (e: Exception) { 
                iconViews[index].setImageResource(R.drawable.icona)
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