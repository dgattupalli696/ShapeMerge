package com.shapemerge

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.EdgeShape
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GameScreen(private val game: ShapeMergeGame) : Screen {

    private enum class State { START, PLAYING, GAME_OVER }

    private val camera = OrthographicCamera()
    private val viewport = FitViewport(Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT, camera)

    private val hudCamera = OrthographicCamera()
    private val hudViewport = FitViewport(Constants.HUD_WIDTH, Constants.HUD_HEIGHT, hudCamera)

    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val font = BitmapFont()
    private val debugRenderer = Box2DDebugRenderer()
    private val drawDebug = false

    private lateinit var world: World
    private val shapes = ArrayList<ShapeEntity>()
    private val mergeQueue = ArrayList<Pair<ShapeEntity, ShapeEntity>>()

    // Active power-up projectiles (bomb, cannonball) tracked for special behavior.
    private class Projectile(val kind: AmmoKind, val body: Body) {
        var life = 0f
        val hit = HashSet<ShapeEntity>() // shapes already knocked (cannonball)
    }
    private val projectiles = ArrayList<Projectile>()

    // Transient floating effects: circle-pop rings + floating score/combo text.
    private class Pop(
        val x: Float,
        val y: Float,
        val text: String,
        val ring: Boolean,
        val color: Color
    ) {
        var time = 0f
    }
    private val pops = ArrayList<Pop>()
    private val popColor = Color()
    private val popDuration = 1.2f

    // Combo: consecutive merges within a time window stack a score multiplier.
    private var combo = 0
    private var comboTimer = 0f

    private val fx = Effects()
    private val particles = Particles()
    private val confettiColor = Color()
    private val sounds = Sounds()
    private val haptics = game.haptics

    private val prefs = Gdx.app.getPreferences("shapemerge")

    private var state = State.START
    private var score = 0
    private var level = 1
    private var scoreTarget = Constants.BASE_TARGET
    private var highScore = prefs.getInteger("highScore", 0)

    private var currentAmmo: Ammo = randomAmmo()
    private var nextAmmo: Ammo = randomAmmo()

    private var aiming = false
    private var previewSpin = 0f
    private val launcher = Vector2(Constants.LAUNCH_X, Constants.LAUNCH_Y)
    private val finger = Vector2()

    // Scratch objects to avoid per-frame allocation.
    private val tmp3 = Vector3()
    private val tmpA = Vector2()
    private val aimDir = Vector2()

    init {
        buildWorld()
        sounds.load()
        Gdx.input.inputProcessor = InputHandler()
    }

    private fun ammoMaxLevel(): Int =
        minOf(Constants.MAX_LEVEL - 1, Constants.MIN_LEVEL + level)

    private fun randomAmmoLevel(): Int =
        MathUtils.random(Constants.MIN_LEVEL, ammoMaxLevel())

    /** Produces the next ammo, occasionally a power-up. */
    private fun randomAmmo(): Ammo =
        if (MathUtils.random() < Constants.POWERUP_CHANCE) {
            when (MathUtils.random(2)) {
                0 -> Ammo(AmmoKind.BOMB)
                1 -> Ammo(AmmoKind.CANNONBALL)
                else -> Ammo(AmmoKind.MULTIBALL)
            }
        } else {
            Ammo(AmmoKind.SHAPE, randomAmmoLevel())
        }

    private fun buildWorld() {
        world = World(Vector2(0f, 0f), true)
        world.setContactListener(MergeContactListener { a, b -> mergeQueue.add(a to b) })
        createWalls()
    }

    private fun createWalls() {
        val bodyDef = BodyDef().apply { type = BodyDef.BodyType.StaticBody }
        val body: Body = world.createBody(bodyDef)
        val w = Constants.WORLD_WIDTH
        val h = Constants.WORLD_HEIGHT
        val edge = EdgeShape()
        val fixtureDef = FixtureDef().apply {
            shape = edge
            restitution = Constants.WALL_RESTITUTION
            friction = 0.2f
        }
        // Four walls around the rectangular board.
        edge.set(0f, 0f, w, 0f); body.createFixture(fixtureDef)        // bottom
        edge.set(0f, h, w, h); body.createFixture(fixtureDef)          // top
        edge.set(0f, 0f, 0f, h); body.createFixture(fixtureDef)        // left
        edge.set(w, 0f, w, h); body.createFixture(fixtureDef)          // right
        // One-way divider: shots pass up from the launch zone into the playground,
        // but playground shapes can't fall back down through it.
        edge.set(0f, Constants.LAUNCH_ZONE_TOP, w, Constants.LAUNCH_ZONE_TOP)
        body.createFixture(fixtureDef).userData = Constants.DIVIDER
        edge.dispose()
    }

    private fun resetGame() {
        for (s in shapes) world.destroyBody(s.body)
        shapes.clear()
        for (p in projectiles) world.destroyBody(p.body)
        projectiles.clear()
        mergeQueue.clear()
        pops.clear()
        particles.clear()
        combo = 0
        comboTimer = 0f
        score = 0
        level = 1
        scoreTarget = Constants.BASE_TARGET
        currentAmmo = randomAmmo()
        nextAmmo = randomAmmo()
        aiming = false
        state = State.PLAYING
    }

    /**
     * Computes the clamped throw direction into [aimDir] (unit vector) and returns
     * the power (0 if the pull is below threshold). The direction is restricted to
     * an upward cone so the launcher always points into the playground.
     */
    private fun computeAim(): Float {
        tmpA.set(launcher).sub(finger)
        val pull = tmpA.len()
        if (pull < Constants.MIN_PULL) return 0f
        val power = min(pull, Constants.MAX_PULL)
        // Angle measured from straight up (0 = up, +right / -left); clamp to a cone.
        var angle = atan2(tmpA.x, tmpA.y)
        angle = angle.coerceIn(-Constants.MAX_AIM_TILT_RAD, Constants.MAX_AIM_TILT_RAD)
        aimDir.set(sin(angle), cos(angle))
        return power
    }

    private fun fire() {
        val power = computeAim()
        if (power <= 0f) return

        when (currentAmmo.kind) {
            AmmoKind.SHAPE -> fireShape(currentAmmo.level, power)
            AmmoKind.BOMB -> fireBomb(power)
            AmmoKind.CANNONBALL -> fireCannonball(power)
            AmmoKind.MULTIBALL -> fireMultiball(power)
        }
        sounds.playThrow()
        haptics.vibrate(8)

        currentAmmo = nextAmmo
        nextAmmo = randomAmmo()
    }

    /** Launches a normal polygon shape of [shapeLevel] along the aim arrow. */
    private fun fireShape(shapeLevel: Int, power: Float) {
        fireShapeInDir(shapeLevel, aimDir.x, aimDir.y, power, launcher.x, launcher.y)
    }

    private fun fireShapeInDir(
        shapeLevel: Int,
        dx: Float,
        dy: Float,
        power: Float,
        spawnX: Float,
        spawnY: Float
    ): ShapeEntity {
        // Spawn at the launcher so the shot travels exactly along the aim arrow.
        // The one-way divider lets it pass up into the playground.
        val shape = ShapeFactory.create(world, shapeLevel, spawnX, spawnY)
        shapes.add(shape)

        val impulse = power * Constants.IMPULSE_SCALE *
            (Constants.radiusForLevel(shapeLevel) / Constants.BASE_RADIUS)
        shape.body.applyLinearImpulse(dx * impulse, dy * impulse, spawnX, spawnY, true)
        shape.body.angularVelocity = MathUtils.random(-3f, 3f)
        return shape
    }

    private var nextMergeGroup = 1

    /** Three small shapes in a spread that won't merge with each other. */
    private fun fireMultiball(power: Float) {
        val baseAngle = atan2(aimDir.x, aimDir.y)
        // Perpendicular to the aim direction, to separate the three spawn points.
        val perpX = -aimDir.y
        val perpY = aimDir.x
        val spacing = Constants.radiusForLevel(Constants.MIN_LEVEL) * 2.2f
        val group = nextMergeGroup++
        val offsets = floatArrayOf(-0.3f, 0f, 0.3f)
        for (i in offsets.indices) {
            val a = baseAngle + offsets[i]
            val lateral = (i - 1) * spacing
            val s = fireShapeInDir(
                Constants.MIN_LEVEL,
                sin(a), cos(a),
                power * 0.95f,
                launcher.x + perpX * lateral,
                launcher.y + perpY * lateral
            )
            s.mergeGroup = group
        }
    }

    private fun spawnProjectileBody(radius: Float, sensor: Boolean, density: Float): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(launcher.x, launcher.y)
            bullet = true
            linearDamping = if (sensor) 0.15f else 0.35f
        }
        val body = world.createBody(bodyDef)
        val circle = CircleShape().apply { this.radius = radius }
        val fixtureDef = FixtureDef().apply {
            shape = circle
            this.density = density
            restitution = 0.25f
            friction = 0.3f
            isSensor = sensor
        }
        body.createFixture(fixtureDef)
        circle.dispose()
        return body
    }

    /** A bomb projectile that explodes near the first shape it reaches. */
    private fun fireBomb(power: Float) {
        val body = spawnProjectileBody(0.42f, sensor = true, density = 1f)
        val impulse = power * Constants.IMPULSE_SCALE
        body.applyLinearImpulse(aimDir.x * impulse, aimDir.y * impulse, launcher.x, launcher.y, true)
        projectiles.add(Projectile(AmmoKind.BOMB, body))
    }

    /** A fast cannonball that plows straight through, flinging shapes aside. */
    private fun fireCannonball(power: Float) {
        val body = spawnProjectileBody(Constants.CANNON_RADIUS, sensor = true, density = 1f)
        body.linearDamping = 0f
        val speed = 13f + power * 1.6f
        body.setLinearVelocity(aimDir.x * speed, aimDir.y * speed)
        projectiles.add(Projectile(AmmoKind.CANNONBALL, body))
    }

    private fun processMerges() {
        if (mergeQueue.isEmpty()) return

        // Group all same-level shapes that collided this step into connected
        // clusters (union-find). A cluster of N shapes merges up by (N-1) levels,
        // so 3 triangles colliding at once jump straight to a pentagon.
        val parent = HashMap<ShapeEntity, ShapeEntity>()
        fun find(x: ShapeEntity): ShapeEntity {
            var root = x
            while (parent[root] != root) root = parent[root]!!
            var node = x
            while (parent[node] != root) {
                val next = parent[node]!!
                parent[node] = root
                node = next
            }
            return root
        }
        for ((a, b) in mergeQueue) {
            if (a.removed || b.removed || a.level != b.level) continue
            parent.putIfAbsent(a, a)
            parent.putIfAbsent(b, b)
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        val groups = HashMap<ShapeEntity, MutableList<ShapeEntity>>()
        for (node in parent.keys) {
            if (node.removed) continue
            groups.getOrPut(find(node)) { ArrayList() }.add(node)
        }
        for (group in groups.values) {
            if (group.size >= 2) mergeGroup(group)
        }
        mergeQueue.clear()
    }

    private fun mergeGroup(group: List<ShapeEntity>) {
        val count = group.size
        val base = group[0].level
        var sumX = 0f
        var sumY = 0f
        var sumVX = 0f
        var sumVY = 0f
        for (s in group) {
            s.removed = true
            sumX += s.body.position.x
            sumY += s.body.position.y
            sumVX += s.body.linearVelocity.x
            sumVY += s.body.linearVelocity.y
            world.destroyBody(s.body)
            shapes.remove(s)
        }
        val cx = sumX / count
        val cy = sumY / count
        val newLevel = base + (count - 1)

        // Combo: extend the chain if within the window, else start fresh.
        combo = if (comboTimer > 0f) combo + 1 else 1
        comboTimer = Constants.COMBO_WINDOW
        val mult = combo

        if (newLevel >= Constants.CIRCLE_LEVEL) {
            // Reached a circle: it pops for a big bonus and disappears.
            val bonus = (2000 + base * 100) * mult
            addScore(bonus)
            pops.add(Pop(cx, cy, "+$bonus", true, Color(1f, 0.95f, 0.4f, 1f)))
            // Big juice: strong shake, slow-mo, and a zoom punch.
            fx.shake(0.32f, 0.45f)
            fx.slowMo(0.35f, 0.7f)
            fx.zoomPunch(0.12f, 0.55f)
            // Confetti burst.
            confettiColor.set(1f, 0.95f, 0.4f, 1f)
            particles.burst(cx, cy, 60, confettiColor, 7f, 0.18f)
            sounds.playPop()
            haptics.vibrate(45)
        } else {
            val merged = ShapeFactory.create(world, newLevel, cx, cy)
            merged.body.linearVelocity = Vector2(sumVX / count, sumVY / count)
            shapes.add(merged)
            addScore(newLevel * newLevel * 5 * mult)
            // Small shake that grows with the merged shape's size.
            fx.shake(0.05f + (newLevel - Constants.MIN_LEVEL) * 0.018f, 0.16f)
            // Merge burst in the merged shape's color.
            particles.burst(cx, cy, 12 + newLevel * 2, Constants.colorForLevel(newLevel), 4.5f, 0.13f)
            // Pitch rises with the resulting shape level and combo step.
            sounds.playMerge(0.8f + (newLevel - Constants.MIN_LEVEL) * 0.12f + (combo - 1) * 0.06f)
            haptics.vibrate(14)
        }

        // Floating combo callout.
        if (combo >= 2) {
            pops.add(Pop(cx, cy + 0.7f, comboLabel(combo), false, comboColor(combo)))
        }
    }

    private fun comboLabel(c: Int): String = when {
        c >= 8 -> "MEGA MERGE  x$c"
        c >= 5 -> "SUPER  x$c"
        else -> "x$c"
    }

    private fun comboColor(c: Int): Color {
        val t = ((c - 2) / 8f).coerceIn(0f, 1f)
        return Color(1f, 0.9f - t * 0.6f, 0.2f, 1f)
    }

    private fun addScore(points: Int) {
        score += points
        if (score > highScore) {
            highScore = score
            prefs.putInteger("highScore", highScore)
            prefs.flush()
        }
        if (score >= scoreTarget) {
            level++
            scoreTarget += Constants.BASE_TARGET + level * 250
        }
    }

    private fun checkGameOver() {
        // Lose when shapes fill the playground past the limit.
        var area = 0f
        for (s in shapes) area += MathUtils.PI * s.radius * s.radius
        val playground = Constants.WORLD_WIDTH * (Constants.WORLD_HEIGHT - Constants.LAUNCH_ZONE_TOP)
        if (area >= Constants.FILL_LIMIT * playground) {
            state = State.GAME_OVER
        }
    }

    override fun render(delta: Float) {
        fx.update(delta)
        val scaled = delta * fx.timeScale
        previewSpin += scaled * 0.6f
        updatePops(scaled)
        particles.update(scaled)
        update(scaled)
        draw()
    }

    private fun updatePops(delta: Float) {
        if (pops.isEmpty()) return
        val it = pops.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.time += delta
            if (p.time >= popDuration) it.remove()
        }
    }

    private fun update(delta: Float) {
        if (state != State.PLAYING) return
        world.step(min(delta, 1f / 30f), 8, 3)
        processMerges()
        updateProjectiles(delta)
        if (comboTimer > 0f) {
            comboTimer -= delta
            if (comboTimer <= 0f) combo = 0
        }
        checkGameOver()
    }

    private fun updateProjectiles(delta: Float) {
        if (projectiles.isEmpty()) return
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += delta
            when (p.kind) {
                AmmoKind.BOMB -> {
                    val pos = p.body.position
                    var trigger = p.life >= Constants.BOMB_LIFE
                    if (!trigger && pos.y > Constants.WORLD_HEIGHT - 0.6f) trigger = true
                    if (!trigger && pos.y > Constants.LAUNCH_ZONE_TOP) {
                        for (s in shapes) {
                            val d = Vector2.dst(pos.x, pos.y, s.body.position.x, s.body.position.y)
                            if (d < s.radius + 0.5f) { trigger = true; break }
                        }
                    }
                    if (trigger) {
                        explodeBomb(pos.x, pos.y)
                        world.destroyBody(p.body)
                        it.remove()
                    }
                }
                AmmoKind.CANNONBALL -> {
                    val pos = p.body.position
                    val vel = p.body.linearVelocity
                    val vlen = vel.len().coerceAtLeast(0.0001f)
                    val fx2 = vel.x / vlen
                    val fy2 = vel.y / vlen
                    // Fling any shape it passes through (once each), keeping its own speed.
                    for (s in shapes) {
                        if (s in p.hit) continue
                        val sx = s.body.position.x
                        val sy = s.body.position.y
                        val d = Vector2.dst(pos.x, pos.y, sx, sy)
                        if (d < Constants.CANNON_RADIUS + s.radius + 0.1f) {
                            val nx = if (d > 0.0001f) (sx - pos.x) / d else 0f
                            val ny = if (d > 0.0001f) (sy - pos.y) / d else 1f
                            val dvx = fx2 * 8f + nx * 4f
                            val dvy = fy2 * 8f + ny * 4f
                            val m = s.body.mass
                            s.body.applyLinearImpulse(dvx * m, dvy * m, sx, sy, true)
                            particles.burst(sx, sy, 7, Constants.colorForLevel(s.level), 4f, 0.11f)
                            p.hit.add(s)
                        }
                    }
                    if (p.life >= Constants.CANNON_LIFE || pos.y > Constants.WORLD_HEIGHT - 0.4f) {
                        confettiColor.set(0.7f, 0.7f, 0.78f, 1f)
                        particles.burst(pos.x, pos.y, 14, confettiColor, 4f, 0.12f)
                        world.destroyBody(p.body)
                        it.remove()
                    }
                }
                else -> it.remove()
            }
        }
    }

    private fun explodeBomb(x: Float, y: Float) {
        fx.shake(0.3f, 0.4f)
        fx.slowMo(0.5f, 0.4f)
        sounds.playPop()
        haptics.vibrate(40)
        confettiColor.set(1f, 0.55f, 0.12f, 1f)
        particles.burst(x, y, 55, confettiColor, 8f, 0.2f)

        var cleared = 0
        val it = shapes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val d = Vector2.dst(x, y, s.body.position.x, s.body.position.y)
            if (d < Constants.BOMB_BLAST_RADIUS + s.radius) {
                particles.burst(s.body.position.x, s.body.position.y, 8, Constants.colorForLevel(s.level), 4f, 0.12f)
                s.removed = true
                world.destroyBody(s.body)
                it.remove()
                cleared++
            }
        }
        if (cleared > 0) addScore(cleared * 30)
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.10f, 0.11f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        // Apply screen shake + zoom punch to the world camera.
        camera.zoom = fx.cameraZoom
        camera.position.set(
            Constants.WORLD_WIDTH / 2f + fx.shakeOffset.x,
            Constants.WORLD_HEIGHT / 2f + fx.shakeOffset.y,
            0f
        )
        camera.update()
        shapeRenderer.projectionMatrix = camera.combined

        drawBoardBackground()
        drawShapes()
        drawProjectiles()
        drawParticles()
        drawLauncherAndAim()
        drawPopRings()

        if (drawDebug) debugRenderer.render(world, camera.combined)

        drawHud()
    }

    private fun drawProjectiles() {
        if (projectiles.isEmpty()) return
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (p in projectiles) {
            val pos = p.body.position
            when (p.kind) {
                AmmoKind.BOMB -> {
                    shapeRenderer.color = Color(0.12f, 0.12f, 0.14f, 1f)
                    shapeRenderer.circle(pos.x, pos.y, 0.42f, 20)
                    shapeRenderer.color = Color(1f, 0.55f, 0.12f, 1f) // spark/fuse
                    shapeRenderer.circle(pos.x, pos.y + 0.42f, 0.12f, 10)
                }
                AmmoKind.CANNONBALL -> {
                    shapeRenderer.color = Color(0.32f, 0.34f, 0.4f, 1f)
                    shapeRenderer.circle(pos.x, pos.y, Constants.CANNON_RADIUS, 24)
                    shapeRenderer.color = Color(0.5f, 0.52f, 0.58f, 1f)
                    shapeRenderer.circle(pos.x - 0.18f, pos.y + 0.18f, 0.18f, 12)
                }
                else -> {}
            }
        }
        shapeRenderer.end()
    }

    private fun drawParticles() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        particles.draw(shapeRenderer)
        shapeRenderer.end()
    }

    private fun drawPopRings() {
        if (pops.isEmpty()) return
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        for (p in pops) {
            if (!p.ring) continue
            val t = (p.time / popDuration).coerceIn(0f, 1f)
            val alpha = 1f - t
            val r = 0.5f + t * 2.6f
            popColor.set(1f, 0.95f, 0.4f, alpha)
            shapeRenderer.color = popColor
            shapeRenderer.circle(p.x, p.y, r, 32)
            popColor.set(1f, 1f, 1f, alpha)
            shapeRenderer.color = popColor
            shapeRenderer.circle(p.x, p.y, r * 0.62f, 28)
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawBoardBackground() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        // Playground.
        shapeRenderer.color = Color(0.16f, 0.18f, 0.24f, 1f)
        shapeRenderer.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT)
        // Launch zone (darker shade).
        shapeRenderer.color = Color(0.10f, 0.12f, 0.17f, 1f)
        shapeRenderer.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.LAUNCH_ZONE_TOP)
        // Divider bar.
        shapeRenderer.color = Color(0.55f, 0.60f, 0.82f, 1f)
        shapeRenderer.rect(0f, Constants.LAUNCH_ZONE_TOP - 0.04f, Constants.WORLD_WIDTH, 0.08f)
        shapeRenderer.end()

        // Border outline.
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.35f, 0.40f, 0.55f, 1f)
        shapeRenderer.rect(0f, 0f, Constants.WORLD_WIDTH, Constants.WORLD_HEIGHT)
        shapeRenderer.end()
    }

    private fun drawShapes() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (s in shapes) {
            shapeRenderer.color = Constants.colorForLevel(s.level)
            drawFilledPolygon(s)
        }
        shapeRenderer.end()
    }

    private fun drawFilledPolygon(s: ShapeEntity) {
        drawPolygonAt(s.body.position.x, s.body.position.y, s.level, s.radius, s.body.angle)
    }

    /** Draws a filled regular polygon. Must be called inside a Filled batch. */
    private fun drawPolygonAt(cx: Float, cy: Float, sides: Int, radius: Float, angle: Float) {
        val cos = MathUtils.cos(angle)
        val sin = MathUtils.sin(angle)
        val local = ShapeFactory.polygonVertices(sides, radius)
        for (i in 0 until sides) {
            val i2 = (i + 1) % sides
            val x1 = cx + (local[i * 2] * cos - local[i * 2 + 1] * sin)
            val y1 = cy + (local[i * 2] * sin + local[i * 2 + 1] * cos)
            val x2 = cx + (local[i2 * 2] * cos - local[i2 * 2 + 1] * sin)
            val y2 = cy + (local[i2 * 2] * sin + local[i2 * 2 + 1] * cos)
            shapeRenderer.triangle(cx, cy, x1, y1, x2, y2)
        }
    }

    private fun drawLauncherAndAim() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        // Visual radius for the launcher preview is capped so the pad always fits
        // below the divider; the thrown shape uses its true size.
        val ammoRadius = min(Constants.radiusForLevel(currentAmmo.level), 0.62f)
        // Launcher pad sized to the current ammo.
        shapeRenderer.color = Color(0.80f, 0.82f, 0.88f, 1f)
        shapeRenderer.circle(launcher.x, launcher.y, ammoRadius + 0.22f, 28)
        // Current ammo icon.
        drawAmmoIcon(currentAmmo, launcher.x, launcher.y, ammoRadius)
        // Next ammo preview (smaller).
        shapeRenderer.color = Color(0.55f, 0.57f, 0.63f, 1f)
        shapeRenderer.circle(Constants.NEXT_X, Constants.NEXT_Y, 0.5f, 24)
        drawAmmoIcon(nextAmmo, Constants.NEXT_X, Constants.NEXT_Y, 0.4f)
        shapeRenderer.end()

        if (state == State.PLAYING && aiming) {
            val power = computeAim()
            if (power > 0f) drawAimArrow(power)
        }
    }

    /** Draws an ammo's icon. Must be called inside a Filled batch. */
    private fun drawAmmoIcon(ammo: Ammo, x: Float, y: Float, radius: Float) {
        when (ammo.kind) {
            AmmoKind.SHAPE -> {
                shapeRenderer.color = Constants.colorForLevel(ammo.level)
                drawPolygonAt(x, y, ammo.level, radius, previewSpin)
            }
            AmmoKind.BOMB -> {
                shapeRenderer.color = Color(0.12f, 0.12f, 0.14f, 1f)
                shapeRenderer.circle(x, y, radius * 0.8f, 20)
                shapeRenderer.color = Color(1f, 0.55f, 0.12f, 1f)
                shapeRenderer.circle(x, y + radius * 0.8f, radius * 0.25f, 10)
            }
            AmmoKind.CANNONBALL -> {
                shapeRenderer.color = Color(0.32f, 0.34f, 0.4f, 1f)
                shapeRenderer.circle(x, y, radius * 0.85f, 24)
                shapeRenderer.color = Color(0.5f, 0.52f, 0.58f, 1f)
                shapeRenderer.circle(x - radius * 0.25f, y + radius * 0.25f, radius * 0.25f, 12)
            }
            AmmoKind.MULTIBALL -> {
                shapeRenderer.color = Constants.colorForLevel(Constants.MIN_LEVEL)
                val r = radius * 0.34f
                shapeRenderer.circle(x, y + radius * 0.4f, r, 12)
                shapeRenderer.circle(x - radius * 0.4f, y - radius * 0.3f, r, 12)
                shapeRenderer.circle(x + radius * 0.4f, y - radius * 0.3f, r, 12)
            }
        }
    }

    private val arrowColor = Color()

    /** Draws a thick arrow from the launcher along [aimDir]; color and length grow with power. */
    private fun drawAimArrow(power: Float) {
        val t = (power / Constants.MAX_PULL).coerceIn(0f, 1f)
        // Green (weak) -> yellow -> red (strong).
        arrowColor.set(t, 1f - t, 0.15f, 1f)

        val dx = aimDir.x
        val dy = aimDir.y
        // Perpendicular unit vector.
        val px = -dy
        val py = dx

        val length = 1.3f + power * 0.7f
        val headLen = 0.55f
        val headHalf = 0.34f
        val shaftHalf = 0.12f

        val sx = launcher.x
        val sy = launcher.y
        val ex = sx + dx * length
        val ey = sy + dy * length
        // Where the shaft meets the head.
        val nx = ex - dx * headLen
        val ny = ey - dy * headLen

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = arrowColor
        // Shaft as a quad (two triangles).
        val s1x = sx + px * shaftHalf; val s1y = sy + py * shaftHalf
        val s2x = sx - px * shaftHalf; val s2y = sy - py * shaftHalf
        val n1x = nx + px * shaftHalf; val n1y = ny + py * shaftHalf
        val n2x = nx - px * shaftHalf; val n2y = ny - py * shaftHalf
        shapeRenderer.triangle(s1x, s1y, s2x, s2y, n1x, n1y)
        shapeRenderer.triangle(s2x, s2y, n2x, n2y, n1x, n1y)
        // Arrowhead.
        shapeRenderer.triangle(
            ex, ey,
            nx + px * headHalf, ny + py * headHalf,
            nx - px * headHalf, ny - py * headHalf
        )
        shapeRenderer.end()
    }

    private fun drawHud() {
        hudViewport.apply()
        batch.projectionMatrix = hudCamera.combined
        batch.begin()
        font.color = Color.WHITE

        // Readouts sit at the bottom of the launch zone, clear of the launcher.
        val row1 = 100f
        val row2 = 54f

        font.data.setScale(1.6f)
        // Left: score + best.
        font.draw(batch, "Score: $score", 20f, row1)
        font.draw(batch, "Best: $highScore", 20f, row2)
        // Right: level + target.
        font.draw(batch, "Level $level", 0f, row1, Constants.HUD_WIDTH - 20f, Align.right, false)
        font.draw(batch, "Target $scoreTarget", 0f, row2, Constants.HUD_WIDTH - 20f, Align.right, false)

        // "NEXT" label below the next-shape preview.
        val nextHudX = Constants.NEXT_X / Constants.WORLD_WIDTH * Constants.HUD_WIDTH
        val nextHudY = Constants.NEXT_Y / Constants.WORLD_HEIGHT * Constants.HUD_HEIGHT
        font.data.setScale(1.1f)
        font.draw(batch, "NEXT", nextHudX - 36f, nextHudY - 52f)

        // Pulsing combo multiplier in the playground while a chain is active.
        if (state == State.PLAYING && combo >= 2) {
            val pulse = (comboTimer / Constants.COMBO_WINDOW).coerceIn(0f, 1f)
            val c = comboColor(combo)
            font.data.setScale(2.2f + pulse * 0.9f)
            font.color = popColor.set(c.r, c.g, c.b, 0.55f + pulse * 0.45f)
            font.draw(batch, "x$combo", 0f, Constants.HUD_HEIGHT * 0.66f, Constants.HUD_WIDTH, Align.center, false)
        }

        drawPopScores()

        font.color = Color.WHITE
        when (state) {
            State.START -> drawCentered("SHAPE MERGE", "Tap to start")
            State.GAME_OVER -> drawCentered("GAME OVER", "Tap to restart")
            State.PLAYING -> {}
        }
        font.color = Color.WHITE
        batch.end()
    }

    private fun drawPopScores() {
        if (pops.isEmpty()) return
        for (p in pops) {
            val t = (p.time / popDuration).coerceIn(0f, 1f)
            val hx = p.x / Constants.WORLD_WIDTH * Constants.HUD_WIDTH
            val hy = p.y / Constants.WORLD_HEIGHT * Constants.HUD_HEIGHT + t * 130f
            font.data.setScale(2.6f - t * 0.6f)
            font.color = popColor.set(p.color.r, p.color.g, p.color.b, 1f - t)
            font.draw(batch, p.text, hx - 160f, hy, 320f, Align.center, false)
        }
    }

    private fun drawCentered(title: String, subtitle: String) {
        font.data.setScale(3.5f)
        font.draw(batch, title, 0f, Constants.HUD_HEIGHT / 2f + 40f, Constants.HUD_WIDTH, 1, false)
        font.data.setScale(2f)
        font.draw(batch, subtitle, 0f, Constants.HUD_HEIGHT / 2f - 30f, Constants.HUD_WIDTH, 1, false)
    }

    private fun screenToWorld(screenX: Int, screenY: Int): Vector2 {
        tmp3.set(screenX.toFloat(), screenY.toFloat(), 0f)
        viewport.unproject(tmp3)
        return finger.set(tmp3.x, tmp3.y)
    }

    private inner class InputHandler : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            when (state) {
                State.START, State.GAME_OVER -> {
                    resetGame()
                    return true
                }
                State.PLAYING -> {
                    val w = screenToWorld(screenX, screenY)
                    // Only start aiming when grabbing the launcher or touching the
                    // playground; ignore taps elsewhere in the launch zone.
                    val nearLauncher = Vector2.dst(w.x, w.y, launcher.x, launcher.y) < 1.5f
                    val inPlayground = w.y >= Constants.LAUNCH_ZONE_TOP
                    if (nearLauncher || inPlayground) {
                        aiming = true
                        return true
                    }
                    return false
                }
            }
        }

        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            if (state == State.PLAYING && aiming) screenToWorld(screenX, screenY)
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (state == State.PLAYING && aiming) {
                screenToWorld(screenX, screenY)
                fire()
                aiming = false
            }
            return true
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        hudViewport.update(width, height, true)
    }

    override fun show() {
        Gdx.input.inputProcessor = InputHandler()
    }

    override fun hide() {}
    override fun pause() {}
    override fun resume() {}

    override fun dispose() {
        world.dispose()
        shapeRenderer.dispose()
        batch.dispose()
        font.dispose()
        debugRenderer.dispose()
        sounds.dispose()
    }
}
