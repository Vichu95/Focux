package com.focux.pulse.ui.screens.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.focux.pulse.ui.theme.PulseAppColorPrimary
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    val angle: Float,
    val radiusRatio: Float,
    val size: Float,
    val speed: Float
)

fun generateParticles(count: Int = 180): List<Particle> {
    return List(count) {
        Particle(
            angle = Random.nextFloat() * 2f * Math.PI.toFloat(),
            radiusRatio = 0.1f + Random.nextFloat() * 0.9f,
            size = 1f + Random.nextFloat() * 3f,
            speed = 0.5f + Random.nextFloat() * 1.5f
        )
    }
}

@Composable
fun BreathingParticleAnimation(
    modifier: Modifier = Modifier,
    currentPhaseScale: Float,
    rotation: Float,
    particles: List<Particle>
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val primaryColor = PulseAppColorPrimary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width / 2
            val minRadius = maxRadius * 0.4f
            val currentRadius = minRadius + (maxRadius - minRadius) * (0.1f + 0.9f * currentPhaseScale)
            
            particles.forEach { p ->
                val r = currentRadius * p.radiusRatio
                val theta = p.angle + rotation * p.speed
                val x = center.x + r * cos(theta)
                val y = center.y + r * sin(theta)
                
                // slight pulsing opacity based on scale
                val alpha = 0.3f + 0.7f * currentPhaseScale
                drawCircle(
                    color = primaryColor.copy(alpha = alpha),
                    radius = p.size,
                    center = Offset(x, y)
                )
            }
        }
    }
}
