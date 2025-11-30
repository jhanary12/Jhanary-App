package com.example.rentmanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- DATA MODELS ---

data class Room(
    val id: String = "",
    val name: String = "",
    val userId: String = ""
)

data class PaymentRecord(
    val id: String = "",
    val roomId: String = "",
    val date: Date = Date(),
    val rentAmount: Double = 0.0,
    val waterAmount: Double = 0.0,
    val electricAmount: Double = 0.0
) {
    val total: Double get() = rentAmount + waterAmount + electricAmount
}

// --- VIEW MODEL ---

class PropertyViewModel : ViewModel() {
    // Fix: Use getInstance() instead of ktx extensions to avoid resolution errors
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _rooms = mutableStateListOf<Room>()
    val rooms: List<Room> get() = _rooms

    private val _records = mutableStateListOf<PaymentRecord>()
    val records: List<PaymentRecord> get() = _records

    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    init {
        // Auth Listener
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                loadData()
                setupFCM()
            } else {
                _rooms.clear()
                _records.clear()
            }
        }
    }

    private fun setupFCM() {
        FirebaseMessaging.getInstance().subscribeToTopic("monthly_reminders")
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    // Log error if needed
                }
            }
    }

    private fun loadData() {
        val userId = auth.currentUser?.uid ?: return
        isLoading = true

        db.collection("rooms")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    errorMessage = "Error: ${e.message}"
                    return@addSnapshotListener
                }
                _rooms.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Room::class.java)?.let { room ->
                        _rooms.add(room.copy(id = doc.id))
                    }
                }
                isLoading = false
            }

        db.collection("records")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                _records.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(PaymentRecord::class.java)?.let { record ->
                        _records.add(record.copy(id = doc.id))
                    }
                }
            }
    }

    fun addRoom(name: String) {
        val userId = auth.currentUser?.uid ?: return
        val newRoom = Room(name = name, userId = userId)
        db.collection("rooms").add(newRoom)
    }

    fun addRecord(roomId: String, rent: Double, water: Double, electric: Double) {
        val userId = auth.currentUser?.uid ?: return
        val newRecord = hashMapOf(
            "roomId" to roomId,
            "userId" to userId,
            "date" to Date(),
            "rentAmount" to rent,
            "waterAmount" to water,
            "electricAmount" to electric
        )
        db.collection("records").add(newRecord)
    }

    fun getRecordsForRoom(roomId: String): List<PaymentRecord> {
        return _records.filter { it.roomId == roomId }.sortedByDescending { it.date }
    }

    fun getCurrentMonthTotal(): Double {
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        return _records.filter {
            val recordCal = Calendar.getInstance().apply { time = it.date }
            recordCal.get(Calendar.MONTH) == currentMonth && recordCal.get(Calendar.YEAR) == currentYear
        }.sumOf { it.total }
    }

    fun getCurrentYearTotal(): Double {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        return _records.filter {
            val recordCal = Calendar.getInstance().apply { time = it.date }
            recordCal.get(Calendar.YEAR) == currentYear
        }.sumOf { it.total }
    }

    fun signOut() {
        auth.signOut()
    }
}

// --- MAIN ACTIVITY ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ -> }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: PropertyViewModel = viewModel()
                    val auth = FirebaseAuth.getInstance()

                    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            isLoggedIn = firebaseAuth.currentUser != null
                        }
                        auth.addAuthStateListener(listener)
                        onDispose { auth.removeAuthStateListener(listener) }
                    }

                    if (isLoggedIn) {
                        RentManagerApp(viewModel)
                    } else {
                        LoginScreen()
                    }
                }
            }
        }
    }
}

// --- LOGIN SCREEN ---

@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegistering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    LocalContext.current
    val auth = FirebaseAuth.getInstance() // Fix: Get instance explicitly

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = if (isRegistering) "Create Account" else "Rent Manager Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    if (isRegistering) {
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnFailureListener { e -> error = e.message ?: "Error" } // Fix: explicit 'e'
                    } else {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnFailureListener { e -> error = e.message ?: "Error" } // Fix: explicit 'e'
                    }
                }
            }
        ) {
            Text(if (isRegistering) "Sign Up" else "Login")
        }

        TextButton(onClick = { isRegistering = !isRegistering; error = "" }) {
            Text(if (isRegistering) "Already have an account? Login" else "Need an account? Sign Up")
        }
    }
}

// --- APP CONTENT (Dashboard) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentManagerApp(viewModel: PropertyViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var selectedRoom by remember { mutableStateOf<Room?>(null) }
    var showAddRoomDialog by remember { mutableStateOf(false) }
    var showAddRecordDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = if (currentScreen == Screen.Dashboard) "Rent Manager" else selectedRoom?.name ?: "Details")
                },
                actions = {
                    IconButton(onClick = { viewModel.signOut() }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    if (currentScreen == Screen.RoomDetails) {
                        IconButton(onClick = { currentScreen = Screen.Dashboard }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (currentScreen == Screen.Dashboard) {
                        showAddRoomDialog = true
                    } else {
                        showAddRecordDialog = true
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (currentScreen == Screen.Dashboard) {
                DashboardScreen(
                    viewModel = viewModel,
                    onRoomClick = { room ->
                        selectedRoom = room
                        currentScreen = Screen.RoomDetails
                    }
                )
            } else {
                selectedRoom?.let { room ->
                    RoomDetailScreen(
                        room = room,
                        viewModel = viewModel
                    )
                }
            }
        }

        if (showAddRoomDialog) {
            AddRoomDialog(
                onDismiss = { showAddRoomDialog = false },
                onConfirm = { name ->
                    viewModel.addRoom(name)
                    showAddRoomDialog = false
                }
            )
        }

        if (showAddRecordDialog && selectedRoom != null) {
            AddRecordDialog(
                roomName = selectedRoom!!.name,
                onDismiss = { showAddRecordDialog = false },
                onConfirm = { rent, water, electric ->
                    viewModel.addRecord(selectedRoom!!.id, rent, water, electric)
                    showAddRecordDialog = false
                }
            )
        }
    }
}

// --- REUSED COMPONENTS ---

@Composable
fun DashboardScreen(viewModel: PropertyViewModel, onRoomClick: (Room) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Financial Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsCard("This Month", viewModel.getCurrentMonthTotal(), Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
            StatsCard("This Year", viewModel.getCurrentYearTotal(), Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Rooms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.rooms) { room ->
                RoomCard(room = room, onClick = { onRoomClick(room) })
            }
        }
    }
}

@Composable
fun StatsCard(title: String, amount: Double, modifier: Modifier = Modifier, color: Color) {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "PH"))
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(text = format.format(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RoomCard(room: Room, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = room.name, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun RoomDetailScreen(room: Room, viewModel: PropertyViewModel) {
    val records = viewModel.getRecordsForRoom(room.id)
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "PH"))

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Payment History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No records found.", color = Color.Gray) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records) { record ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = dateFormat.format(record.date), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(text = currencyFormat.format(record.total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("Rent", style = MaterialTheme.typography.labelSmall); Text(currencyFormat.format(record.rentAmount)) }
                                Column { Text("Water", style = MaterialTheme.typography.labelSmall); Text(currencyFormat.format(record.waterAmount)) }
                                Column { Text("Electric", style = MaterialTheme.typography.labelSmall); Text(currencyFormat.format(record.electricAmount)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddRoomDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var roomName by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add New Room", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = roomName, onValueChange = { roomName = it }, label = { Text("Room Name/Number") }, singleLine = true)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { if(roomName.isNotBlank()) onConfirm(roomName) }) { Text("Add") }
                }
            }
        }
    }
}

@Composable
fun AddRecordDialog(roomName: String, onDismiss: () -> Unit, onConfirm: (Double, Double, Double) -> Unit) {
    var rent by remember { mutableStateOf("") }
    var water by remember { mutableStateOf("") }
    var electric by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add Record", style = MaterialTheme.typography.titleLarge)
                Text("For $roomName", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = rent, onValueChange = { if(it.all { c -> c.isDigit() || c == '.' }) rent = it }, label = { Text("Rent Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = water, onValueChange = { if(it.all { c -> c.isDigit() || c == '.' }) water = it }, label = { Text("Water Bill") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = electric, onValueChange = { if(it.all { c -> c.isDigit() || c == '.' }) electric = it }, label = { Text("Electric Bill") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }, singleLine = true)
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(rent.toDoubleOrNull()?:0.0, water.toDoubleOrNull()?:0.0, electric.toDoubleOrNull()?:0.0) }) { Text("Save") }
                }
            }
        }
    }
}

// --- ENUM ---
enum class Screen { Dashboard, RoomDetails }