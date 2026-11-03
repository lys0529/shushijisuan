package com.example.verticalmath

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

// ================= 数据模型 =================
data class Question(
    val a: Int, val b: Int,
    val p1D: List<Int>, val p1C: List<Int>,
    val p2D: List<Int>, val p2C: List<Int>,
    val addC: List<Int>, val ansD: List<Int>,
    val userP1: MutableList<Int?> = MutableList(p1D.size) { null },
    val userP2: MutableList<Int?> = MutableList(p2D.size) { null },
    val userAns: MutableList<Int?> = MutableList(ansD.size) { null },
    // 新增：用户输入的进位
    val userCarP1: MutableList<Int?> = MutableList(p1C.size) { null },
    val userCarP2: MutableList<Int?> = MutableList(p2C.size) { null },
    val userCarAdd: MutableList<Int?> = MutableList(addC.size) { null },
    var sub: Boolean = false, var cor: Boolean = false
)

// ================= ViewModel =================
class MathViewModel(application: Application) : AndroidViewModel(application) {
    val qs = MutableLiveData<List<Question>>()
    private val prefs = application.getSharedPreferences("MathApp", Context.MODE_PRIVATE)
    
    val todayTotal = MutableLiveData<Int>()
    val todayCorrect = MutableLiveData<Int>()
    val allTotal = MutableLiveData<Int>()

    init { 
        gen()
        loadStats()
    }
    
    fun gen() {
        val list = mutableListOf<Question>()
        for (i in 1..10) {
            val a = Random.nextInt(100, 1000)
            val b = Random.nextInt(10, 100)
            val b1 = b % 10; val b2 = b / 10
            val p1 = a * b1; val p2 = a * b2 * 10; val ans = a * b
            
            val p1D = p1.toString().map { it.digitToInt() }
            val p2D = p2.toString().map { it.digitToInt() }
            val ansD = ans.toString().map { it.digitToInt() }
            
            val p1C = calcC(a, b1)
            val p2C = calcC(a, b2)
            val addC = calcAddC(p1D, p2D)
            
            list.add(Question(a, b, p1D, p1C, p2D, p2C, addC, ansD))
        }
        qs.value = list
    }
    
    private fun calcC(a: Int, b: Int): List<Int> {
        var c = 0; val res = mutableListOf<Int>()
        for (ch in a.toString().reversed()) {
            val s = ch.digitToInt() * b + c
            c = s / 10; res.add(c)
        }
        while(res.size < a.toString().length) res.add(0)
        return res.reversed()
    }
    
    private fun calcAddC(p1: List<Int>, p2: List<Int>): List<Int> {
        val len = max(p1.size, p2.size)
        var c = 0; val res = mutableListOf<Int>()
        for (i in 0 until len) {
            val v1 = if (i < p1.size) p1[p1.size - 1 - i] else 0
            val v2 = if (i < p2.size) p2[p2.size - 1 - i] else 0
            c = (v1 + v2 + c) / 10; res.add(c)
        }
        return res.reversed()
    }

    private fun getTodayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    fun submitStat(isCorrect: Boolean) {
        val today = getTodayStr()
        var tTotal = prefs.getInt("tTotal", 0)
        var tCorrect = prefs.getInt("tCorrect", 0)
        var aTotal = prefs.getInt("aTotal", 0)
        val savedDate = prefs.getString("date", "") ?: ""

        if (savedDate != today) {
            tTotal = 0; tCorrect = 0
            prefs.edit().putString("date", today).apply()
        }
        tTotal++; aTotal++
        if (isCorrect) tCorrect++
        
        prefs.edit().putInt("tTotal", tTotal).putInt("tCorrect", tCorrect).putInt("aTotal", aTotal).apply()
        loadStats()
    }

    private fun loadStats() {
        val today = getTodayStr()
        val savedDate = prefs.getString("date", "") ?: ""
        if (savedDate == today) {
            todayTotal.value = prefs.getInt("tTotal", 0)
            todayCorrect.value = prefs.getInt("tCorrect", 0)
        } else {
            todayTotal.value = 0
            todayCorrect.value = 0
        }
        allTotal.value = prefs.getInt("aTotal", 0)
    }

    fun addWrong(q: Question) {
        val arrStr = prefs.getString("wrongbook", "[]")
        val arr = JSONArray(arrStr)
        val obj = JSONObject().apply {
            put("expr", "${q.a} × ${q.b}")
            put("ans", q.ansD.joinToString(""))
        }
        arr.put(obj)
        prefs.edit().putString("wrongbook", arr.toString()).apply()
    }

    fun getWrongBook(): List<Pair<String, String>> {
        val arrStr = prefs.getString("wrongbook", "[]")
        val arr = JSONArray(arrStr)
        val list = mutableListOf<Pair<String, String>>()
        for (i in arr.length() - 1 downTo 0) {
            val obj = arr.getJSONObject(i)
            list.add(Pair(obj.getString("expr"), obj.getString("ans")))
        }
        return list
    }

    fun clearWrongBook() {
        prefs.edit().putString("wrongbook", "[]").apply()
    }
}

// ================= 适配器 =================
class QAdapter(val list: List<Question>, val onUpdate: () -> Unit, val onStat: (Boolean) -> Unit) : RecyclerView.Adapter<QAdapter.VH>() {
    var activeCell: EditText? = null
    var activeList: MutableList<Int?>? = null
    var activeIndex: Int = -1

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val box: LinearLayout = v.findViewById(R.id.llVerticalContainer)
        val btn: Button = v.findViewById(R.id.btnSubmitSingle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_question, parent, false))

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val q = list[pos]
        holder.title.text = "${pos+1}. ${q.a} × ${q.b}"
        holder.box.removeAllViews()
        
        addFixedRow(holder.box, q.a.toString().map{it.digitToInt()}, false)
        addFixedRow(holder.box, q.b.toString().map{it.digitToInt()}, true)
        
        // P1 进位框 (可编辑)
        addCarryRowInput(holder.box, q.p1C, q.userCarP1, q.sub, Color.RED)
        // P1 结果行 (可编辑)
        addInputRow(holder.box, q.p1D, q.userP1, q.sub, false, Color.BLACK, false)
        
        // P2 进位框 (可编辑)
        addCarryRowInput(holder.box, q.p2C, q.userCarP2, q.sub, Color.RED)
        // P2 结果行 (可编辑)
        addInputRow(holder.box, q.p2D, q.userP2, q.sub, false, Color.BLACK, true)
        
        val line = View(holder.itemView.context).apply { 
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4).apply { setMargins(0,8,0,8) }
            setBackgroundColor(Color.parseColor("#94A3B8")) 
        }
        holder.box.addView(line)
        
        // 加法进位框 (可编辑，蓝色)
        addCarryRowInput(holder.box, q.addC, q.userCarAdd, q.sub, Color.BLUE)
        // 最终答案行 (可编辑)
        addInputRow(holder.box, q.ansD, q.userAns, q.sub, true, Color.BLUE, false)

        holder.btn.isEnabled = !q.sub
        holder.btn.text = if (q.sub) "✓ 已提交" else "提交本题"
        holder.btn.setOnClickListener {
            // 检查是否填写完整（包括计算的输入行）
            if (q.userP1.contains(null) || q.userAns.contains(null)) {
                Toast.makeText(it.context, "请填写完整计算过程和最终答案", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 严谨对错判定：过程必须对，最终答案也必须对
            var okP1 = true
            q.userP1.forEachIndexed { i, v -> if (v != q.p1D[i]) okP1 = false }
            
            var okP2 = true
            q.userP2.forEachIndexed { i, v -> 
                if (i == q.p2D.size - 1 && q.p2D[i] == 0) return@forEachIndexed
                if (v != q.p2D[i]) okP2 = false 
            }
            
            var okAns = true
            q.userAns.forEachIndexed { i, v -> if (v != q.ansD[i]) okAns = false }

            q.cor = okP1 && okP2 && okAns
            q.sub = true
            onStat(q.cor)
            notifyItemChanged(pos)
            onUpdate()
        }
    }
    
    // 只读数字行
    private fun addFixedRow(parent: LinearLayout, digits: List<Int>, isOp: Boolean) {
        val row = LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        if (isOp) row.addView(TextView(parent.context).apply { text="×"; textSize=20f; setPadding(24,0,24,0) })
        digits.forEach { row.addView(TextView(parent.context).apply { text=it.toString(); textSize=24f; gravity=Gravity.CENTER; width=100 }) }
        parent.addView(row)
    }

    // 进位框输入行 (新增加)
    private fun addCarryRowInput(parent: LinearLayout, correctCarries: List<Int>, userCarries: MutableList<Int?>, sub: Boolean, textColor: Int) {
        val row = LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        correctCarries.forEachIndexed { i, _ ->
            val et = EditText(parent.context).apply {
                inputType = InputType.TYPE_NULL
                textSize = 14f; width = 100; gravity = Gravity.CENTER
                isEnabled = !sub
                setTextColor(textColor)
                // 设置成透明/无边框稍微小一点
                setBackgroundResource(android.R.drawable.editbox_background_normal)
                // 限制高度
                layoutParams = LinearLayout.LayoutParams(100, 80)
                if (sub) setText(correctCarries[i].toString()) // 提交后显示正确答案
                else if (userCarries[i] != null) setText(userCarries[i].toString())

                setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) { 
                        activeCell = this
                        activeList = userCarries
                        activeIndex = i 
                    }
                }
            }
            row.addView(et)
        }
        parent.addView(row)
    }

    // 数字输入行 (原版)
    private fun addInputRow(parent: LinearLayout, digits: List<Int>, userList: MutableList<Int?>, sub: Boolean, isAns: Boolean, textColor: Int, isP2: Boolean) {
        val row = LinearLayout(parent.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        digits.forEachIndexed { i, digit ->
            val isFixedZero = isP2 && (i == digits.size - 1) && (digit == 0)
            if (isFixedZero) {
                row.addView(TextView(parent.context).apply {
                    text = "0"; textSize = 24f; width = 100; gravity = Gravity.CENTER
                    setTextColor(Color.GRAY); setBackgroundResource(android.R.drawable.editbox_background_normal)
                    layoutParams = LinearLayout.LayoutParams(100, 120)
                })
            } else {
                val et = EditText(parent.context).apply {
                    inputType = InputType.TYPE_NULL; textSize = 24f; width = 100; gravity = Gravity.CENTER
                    isEnabled = !sub; setTextColor(textColor); setBackgroundResource(android.R.drawable.editbox_background_normal)
                    layoutParams = LinearLayout.LayoutParams(100, 120)
                    
                    if (sub) setText(if (isAns) digits[i].toString() else userList[i]?.toString() ?: "")
                    else if (userList[i] != null) setText(userList[i].toString())
                    
                    setOnFocusChangeListener { _, hasFocus ->
                        if (hasFocus) { activeCell = this; activeList = userList; activeIndex = i }
                    }
                }
                row.addView(et)
            }
        }
        parent.addView(row)
    }

    fun typeNumber(num: Int) {
        val cell = activeCell ?: return
        val list = activeList ?: return
        val idx = activeIndex
        if(idx != -1) {
            cell.setText(num.toString()); list[idx] = num
            val parentRow = cell.parent as LinearLayout
            var nextView = parentRow.getChildAt(idx + 1)
            if (nextView !is EditText) {
                val verticalBox = parentRow.parent as LinearLayout
                val rowIndex = verticalBox.indexOfChild(parentRow)
                for (j in rowIndex + 1 until verticalBox.childCount) {
                    val nextRow = verticalBox.getChildAt(j) as? LinearLayout
                    if (nextRow != null) {
                        for (k in 0 until nextRow.childCount) {
                            val v = nextRow.getChildAt(k)
                            if (v is EditText && v.isEnabled) { nextView = v; break }
                        }
                    }
                    if (nextView is EditText) break
                }
            }
            if (nextView is EditText && nextView.isEnabled) nextView.requestFocus()
        }
    }

    fun clearNumber() {
        val cell = activeCell ?: return
        val list = activeList ?: return
        if(activeIndex != -1) { cell.setText(""); list[activeIndex] = null }
    }

    override fun getItemCount() = list.size
}

// ================= Activity =================
class MainActivity : AppCompatActivity() {
    private lateinit var vm: MathViewModel
    private lateinit var adapter: QAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        vm = ViewModelProvider(this)[MathViewModel::class.java]
        
        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        val btnSubmitAll = findViewById<Button>(R.id.btnSubmitAll)
        val btnStats = findViewById<Button>(R.id.btnStats)
        val btnWrong = findViewById<Button>(R.id.btnWrong)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val tvStatsBar = findViewById<TextView>(R.id.tvStatsBar)

        rv.layoutManager = LinearLayoutManager(this)

        vm.qs.observe(this) { list ->
            adapter = QAdapter(list, onUpdate = {
                if (list.all { it.sub }) {
                    btnSubmitAll.isEnabled = true
                    btnSubmitAll.text = "查看本轮成绩"
                }
            }, onStat = { isCorrect ->
                vm.submitStat(isCorrect)
            })
            rv.adapter = adapter
        }

        val updateStatsUI = {
            val t = vm.todayTotal.value ?: 0
            val c = vm.todayCorrect.value ?: 0
            val acc = if (t > 0) "${c * 100 / t}%" else "—"
            tvStatsBar.text = "今日 $t 题 | 正确率 $acc"
        }
        vm.todayTotal.observe(this) { updateStatsUI() }
        vm.todayCorrect.observe(this) { updateStatsUI() }

        btnReset.setOnClickListener {
            btnSubmitAll.isEnabled = false
            btnSubmitAll.text = "提交全部"
            vm.gen()
        }

        val keys = mapOf(
            findViewById<Button>(R.id.btn0) to 0, findViewById<Button>(R.id.btn1) to 1,
            findViewById<Button>(R.id.btn2) to 2, findViewById<Button>(R.id.btn3) to 3,
            findViewById<Button>(R.id.btn4) to 4, findViewById<Button>(R.id.btn5) to 5,
            findViewById<Button>(R.id.btn6) to 6, findViewById<Button>(R.id.btn7) to 7,
            findViewById<Button>(R.id.btn8) to 8, findViewById<Button>(R.id.btn9) to 9
        )
        for ((b, num) in keys) b.setOnClickListener { adapter.typeNumber(num) }
        findViewById<Button>(R.id.btnClear).setOnClickListener { adapter.clearNumber() }

        btnSubmitAll.setOnClickListener {
            val list = vm.qs.value ?: return@setOnClickListener
            val correct = list.count { it.cor }
            list.filter { !it.cor }.forEach { vm.addWrong(it) }

            AlertDialog.Builder(this)
                .setTitle("本轮成绩")
                .setMessage("正确率：${correct * 100 / list.size}%\n答对 $correct / 10 题\n\n错题已自动加入错题本。")
                .setPositiveButton("重新出题") { _, _ -> 
                    btnSubmitAll.isEnabled = false
                    btnSubmitAll.text = "提交全部"
                    vm.gen() 
                }
                .setNegativeButton("关闭", null)
                .show()
        }

        btnWrong.setOnClickListener {
            val wrongs = vm.getWrongBook()
            if (wrongs.isEmpty()) {
                Toast.makeText(this, "错题本是空的哦！", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val items = wrongs.map { "❌ ${it.first} = ${it.second}" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("错题本")
                .setItems(items, null)
                .setPositiveButton("清空错题本") { _, _ -> vm.clearWrongBook() }
                .setNegativeButton("关闭", null)
                .show()
        }

        btnStats.setOnClickListener {
            val t = vm.todayTotal.value ?: 0
            val c = vm.todayCorrect.value ?: 0
            val all = vm.allTotal.value ?: 0
            val acc = if (t > 0) "${c * 100 / t}%" else "—"
            AlertDialog.Builder(this)
                .setTitle("学习统计")
                .setMessage("【今日数据】\n已做题目：$t 题\n正确题数：$c 题\n正确率：$acc\n\n【累计数据】\n历史总做题：$all 题")
                .setPositiveButton("知道了", null)
                .show()
        }
    }
}