package com.example.scientificcalculator

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var displayContainer: View
    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private lateinit var scientificContainer: View
    private lateinit var historyContainer: View
    private lateinit var llHistoryItems: android.widget.LinearLayout
    private lateinit var btnAngleToggle: Button

    // Calculator State
    private var expressionString = ""
    private var isNewOperation = true
    private var lastResult = 0.0
    private var isDegreeMode = true
    private var is2ndMode = false
    private var historyList = mutableListOf<String>()

    // ---------------------------------------------------------------
    // Recursive Descent Parser for Mathematical Expressions
    // ---------------------------------------------------------------
    inner class ExpressionParser(private val expr: String) {
        private var pos = -1
        private var ch = 0

        // Move to the next character in the expression string
        private fun nextChar() {
            ch = if (++pos < expr.length) expr[pos].code else -1
        }

        // Consume a specific character if it matches, and advance
        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            return if (ch == charToEat) { nextChar(); true } else false
        }

        // Main parse entry point
        fun parse(): Double {
            nextChar()
            val result = parseExpression()
            return if (pos < expr.length) Double.NaN else result
        }

        // Parse addition (+) and subtraction (-) operators
        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        // Parse multiplication (*), division (/), modulo (%), and permutations (P) / combinations (C)
        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                x = when {
                    eat('*'.code) -> x * parseFactor()
                    eat('/'.code) -> {
                        val d = parseFactor()
                        if (d == 0.0) return Double.NaN else x / d
                    }
                    eat('%'.code) -> {
                        val d = parseFactor()
                        if (d == 0.0) return Double.NaN else x % d
                    }
                    eat('P'.code) -> {
                        val r = parseFactor()
                        nPr(x.toInt(), r.toInt()).toDouble()
                    }
                    eat('C'.code) -> {
                        val r = parseFactor()
                        nCr(x.toInt(), r.toInt()).toDouble()
                    }
                    else -> return x
                }
            }
        }

        // Parse numbers, parentheses, square root, mathematical constants, and functions
        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor() // Unary plus
            if (eat('-'.code)) return -parseFactor() // Unary minus

            var x: Double
            val startPos = pos

            when {
                // Parentheses expression
                eat('('.code) -> {
                    x = parseExpression()
                    eat(')'.code)
                }
                // Number parsing
                (ch >= '0'.code && ch <= '9'.code) || ch == '.'.code -> {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = expr.substring(startPos, pos).toDoubleOrNull() ?: Double.NaN
                }
                // Square root operator (√)
                ch == 0x221A -> {
                    nextChar()
                    x = Math.sqrt(parseFactor())
                }
                // Function parsing (sin, cos, tan, asin, acos, atan, ln, log)
                ch >= 'a'.code && ch <= 'z'.code -> {
                    while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
                    val func = expr.substring(startPos, pos)
                    val arg = parseFactor()
                    x = when (func) {
                        "sin" -> {
                            val a = if (isDegreeMode) Math.toRadians(arg) else arg
                            Math.sin(a)
                        }
                        "cos" -> {
                            val a = if (isDegreeMode) Math.toRadians(arg) else arg
                            Math.cos(a)
                        }
                        "tan" -> {
                            val a = if (isDegreeMode) Math.toRadians(arg) else arg
                            Math.tan(a)
                        }
                        "asin" -> {
                            val a = Math.asin(arg)
                            if (isDegreeMode) Math.toDegrees(a) else a
                        }
                        "acos" -> {
                            val a = Math.acos(arg)
                            if (isDegreeMode) Math.toDegrees(a) else a
                        }
                        "atan" -> {
                            val a = Math.atan(arg)
                            if (isDegreeMode) Math.toDegrees(a) else a
                        }
                        "sqrt" -> Math.sqrt(arg)
                        "ln"   -> Math.log(arg)
                        "log"  -> Math.log10(arg)
                        else   -> Double.NaN
                    }
                }
                else -> x = Double.NaN
            }

            // Exponent / power operator (^)
            if (eat('^'.code)) x = Math.pow(x, parseFactor())
            // Factorial postfix operator (!)
            while (eat('!'.code)) x = factorial(x.toInt()).toDouble()

            return x
        }
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        displayContainer  = findViewById(R.id.displayContainer)
        tvExpression      = findViewById(R.id.tvExpression)
        tvResult          = findViewById(R.id.tvResult)
        scientificContainer = findViewById(R.id.scientificContainer)
        historyContainer  = findViewById(R.id.historyContainer)
        llHistoryItems    = findViewById(R.id.llHistoryItems)
        btnAngleToggle    = findViewById(R.id.btn_angle_toggle)

        // Slide down on display area to open history
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dy = e2.y - (e1?.y ?: 0f)
                if (dy > 100 && Math.abs(velocityY) > 200) {
                    displayHistoryItems()
                    historyContainer.visibility = View.VISIBLE
                    return true
                }
                return false
            }
        })
        displayContainer.setOnTouchListener(object : View.OnTouchListener {
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(event)
                return true
            }
        })

        setupKeyboard()
        loadHistory()

        if (savedInstanceState != null) {
            expressionString = savedInstanceState.getString("expressionString", "")!!
            isNewOperation   = savedInstanceState.getBoolean("isNewOperation", true)
            lastResult       = savedInstanceState.getDouble("lastResult", 0.0)
            isDegreeMode     = savedInstanceState.getBoolean("isDegreeMode", true)

            tvExpression.text = savedInstanceState.getString("expressionText", "")
            tvResult.text     = savedInstanceState.getString("resultText", "0")
            val isIntermediate = savedInstanceState.getBoolean("isIntermediate", true)
            setResultTextColor(isIntermediate)
            updateAngleToggleText()
        } else {
            clear()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("expressionString", expressionString)
        outState.putBoolean("isNewOperation", isNewOperation)
        outState.putDouble("lastResult", lastResult)
        outState.putBoolean("isDegreeMode", isDegreeMode)
        outState.putString("expressionText", tvExpression.text.toString())
        outState.putString("resultText", tvResult.text.toString())
        val isIntermediate = tvResult.currentTextColor == resources.getColor(R.color.text_secondary, theme)
        outState.putBoolean("isIntermediate", isIntermediate)
    }

    // ---------------------------------------------------------------
    // UI helpers
    // ---------------------------------------------------------------

    private fun setResultTextColor(isIntermediate: Boolean) {
        val color = if (isIntermediate) R.color.text_secondary else R.color.text_primary
        tvResult.setTextColor(resources.getColor(color, theme))
    }

    private fun updateAngleToggleText() {
        btnAngleToggle.text = if (isDegreeMode) "DEG" else "RAD"
    }

    // ---------------------------------------------------------------
    // Keyboard Setup
    // ---------------------------------------------------------------

    private fun setupKeyboard() {
        val numberIds = listOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        )
        for (id in numberIds) {
            findViewById<Button>(id).setOnClickListener { v ->
                appendNumber((v as Button).text.toString())
            }
        }

        // Main keyboard square root button (replaces "00")
        findViewById<Button>(R.id.btn_double_zero).setOnClickListener {
            appendRaw("√(")
        }

        findViewById<Button>(R.id.btn_dot).setOnClickListener     { appendDot() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener   { clear() }
        findViewById<Button>(R.id.btn_delete).setOnClickListener  { deleteLast() }
        findViewById<Button>(R.id.btn_equal).setOnClickListener   { evaluateFinal() }

        val operatorMap = mapOf(
            R.id.btn_add      to "+",
            R.id.btn_subtract to "-",
            R.id.btn_multiply to "×",
            R.id.btn_divide   to "÷",
            R.id.btn_percent  to "%"
        )
        for ((id, op) in operatorMap) {
            findViewById<Button>(id).setOnClickListener { selectOperator(op) }
        }

        // Angle toggle
        btnAngleToggle.setOnClickListener {
            isDegreeMode = !isDegreeMode
            updateAngleToggleText()
            updateIntermediateResult()
        }

        // Scientific panel toggle (main keyboard "√" button stays visible)
        findViewById<Button>(R.id.btn_sci_toggle).setOnClickListener {
            if (scientificContainer.visibility == View.VISIBLE) {
                scientificContainer.visibility = View.GONE
            } else {
                scientificContainer.visibility = View.VISIBLE
            }
        }

        // History
        findViewById<View>(R.id.btn_history_toggle).setOnClickListener {
            displayHistoryItems()
            historyContainer.visibility = View.VISIBLE
        }
        findViewById<Button>(R.id.btn_close_history).setOnClickListener  { historyContainer.visibility = View.GONE }
        findViewById<Button>(R.id.btn_clear_history).setOnClickListener  { clearHistory() }

        // 2nd Mode Setup
        val btn2nd = findViewById<Button>(R.id.btn_2nd)
        val btnSin = findViewById<Button>(R.id.btn_sin)
        val btnCos = findViewById<Button>(R.id.btn_cos)
        val btnTan = findViewById<Button>(R.id.btn_tan)

        btn2nd.setOnClickListener {
            is2ndMode = !is2ndMode
            if (is2ndMode) {
                btn2nd.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.btn_equal, theme))
                btn2nd.setTextColor(resources.getColor(R.color.white, theme))
                btnSin.text = "sin⁻¹"
                btnCos.text = "cos⁻¹"
                btnTan.text = "tan⁻¹"
            } else {
                btn2nd.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.btn_action, theme))
                btn2nd.setTextColor(resources.getColor(R.color.text_primary, theme))
                btnSin.text = "sin"
                btnCos.text = "cos"
                btnTan.text = "tan"
            }
        }

        btnSin.setOnClickListener {
            if (is2ndMode) appendRaw("asin(") else appendRaw("sin(")
        }
        btnCos.setOnClickListener {
            if (is2ndMode) appendRaw("acos(") else appendRaw("cos(")
        }
        btnTan.setOnClickListener {
            if (is2ndMode) appendRaw("atan(") else appendRaw("tan(")
        }

        // Scientific function buttons
        val sciMap = mapOf(
            R.id.btn_pow          to " ^ ",
            R.id.btn_paren_open   to "(",
            R.id.btn_paren_close  to ")",
            R.id.btn_pi           to "3.141592653589793",
            R.id.btn_e            to "2.718281828459045",
            R.id.btn_fact         to "!",
            R.id.btn_npr          to " P ",
            R.id.btn_ncr          to " C ",
            R.id.btn_ln           to "ln(",
            R.id.btn_log          to "log("
        )
        for ((id, value) in sciMap) {
            findViewById<Button>(id).setOnClickListener { appendRaw(value) }
        }
    }

    // ---------------------------------------------------------------
    // Input handlers
    // ---------------------------------------------------------------

    private fun appendNumber(num: String) {
        if (isNewOperation) {
            expressionString = ""
            isNewOperation   = false
        }
        expressionString += num
        tvExpression.text = expressionString
        updateIntermediateResult()
    }

    private fun appendRaw(text: String) {
        if (isNewOperation) {
            expressionString = ""
            isNewOperation   = false
        }
        expressionString += text
        tvExpression.text = expressionString
        updateIntermediateResult()
    }

    private fun appendDot() {
        if (isNewOperation) {
            expressionString = "0."
            isNewOperation   = false
            tvExpression.text = expressionString
            updateIntermediateResult()
            return
        }
        val last = expressionString.takeLast(1)
        if (last.isEmpty() || last == " " || last == "(") {
            expressionString += "0."
        } else {
            // Only add if the current number token has no dot yet
            var i = expressionString.length - 1
            var hasDot = false
            while (i >= 0 && expressionString[i] != ' ' && expressionString[i] != '(' && expressionString[i] != ')') {
                if (expressionString[i] == '.') { hasDot = true; break }
                i--
            }
            if (!hasDot) expressionString += "."
        }
        tvExpression.text = expressionString
        updateIntermediateResult()
    }

    private fun selectOperator(op: String) {
        if (expressionString.isEmpty()) {
            if (op == "-") { expressionString = "-"; tvExpression.text = expressionString; isNewOperation = false }
            return
        }
        if (isNewOperation) { expressionString = formatResult(lastResult); isNewOperation = false }
        if (expressionString.endsWith(" ")) {
            expressionString = expressionString.dropLast(3) + " $op "
        } else {
            expressionString += " $op "
        }
        tvExpression.text = expressionString
        updateIntermediateResult()
    }

    private fun clear() {
        expressionString = ""; isNewOperation = true; lastResult = 0.0
        tvExpression.text = ""; tvResult.text = "0"
        setResultTextColor(true)
    }

    private fun deleteLast() {
        if (isNewOperation) { clear(); return }
        if (expressionString.isNotEmpty()) {
            expressionString = when {
                expressionString.endsWith(" ")    -> expressionString.dropLast(3)
                expressionString.endsWith("sin(") -> expressionString.dropLast(4)
                expressionString.endsWith("cos(") -> expressionString.dropLast(4)
                expressionString.endsWith("tan(") -> expressionString.dropLast(4)
                expressionString.endsWith("asin(") -> expressionString.dropLast(5)
                expressionString.endsWith("acos(") -> expressionString.dropLast(5)
                expressionString.endsWith("atan(") -> expressionString.dropLast(5)
                expressionString.endsWith("ln(")  -> expressionString.dropLast(3)
                expressionString.endsWith("log(") -> expressionString.dropLast(4)
                expressionString.endsWith("√(")   -> expressionString.dropLast(2)
                else                               -> expressionString.dropLast(1)
            }
            tvExpression.text = expressionString
            updateIntermediateResult()
        }
    }

    // ---------------------------------------------------------------
    // Evaluation
    // ---------------------------------------------------------------

    private fun updateIntermediateResult() {
        setResultTextColor(true)
        if (expressionString.isEmpty() || expressionString == "-") { tvResult.text = "0"; return }
        val res = evaluateExpression(expressionString)
        tvResult.text = if (res.isNaN() || res.isInfinite()) "" else formatResult(res)
    }

    private fun evaluateFinal() {
        if (expressionString.isEmpty()) return
        val res = evaluateExpression(expressionString)
        val finalExpr = expressionString
        tvExpression.text = "$expressionString ="
        if (res.isNaN() || res.isInfinite()) {
            tvResult.text = "Error"; lastResult = 0.0
        } else {
            val formatted = formatResult(res)
            tvResult.text = formatted; lastResult = res
            saveHistoryItem(finalExpr, formatted)
        }
        setResultTextColor(false)
        isNewOperation = true
    }

    private fun evaluateExpression(expr: String): Double {
        val sanitized = expr
            .replace("×", "*")
            .replace("÷", "/")
        return try {
            ExpressionParser(sanitized).parse()
        } catch (e: Exception) {
            Double.NaN
        }
    }

    // ---------------------------------------------------------------
    // Math helpers
    // ---------------------------------------------------------------

    private fun factorial(n: Int): Long {
        if (n < 0 || n > 20) return 0L
        var r = 1L
        for (i in 2..n) r *= i
        return r
    }

    private fun nPr(n: Int, r: Int): Long {
        if (n < 0 || r < 0 || r > n) return 0L
        return factorial(n) / factorial(n - r)
    }

    private fun nCr(n: Int, r: Int): Long {
        if (n < 0 || r < 0 || r > n) return 0L
        return factorial(n) / (factorial(r) * factorial(n - r))
    }

    private fun formatResult(num: Double): String =
        if (num == num.toLong().toDouble()) num.toLong().toString()
        else DecimalFormat("0.#######").format(num)

    // ---------------------------------------------------------------
    // History
    // ---------------------------------------------------------------

    private fun loadHistory() {
        val prefs = getSharedPreferences("calc_prefs", MODE_PRIVATE)
        val saved = prefs.getString("history", "") ?: ""
        historyList = if (saved.isEmpty()) mutableListOf()
                      else saved.split("\n").filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveHistoryItem(expression: String, result: String) {
        val item = "$expression = $result"
        historyList.removeAll { it == item }
        historyList.add(0, item)
        if (historyList.size > 20) historyList.removeAt(historyList.size - 1)
        getSharedPreferences("calc_prefs", MODE_PRIVATE)
            .edit().putString("history", historyList.joinToString("\n")).apply()
    }

    private fun clearHistory() {
        historyList.clear()
        getSharedPreferences("calc_prefs", MODE_PRIVATE).edit().remove("history").apply()
        displayHistoryItems()
    }

    private fun displayHistoryItems() {
        llHistoryItems.removeAllViews()
        if (historyList.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No history yet"
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 64, 0, 0)
            }
            llHistoryItems.addView(tv)
            return
        }

        for (item in historyList) {
            val parts = item.split(" = ", limit = 2)
            if (parts.size != 2) continue

            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val pad = (12 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
            }

            val tvExpr = TextView(this).apply {
                text = parts[0]
                setTextColor(resources.getColor(R.color.text_secondary, theme))
                textSize = 14f
            }
            val tvRes = TextView(this).apply {
                text = "= ${parts[1]}"
                setTextColor(resources.getColor(R.color.text_primary, theme))
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            container.addView(tvExpr)
            container.addView(tvRes)

            container.setOnClickListener {
                expressionString = parts[0]
                tvExpression.text = expressionString
                isNewOperation = false
                updateIntermediateResult()
                historyContainer.visibility = View.GONE
            }

            // Divider
            val divider = View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(resources.getColor(R.color.text_secondary, theme))
                alpha = 0.2f
            }

            llHistoryItems.addView(container)
            llHistoryItems.addView(divider)
        }
    }
}
