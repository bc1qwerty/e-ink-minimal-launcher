package com.palma.minimal.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDateBattery: TextView
    private lateinit var tvAllApps: TextView
    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var btnRefreshScreen: View
    private lateinit var ivSettings: ImageView
    private lateinit var indexBarLayout: LinearLayout
    
    private lateinit var appAdapter: AppAdapter
    private var allAppsList = mutableListOf<AppInfo>()
    private var favoriteAppsList = mutableListOf<AppInfo>()
    
    private lateinit var prefs: SharedPreferences
    private var isShowingAllApps = false

    companion object {
        private const val TAG = "PalmaLauncher"
        private const val PREFS_NAME = "LauncherPrefs"
        private const val KEY_FAVORITES = "favorites_list"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        setupRecyclerView()
        loadApps()
        setupIndexBar()
        updateHeader()
        setupListeners()
    }

    private fun initViews() {
        tvTime = findViewById(R.id.tvTime)
        tvDateBattery = findViewById(R.id.tvDateBattery)
        tvAllApps = findViewById(R.id.tvAllApps)
        recyclerViewApps = findViewById(R.id.recyclerViewApps)
        btnRefreshScreen = findViewById(R.id.btnRefreshScreen)
        ivSettings = findViewById(R.id.ivSettings)
        indexBarLayout = findViewById(R.id.indexBarLayout)
        
        tvAllApps.text = "즐겨찾기"
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter(mutableListOf(), this::onAppClicked, this::onAppLongClicked, this::onOrderChanged)
        
        recyclerViewApps.layoutManager = GridLayoutManager(this, 2)
        recyclerViewApps.adapter = appAdapter
        recyclerViewApps.itemAnimator = null

        recyclerViewApps.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                recyclerViewApps.viewTreeObserver.removeOnGlobalLayoutListener(this)
                calculateItemHeight()
            }
        })

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                if (isShowingAllApps) return false
                appAdapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = !isShowingAllApps
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerViewApps)
    }

    private fun setupIndexBar() {
        indexBarLayout.removeAllViews()
        val alphabets = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray()
        
        alphabets.forEach { char ->
            val tv = TextView(this).apply {
                text = char.toString()
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 4)
                setTextColor(0xFF000000.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                setOnClickListener {
                    scrollToLetter(char)
                }
            }
            indexBarLayout.addView(tv)
        }
        indexBarLayout.visibility = View.GONE
    }

    private fun scrollToLetter(letter: Char) {
        if (!isShowingAllApps) return
        val position = allAppsList.indexOfFirst { 
            val firstChar = it.name.uppercase().firstOrNull() ?: ' '
            if (letter == '#') !firstChar.isLetter() else firstChar == letter
        }
        if (position != -1) {
            (recyclerViewApps.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
        }
    }

    private fun calculateItemHeight() {
        if (!isShowingAllApps) {
            val totalHeight = recyclerViewApps.height
            if (totalHeight <= 0) return
            val rows = when {
                favoriteAppsList.size > 6 -> 4
                favoriteAppsList.size > 4 -> 3
                else -> 2
            }
            appAdapter.setItemHeight(totalHeight / rows)
        }
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pm: PackageManager = packageManager
        val apps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        allAppsList.clear()
        for (app in apps) {
            val packageName = app.activityInfo.packageName
            if (packageName == this.packageName) continue
            val name = app.loadLabel(pm).toString()
            val icon = app.loadIcon(pm)
            allAppsList.add(AppInfo(name, packageName, icon))
        }
        allAppsList.sortBy { it.name.lowercase() }
        filterFavorites()
    }

    private fun filterFavorites() {
        val favString = prefs.getString(KEY_FAVORITES, "") ?: ""
        val favPackageList = if (favString.isEmpty()) mutableListOf() else favString.split(",").toMutableList()
        favoriteAppsList.clear()
        favPackageList.forEach { pkg ->
            allAppsList.find { it.packageName == pkg }?.let { favoriteAppsList.add(it) }
        }
        if (favoriteAppsList.isEmpty() && allAppsList.isNotEmpty()) {
            favoriteAppsList.addAll(allAppsList.take(6))
            saveFavorites()
        }
        updateDisplayList()
    }

    private fun saveFavorites() {
        val favString = favoriteAppsList.joinToString(",") { it.packageName }
        prefs.edit().putString(KEY_FAVORITES, favString).apply()
    }

    private fun updateDisplayList() {
        if (isShowingAllApps) {
            recyclerViewApps.layoutManager = LinearLayoutManager(this)
            appAdapter.updateData(allAppsList, false)
            indexBarLayout.visibility = View.VISIBLE
            tvAllApps.text = "모든 앱"
        } else {
            recyclerViewApps.layoutManager = GridLayoutManager(this, 2)
            calculateItemHeight()
            appAdapter.updateData(favoriteAppsList, true)
            indexBarLayout.visibility = View.GONE
            tvAllApps.text = "즐겨찾기"
        }
    }

    private fun onOrderChanged(newList: List<AppInfo>) {
        if (!isShowingAllApps) {
            favoriteAppsList = newList.toMutableList()
            saveFavorites()
        }
    }

    private fun updateHeader() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일 (E)", Locale.getDefault())
        val currentTime = Date()
        
        tvTime.text = timeFormat.format(currentTime)
        
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        tvDateBattery.text = String.format("%s | %d%%", dateFormat.format(currentTime), batLevel)
    }

    private fun setupListeners() {
        tvAllApps.setOnClickListener {
            isShowingAllApps = !isShowingAllApps
            updateDisplayList()
        }

        ivSettings.setOnClickListener {
            showSettingsMenu()
        }

        btnRefreshScreen.setOnClickListener {
            window.decorView.invalidate()
        }

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_TICK)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateHeader()
            }
        }, filter)
    }

    private fun showSettingsMenu() {
        val options = arrayOf("앱 정보", "기본 런처 설정", "런처 삭제", "런처 재시작", "개인정보취급방침", "서비스이용약관", "오픈소스 라이선스", "GitHub 저장소")
        
        AlertDialog.Builder(this)
            .setTitle("런처 설정")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openAppInfo()
                    1 -> openDefaultLauncherSettings()
                    2 -> uninstallLauncher()
                    3 -> restartLauncher()
                    4 -> openUrl("https://github.com/Seo-Young-Seok/palma-minimal-launcher/blob/main/PRIVACY.md")
                    5 -> openUrl("https://github.com/Seo-Young-Seok/palma-minimal-launcher/blob/main/TERMS.md")
                    6 -> openUrl("https://github.com/Seo-Young-Seok/palma-minimal-launcher/blob/main/LICENSE")
                    7 -> openUrl("https://github.com/Seo-Young-Seok/palma-minimal-launcher")
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun openAppInfo() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:\$packageName")
        }
        startActivity(intent)
    }

    private fun openDefaultLauncherSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }

    private fun uninstallLauncher() {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:\$packageName")
        }
        startActivity(intent)
    }

    private fun restartLauncher() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "URL을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onAppClicked(appInfo: AppInfo) {
        val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
        }
    }

    private fun onAppLongClicked(appInfo: AppInfo) {
        if (isShowingAllApps) {
            val isFav = favoriteAppsList.any { it.packageName == appInfo.packageName }
            val actionText = if (isFav) "즐겨찾기에서 제거" else "즐겨찾기에 추가"
            
            AlertDialog.Builder(this)
                .setTitle(appInfo.name)
                .setMessage(actionText + "하시겠습니까?")
                .setPositiveButton("확인") { _, _ ->
                    if (isFav) {
                        favoriteAppsList.removeAll { it.packageName == appInfo.packageName }
                    } else {
                        favoriteAppsList.add(appInfo)
                    }
                    saveFavorites()
                    Toast.makeText(this, if (isFav) "제거됨" else "추가됨", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }
}
