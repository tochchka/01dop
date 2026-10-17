package com.example.magicball

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sqrt
import kotlin.random.Random
class MainActivity : Activity() {
    private lateinit var game: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = GameView(this)
        setContentView(game)
    }

    override fun onResume() {
        super.onResume()
        game.startGame()
    }

    override fun onPause() {
        game.stopGame()
        super.onPause()
    }
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, SensorEventListener {
    // БЛОК 1. Настройки игры (1.1–1.6)
    private val BALL_COLOR = Color.RED
    private val TARGET_COLOR = Color.GREEN
    private val BG_COLOR = Color.BLACK
    private val BALL_RADIUS = 40f
    private val TARGET_COUNT = 5
    private val SENSITIVITY = 0.3f
    private val TARGET_RADIUS = 27f

    // БЛОК 2. Положение шарика, скорость и счёт (2.1–2.4)
    private var ballX = 100f
    private var ballY = 100f
    private var ballSpeedX = 0f
    private var ballSpeedY = 0f
    private var score = 0
    private var gameWon = false

    // БЛОК 3. Кисти для рисования
    private val ballPaint = Paint().apply { color = BALL_COLOR; isAntiAlias = true }
    private val targetPaint = Paint().apply { color = TARGET_COLOR; isAntiAlias = true }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 46f
        isAntiAlias = true
    }

    // БЛОК 4. Акселерометр (4.1–4.2)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // БЛОК 5. Поток для игры (5.1–5.2)
    private var thread: Thread? = null
    @Volatile private var isRunning = false
    @Volatile private var tiltX = 0f
    @Volatile private var tiltY = 0f

    private val targets = mutableListOf<Pair<Float, Float>>()

    // БЛОК 6. Создание игрового поля и мишеней
    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        createTargets()
        startGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    private fun createTargets() {
        if (width <= 0 || height <= 0 || targets.isNotEmpty()) return
        val left = TARGET_RADIUS.toInt()
        val right = (width - TARGET_RADIUS).toInt().coerceAtLeast(left + 1)
        val top = 130.coerceAtMost((height - TARGET_RADIUS).toInt().coerceAtLeast(left))
        val bottom = (height - TARGET_RADIUS).toInt().coerceAtLeast(top + 1)
        repeat(TARGET_COUNT) {
            var x: Float
            var y: Float
            var tries = 0
            do {
                x = Random.nextInt(left, right).toFloat()
                y = Random.nextInt(top, bottom).toFloat()
                tries++
            } while (distance(x, y, ballX, ballY) < BALL_RADIUS + TARGET_RADIUS + 30f && tries < 50)
            targets.add(Pair(x, y))
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopGame()
    }

    // БЛОК 7. Запуск и остановка (7.1–7.2)
    fun startGame() {
        if (isRunning || !holder.surface.isValid) return
        isRunning = true
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
        thread = Thread { gameLoop() }
        thread?.start()
    }

    fun stopGame() {
        isRunning = false
        sensorManager.unregisterListener(this)
        try {
            if (thread != Thread.currentThread()) thread?.join(500)
        } catch (_: InterruptedException) { }
        thread = null
    }

    // БЛОК 8. Игровой цикл, движение и рисование (8.1–8.3)
    private fun gameLoop() {
        while (isRunning) {
            update()
            var canvas: Canvas? = null
            try {
                if (holder.surface.isValid) {
                    canvas = holder.lockCanvas()
                    if (canvas != null) drawGame(canvas)
                }
            } catch (_: Exception) {
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
                }
            }
            try { Thread.sleep(16) } catch (_: InterruptedException) { break }
        }
    }

    private fun update() {
        if (gameWon || width < BALL_RADIUS * 2 || height < BALL_RADIUS * 2) return

        ballSpeedX += tiltX * SENSITIVITY
        ballSpeedY += tiltY * SENSITIVITY
        ballSpeedX *= 0.98f
        ballSpeedY *= 0.98f
        ballX += ballSpeedX
        ballY += ballSpeedY

        if (ballX < BALL_RADIUS) { ballX = BALL_RADIUS; ballSpeedX = -ballSpeedX * 0.7f }
        if (ballX > width - BALL_RADIUS) { ballX = width - BALL_RADIUS; ballSpeedX = -ballSpeedX * 0.7f }
        if (ballY < BALL_RADIUS) { ballY = BALL_RADIUS; ballSpeedY = -ballSpeedY * 0.7f }
        if (ballY > height - BALL_RADIUS) { ballY = height - BALL_RADIUS; ballSpeedY = -ballSpeedY * 0.7f }

        val iterator = targets.iterator()
        while (iterator.hasNext()) {
            val target = iterator.next()
            if (distance(ballX, ballY, target.first, target.second) < BALL_RADIUS + TARGET_RADIUS) {
                iterator.remove()
                score++
            }
        }
        if (score == TARGET_COUNT) gameWon = true
    }

    private fun drawGame(canvas: Canvas) {
        canvas.drawColor(BG_COLOR)
        for (target in targets) canvas.drawCircle(target.first, target.second, TARGET_RADIUS, targetPaint)
        canvas.drawCircle(ballX, ballY, BALL_RADIUS, ballPaint)
        canvas.drawText("Счёт: $score / $TARGET_COUNT", 25f, 70f, textPaint)
        if (gameWon) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("ПОБЕДА!", width / 2f, height / 2f, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    // БЛОК 9. Наклон телефона (9.1–9.2)
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            tiltX = -event.values[0]
            tiltY = event.values[1]
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
