package com.palma.minimal.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
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
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var tvDateBattery: TextView
    private lateinit var tvAllApps: TextView
    private lateinit var recyclerViewApps: RecyclerView
    private lateinit var btnRefreshScreen: View
    private lateinit var ivSettings: ImageView
    private lateinit var indexBarLayout: LinearLayout
    private lateinit var indexBarContainer: View
    
    private lateinit var appAdapter: AppAdapter
    private var allAppsList = mutableListOf<AppInfo>()
    private var favoriteAppsList = mutableListOf<AppInfo>()
    private var filteredAppsList = mutableListOf<AppInfo>()
    
    private lateinit var prefs: SharedPreferences
    private var isShowingAllApps = false
    private var currentSelectedIndex = -1
    private var availableIndices = mutableListOf<String>()
    
    private var favColumns = 2

    companion object {
        private const val TAG = "PalmaLauncher"
        private const val PREFS_NAME = "LauncherPrefs"
        private const val KEY_FAVORITES = "favorites_list"
        private const val KEY_COLUMNS = "fav_columns"
        private val POTENTIAL_INDICES = listOf("ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ", 
                                              "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        favColumns = prefs.getInt(KEY_COLUMNS, 2)

        initViews()
        setupRecyclerView()
        loadApps()
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
        indexBarContainer = findViewById(R.id.indexBarContainer)
        
        tvAllApps.text = "즐겨찾기"
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter(mutableListOf(), this::onAppClicked, this::onAppLongClicked, this::onOrderChanged)
        recyclerViewApps.adapter = appAdapter
        recyclerViewApps.itemAnimator = null
        updateDisplayList()

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
        availableIndices.clear()
        POTENTIAL_INDICES.forEach { label ->
            val hasApp = allAppsList.any { app ->
                val firstChar = app.name.firstOrNull() ?: return@any false
                val initial = getInitialSound(firstChar)
                if (label == "#") {
                    !initial[0].isLetter() && !isKorean(firstChar)
                } else if (label.length == 1 && isKoreanConsonant(label[0])) {
                    initial == label
                } else {
                    app.name.uppercase().startsWith(label)
                }
            }
            if (hasApp) availableIndices.add(label)
        }

        availableIndices.forEach { label ->
            val tv = TextView(this).apply {
                text = label
                textSize = 24f // 2x larger
                setTypeface(null, Typeface.BOLD) // Bold
                gravity = Gravity.CENTER
                setTextColor(0xFF888888.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            indexBarLayout.addView(tv)
        }

        indexBarLayout.setOnTouchListener { v, event ->
            if (!isShowingAllApps) return@setOnTouchListener false
            val y = event.y
            val height = v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (height > 0) {
                        val index = ((y / height) * availableIndices.size).toInt()
                        if (index >= 0 && index < availableIndices.size) {
                            if (index != currentSelectedIndex) {
                                currentSelectedIndex = index
                                filterAppsByLabel(availableIndices[index])
                            }
                            applyWaveEffect(index)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resetWaveEffect()
                    true
                }
                else -> false
            }
        }
        indexBarContainer.visibility = if (isShowingAllApps) View.VISIBLE else View.GONE
    }

    private fun applyWaveEffect(selectedIndex: Int) {
        for (i in 0 until indexBarLayout.childCount) {
            val tv = indexBarLayout.getChildAt(i) as TextView
            val dist = abs(i - selectedIndex)
            val translationX = when (dist) {
                0 -> -300f // 3x stronger
                1 -> -180f
                2 -> -90f
                3 -> -45f
                else -> 0f
            }
            tv.translationX = translationX
            if (dist == 0) {
                tv.setTextColor(0xFF000000.toInt())
                tv.textSize = 32f // Larger when selected
            } else {
                tv.setTextColor(0xFF888888.toInt())
                tv.textSize = 24f
            }
        }
    }

    private fun resetWaveEffect() {
        for (i in 0 until indexBarLayout.childCount) {
            val tv = indexBarLayout.getChildAt(i) as TextView
            tv.translationX = 0f
            tv.setTextColor(0xFF888888.toInt())
            tv.textSize = 24f
        }
        currentSelectedIndex = -1
    }

    private fun filterAppsByLabel(label: String) {
        if (!isShowingAllApps) return
        filteredAppsList.clear()
        allAppsList.forEach { app ->
            val firstChar = app.name.firstOrNull() ?: return@forEach
            val initial = getInitialSound(firstChar)
            val match = if (label == "#") {
                !initial[0].isLetter() && !isKorean(firstChar)
            } else if (label.length == 1 && isKoreanConsonant(label[0])) {
                initial == label
            } else {
                app.name.uppercase().startsWith(label)
            }
            if (match) filteredAppsList.add(app)
        }
        appAdapter.updateData(filteredAppsList, false)
    }

    private fun getInitialSound(c: Char): String {
        val KOREAN_BEGIN = 0xAC00
        val KOREAN_END = 0xD7A3
        val CONSONANTS = arrayOf("ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
        if (c.code in KOREAN_BEGIN..KOREAN_END) {
            val index = (c.code - KOREAN_BEGIN) / 28 / 21
            return CONSONANTS[index]
        }
        return c.uppercaseChar().toString()
    }

    private fun isKorean(c: Char): Boolean = c.code in 0xAC00..0xD7A3 || c.code in 0x3131..0x318E
    private fun isKoreanConsonant(c: Char): Boolean = c.code in 0x3131..0x314E

    private fun calculateItemHeight() {
        if (!isShowingAllApps) {
            val totalHeight = recyclerViewApps.height
            if (totalHeight <= 0) return
            // Max apps logic: 1col:4, 2col:8, 3col:12. All lead to 4 rows.
            val rows = 4
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
        
        // Apply Limit based on columns
        val limit = favColumns * 4
        if (favoriteAppsList.size > limit) {
            favoriteAppsList = favoriteAppsList.take(limit).toMutableList()
        }
        
        if (favoriteAppsList.isEmpty() && allAppsList.isNotEmpty()) {
            favoriteAppsList.addAll(allAppsList.take(limit.coerceAtMost(allAppsList.size)))
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
            setupIndexBar()
            indexBarContainer.visibility = View.VISIBLE
            tvAllApps.text = "모든 앱"
            // Add padding to avoid index bar overlap
            recyclerViewApps.setPadding(24, 0, 60, 0)
        } else {
            recyclerViewApps.layoutManager = GridLayoutManager(this, favColumns)
            calculateItemHeight()
            appAdapter.updateData(favoriteAppsList, true)
            indexBarContainer.visibility = View.GONE
            tvAllApps.text = "즐겨찾기"
            recyclerViewApps.setPadding(24, 0, 24, 0) // Balanced padding
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
        ivSettings.setOnClickListener { showSettingsMenu() }
        btnRefreshScreen.setOnClickListener { window.decorView.invalidate() }
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_TICK)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { updateHeader() }
        }, filter)
    }

    private fun showSettingsMenu() {
        val options = arrayOf("즐겨찾기 배치 설정", "앱 정보", "기본 런처 설정", "런처 삭제", "런처 재시작", "개인정보취급방침", "서비스이용약관", "GitHub 저장소")
        AlertDialog.Builder(this)
            .setTitle("런처 설정")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showColumnSettings()
                    1 -> openAppInfo()
                    2 -> openDefaultLauncherSettings()
                    3 -> uninstallLauncher()
                    4 -> restartLauncher()
                    5 -> openUrl("https://github.com/bc1qwerty/palma-minimal-launcher/blob/main/PRIVACY.md")
                    6 -> openUrl("https://github.com/bc1qwerty/palma-minimal-launcher/blob/main/TERMS.md")
                    7 -> openUrl("https://github.com/bc1qwerty/palma-minimal-launcher")
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showColumnSettings() {
        val cols = arrayOf("1단 (최대 4개)", "2단 (최대 8개)", "3단 (최대 12개)")
        AlertDialog.Builder(this)
            .setTitle("즐겨찾기 배치 선택")
            .setSingleChoiceItems(cols, favColumns - 1) { dialog, which ->
                favColumns = which + 1
                prefs.edit().putInt(KEY_COLUMNS, favColumns).apply()
                filterFavorites() // Will apply limits and refresh
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppInfo() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:" + packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "설정을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDefaultLauncherSettings() { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
    private fun uninstallLauncher() { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName))) }
    private fun restartLauncher() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } 
        catch (e: Exception) { Toast.makeText(this, "URL을 열 수 없습니다.", Toast.LENGTH_SHORT).show() }
    }
    private fun onAppClicked(appInfo: AppInfo) {
        packageManager.getLaunchIntentForPackage(appInfo.packageName)?.let {
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
                    if (isFav) favoriteAppsList.removeAll { it.packageName == appInfo.packageName }
                    else {
                        if (favoriteAppsList.size < favColumns * 4) {
                            favoriteAppsList.add(appInfo)
                        } else {
                            Toast.makeText(this, "즐겨찾기가 가득 찼습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    saveFavorites()
                    updateDisplayList()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }
}
