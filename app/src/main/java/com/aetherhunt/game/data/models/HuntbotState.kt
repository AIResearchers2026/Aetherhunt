package com.aetherhunt.game.data.models

data class HuntbotState(
    val rank: Int = 1,
    val levelEff: Int = 0, // Efficiency
    val levelDur: Int = 0, // Duration
    val levelCst: Int = 0, // Cost
    val levelGn: Int = 0,  // Gain (Essence)
    val levelExp: Int = 0, // Experience
    val isActive: Boolean = false,
    val cycleStartTime: Long = 0L
) {
    // Base stats scaled by level
    val efficiency: Double get() = 5.0 + (levelEff * 2.5) 
    val duration: Double get() = 3600.0 / (1.0 + levelDur * 0.1) 
    val cost: Long get() = 100L + (levelCst * 50L)
    val essenceGain: Double get() = 0.1 + (levelGn * 0.05) 
    val expGain: Double get() = 10.0 + (levelExp * 5.0)

    fun calculateOfflineRewards(lastSaveTime: Long): Triple<Long, Long, Long> {
        val secondsPassed = (System.currentTimeMillis() - lastSaveTime) / 1000
        if (secondsPassed <= 0 || !isActive) return Triple(0, 0, 0)

        val cyclesCompleted = (secondsPassed / duration).toInt()
        
        return Triple(
            (cyclesCompleted * efficiency).toLong(), // Animals
            ((secondsPassed / 3600.0) * essenceGain).toLong(), // Essence
            ((secondsPassed / 3600.0) * expGain).toLong() // XP
        )
    }
}
