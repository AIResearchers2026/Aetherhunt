
package com.aetherhunt.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.random.Random

data class Animal(val id: String, val name: String, val rarity: Int, val goldValue: Long, val essenceValue: Long)
data class Huntbot(val eff: Int=0, val dur: Int=0, val cost: Int=0, val gain: Int=0, val exp: Int=0, var lastTick: Long = System.currentTimeMillis(), var active: Boolean = true)
data class GameState(
    var gold: Long = 100, var essence: Long = 0, var essenceAccum: Double = 0.0,
    var huntbot: Huntbot = Huntbot(), var inventory: ArrayList<Animal> = arrayListOf(),
    var prestigeLevel: Int = 0, var lastSave: Long = System.currentTimeMillis()
)

object SaveManager {
    private val gson = Gson()
    private lateinit var mainFile: File
    private lateinit var tempFile: File

    fun init(context: android.content.Context) {
        mainFile = File(context.filesDir, "aether_save.json")
        tempFile = File(context.filesDir, "aether_save.tmp")
    }

    fun save(state: GameState) {
        try {
            state.lastSave = System.currentTimeMillis()
            tempFile.writeText(gson.toJson(state))
            if (mainFile.exists()) mainFile.delete()
            tempFile.renameTo(mainFile)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun load(): GameState? {
        return try { if (mainFile.exists()) gson.fromJson(mainFile.readText(), GameState::class.java) else null } 
        catch (e: Exception) { null }
    }
}

class GameViewModel : ViewModel() {
    var state by mutableStateOf(GameState())
        private set
    var offlineReward by mutableStateOf(0L)
        private set

    init {
        SaveManager.load()?.let { state = it; calculateOffline() }
        startLoop()
    }

    private fun calculateOffline() {
        val seconds = (System.currentTimeMillis() - state.lastSave) / 1000
        if (seconds > 10) {
            val essenceRate = 0.1 + (state.huntbot.gain * 0.05) * (1 + state.prestigeLevel * 0.1)
            val earned = (seconds / 3600.0 * essenceRate).toLong()
            if (earned > 0) { state.essence += earned; offlineReward = earned; save() }
            val huntRate = (state.huntbot.eff * 0.05) + 0.01
            for (i in 1..(seconds * huntRate).toInt()) { addRandomAnimal() }
        }
        state.huntbot.lastTick = System.currentTimeMillis()
    }

    private fun startLoop() {
        MainScope().launch { while(true) { delay(1000); tick() } }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val delta = (now - state.huntbot.lastTick) / 1000.0
        state.huntbot.lastTick = now
        
        if (state.huntbot.active) {
            val essenceRate = 0.1 + (state.huntbot.gain * 0.05) * (1 + state.prestigeLevel * 0.1)
            state.essenceAccum += (delta / 3600.0) * essenceRate
            if (state.essenceAccum >= 1.0) {
                val gained = state.essenceAccum.toLong()
                state.essence += gained
                state.essenceAccum -= gained
            }
            val huntRate = (state.huntbot.eff * 0.05) + 0.01
            if (Random.nextFloat() < huntRate * delta) { addRandomAnimal() }
        }
        save()
    }

    fun addRandomAnimal() {
        val rng = Random.nextInt(1000)
        val animal = when {
            rng < 600 -> Animal(UUID.randomUUID().toString(), "Shadow Rat", 1, 10, 1)
            rng < 900 -> Animal(UUID.randomUUID().toString(), "Neon Fox", 2, 50, 5)
            rng < 990 -> Animal(UUID.randomUUID().toString(), "Aether Wyrm", 3, 500, 50)
            else -> Animal(UUID.randomUUID().toString(), "Void Leviathan", 4, 5000, 500)
        }
        state.inventory.add(animal)
    }

    fun manualHunt() { if (state.gold >= 10) { state.gold -= 10; addRandomAnimal(); save() } }
    fun upgradeTrait(trait: String) {
        val level = when(trait) { "EFF" -> state.huntbot.eff; "DUR" -> state.huntbot.dur; "CST" -> state.huntbot.cost; "GAIN" -> state.huntbot.gain; "EXP" -> state.huntbot.exp; else -> 0 }
        val cost = (100L * (1 + level)) * (1 + state.prestigeLevel)
        if (state.gold >= cost) {
            state.gold -= cost
            when(trait) { "EFF" -> state.huntbot.eff++; "DUR" -> state.huntbot.dur++; "CST" -> state.huntbot.cost++; "GAIN" -> state.huntbot.gain++; "EXP" -> state.huntbot.exp++ }
            save()
        }
    }
    fun sell(animal: Animal) { state.gold += animal.goldValue * (1 + state.prestigeLevel); state.inventory.remove(animal); save() }
    fun sacrifice(animal: Animal) { state.essence += animal.essenceValue; state.inventory.remove(animal); save() }
    fun prestige() { if (state.essence >= 1000) { state.prestigeLevel++; state.gold = 100; state.essence = 0; state.huntbot = Huntbot(); state.inventory.clear(); save() } }
    fun closeOfflinePopup() { offlineReward = 0L }
    private fun save() { SaveManager.save(state) }
}

val NeonCyan = Color(0xFF00E5FF)
val NeonPurple = Color(0xFFB388FF)
val DarkBg = Color(0xFF0A0A0C)
val CardBg = Color(0xFF15151A)

@Composable
fun PremiumButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val gradient = Brush.linearGradient(listOf(NeonCyan, NeonPurple))
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(50.dp).shadow(10.dp, RoundedCornerShape(12.dp)), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
        Box(modifier = Modifier.fillMaxSize().background(gradient, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
fun AetherApp(viewModel: GameViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    LaunchedEffect(Unit) { SaveManager.init(context) }

    MaterialTheme(colorScheme = darkColors(background = DarkBg, surface = CardBg, primary = NeonCyan)) {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    when(currentTab) {
                        0 -> HuntScreen(viewModel)
                        1 -> HuntbotScreen(viewModel)
                        2 -> CollectionScreen(viewModel)
                    }
                }
                NavigationBar(containerColor = CardBg) {
                    NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Text("🏹", fontSize = 24.sp) }, label = { Text("Hunt") })
                    NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Text("🤖", fontSize = 24.sp) }, label = { Text("Huntbot") })
                    NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Text("🎒", fontSize = 24.sp) }, label = { Text("Collection") })
                }
            }
            if (viewModel.offlineReward > 0) {
                AlertDialog(onDismissRequest = { viewModel.closeOfflinePopup() }, title = { Text("Welcome Back!") }, text = { Text("Your Huntbot gathered ${viewModel.offlineReward} Essence while you were offline.") }, confirmButton = { TextButton(onClick = { viewModel.closeOfflinePopup() }) { Text("Claim") } })
            }
        }
    }
}

@Composable
fun HuntScreen(viewModel: GameViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AETHER HUNT", fontSize = 32.sp, fontWeight = FontWeight.Black, color = NeonCyan)
        Spacer(Modifier.height(20.dp))
        Text("Gold: ${viewModel.state.gold}", fontSize = 24.sp, color = Color.Yellow)
        Text("Essence: ${viewModel.state.essence}", fontSize = 24.sp, color = NeonPurple)
        Spacer(Modifier.height(40.dp))
        PremiumButton("Manual Hunt (10 Gold)", viewModel.state.gold >= 10) { viewModel.manualHunt() }
        Spacer(Modifier.height(20.dp))
        PremiumButton("PRESTIGE (Requires 1000 Essence)", viewModel.state.essence >= 1000) { viewModel.prestige() }
        Text("Prestige Level: ${viewModel.state.prestigeLevel} (x${1 + viewModel.state.prestigeLevel} Multiplier)", color = Color.Gray)
    }
}

@Composable
fun HuntbotScreen(viewModel: GameViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("HUNTBOT UPGRADES", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NeonCyan, modifier = Modifier.padding(bottom = 8.dp)) }
        traitItem("Efficiency", viewModel.state.huntbot.eff, "EFF", viewModel)
        traitItem("Duration", viewModel.state.huntbot.dur, "DUR", viewModel)
        traitItem("Cost Reduction", viewModel.state.huntbot.cost, "CST", viewModel)
        traitItem("Essence Gain", viewModel.state.huntbot.gain, "GAIN", viewModel)
        traitItem("XP Gain", viewModel.state.huntbot.exp, "EXP", viewModel)
    }
}

@Composable
fun traitItem(name: String, level: Int, id: String, viewModel: GameViewModel) {
    val cost = (100L * (1 + level)) * (1 + viewModel.state.prestigeLevel)
    Box(modifier = Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(16.dp)).border(2.dp, NeonCyan, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Level: $level", color = Color.Gray)
                Text("Cost: $cost Gold", color = Color.Yellow, fontSize = 12.sp)
            }
            Button(onClick = { viewModel.upgradeTrait(id) }, enabled = viewModel.state.gold >= cost) { Text("UPGRADE") }
        }
    }
}

@Composable
fun CollectionScreen(viewModel: GameViewModel) {
    Column {
        Text("INVENTORY (${viewModel.state.inventory.size})", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NeonCyan, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.state.inventory) { animal ->
                Box(modifier = Modifier.fillMaxWidth().background(CardBg, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(animal.name, fontWeight = FontWeight.Bold, color = rarityColor(animal.rarity))
                            Text("Sell: ${animal.goldValue}G | Sac: ${animal.essenceValue}E", color = Color.Gray, fontSize = 12.sp)
                        }
                        Row {
                            TextButton(onClick = { viewModel.sell(animal) }) { Text("SELL", color = Color.Yellow) }
                            TextButton(onClick = { viewModel.sacrifice(animal) }) { Text("SAC", color = NeonPurple) }
                        }
                    }
                }
            }
        }
    }
}

fun rarityColor(rarity: Int) = when(rarity) { 1 -> Color.Gray; 2 -> Color.Green; 3 -> Color.Blue; 4 -> NeonPurple; else -> Color.White }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AetherApp() }
    }
}
