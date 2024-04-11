package fr.uge.mobistorytra

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources.Theme
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.Collections
import kotlin.concurrent.thread
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.Locale


class MainActivity : ComponentActivity() {

    private val wikipediaContentCache = mutableMapOf<String, String>()
    private val context = this
    private var eventoftheday: Event? = null

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun LocationPermissionFeature() {
        val fineLocationPermissionState = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION)

        when (fineLocationPermissionState.status) {
            is PermissionStatus.Granted -> {
                Text("Permission de localisation accordée. Vous pouvez utiliser la localisation ici.")
            }
            is PermissionStatus.Denied -> {
                val shouldShowRationale = (fineLocationPermissionState.status as PermissionStatus.Denied).shouldShowRationale
                if (shouldShowRationale) {
                    Button(onClick = { fineLocationPermissionState.launchPermissionRequest() }) {
                        Text("Demander la permission de localisation")
                    }
                } else {
                    Button(onClick = { fineLocationPermissionState.launchPermissionRequest() }) {
                        Text("Demander la permission de localisation")
                    }
                }
            }
        }
        return
    }


    fun obtainLocation(context: Context, onLocationUpdated: (Double, Double) -> Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocationUpdated(location.latitude, location.longitude)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 0f, locationListener)
        }
    }



    /*fun checkFirstRunAndProcessFile(context: Context, dbHelper: EventsDatabaseHelper) {
        val prefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("isFirstRun20", true)

        if (isFirstRun) {
            processJsonFile("events.txt", context, dbHelper)

            prefs.edit().putBoolean("isFirstRun20", false).apply()
        }
    }*/

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), CONTEXT_INCLUDE_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTEXT_INCLUDE_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                eventoftheday?.let { sendEventNotification(context, it) }
            } else {

            }
        }
    }



    fun sendEventNotification(context: Context, event: Event) {
        val notificationId = 1
        val channelId = "event_notification_channel"


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Événement du jour"
            val descriptionText = "Notifications pour l'événement du jour"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle("Événement du jour")
            .setContentText(extractSpecificPart(event.label))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            notify(notificationId, notificationBuilder.build())
        }
    }



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dbHelper = EventsDatabaseHelper(this)
        if(!dbHelper.hasEventsData()){
            val downloader = EventsDownloader(
                this,
                "https://igm.univ-mlv.fr/~chilowi/data/historyevents/events.txt.gz"
            )
            downloader.downloadAndDecompressFile()
            processJsonFile("events.txt", context, dbHelper)
        }
        println("ici")
        val viewModel =
            ViewModelProvider(this, ViewModelFactory(dbHelper))[ItemsViewModel::class.java]


        setContent {
                Column {
                    LocationPermissionFeature()
                    EventListScreen(viewModel)
                }
            }
    }


    @Composable
    fun SortDropdownMenu(selectedOption: DisplayMode, onOptionSelected: (DisplayMode) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        val options = listOf<DisplayMode>(DisplayMode.Populaire, DisplayMode.Temporal, DisplayMode.Alphabetical, DisplayMode.Geo, DisplayMode.Etiquette)

        Box(Modifier.wrapContentSize(Alignment.TopStart)) {
            Text(
                "$selectedOption", modifier = Modifier
                    .clickable(onClick = { expanded = true })
                    .padding(16.dp)
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text("$option") }, onClick = {
                        onOptionSelected(option)
                        expanded = false
                    })
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun findEventsByDate(events: List<Event>, viewModel: ItemsViewModel): Event? {
        val today = LocalDate.now()
        val month = today.monthValue
        val dayOfMonth = today.dayOfMonth
        for (event in events.shuffled()){
            if(event.date?.month == month && event.date.day == dayOfMonth){
                eventoftheday = event
            }
        }
        return null
    }


    @RequiresApi(Build.VERSION_CODES.O)
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun EventListScreen(viewModel: ItemsViewModel) {
        val events by viewModel.items.observeAsState(initial = emptyList())
        var searchText by remember { mutableStateOf("") }
        var showFrise by remember { mutableStateOf(false) } // Contrôle l'affichage de la frise
        var showQuiz by remember { mutableStateOf(false) } // Contrôle l'affichage du quiz
        var showEvents by remember { mutableStateOf(true) } // Contrôle l'affichage des cartes d'événements
        var display by remember {
            mutableStateOf(DisplayMode.Temporal)
        }
        var score by remember { mutableStateOf<Int?>(null) } // Pour stocker le score après le quiz

        findEventsByDate(events, viewModel)
        requestNotificationPermission()



        var latitude by remember { mutableStateOf(0.0) }
        var longitude by remember { mutableStateOf(0.0) }

        DisposableEffect(Unit) {
            obtainLocation(context) { lat, lon ->
                latitude = lat
                longitude = lon
            }

            onDispose { }
        }


        Column {
            SortDropdownMenu(selectedOption = display, onOptionSelected = { option ->
                display = option
                viewModel.sortEvents(option, latitude, longitude)
            })

            Row {
                Button(onClick = {
                    showFrise = !showFrise
                    showQuiz = false
                    showEvents = !showFrise
                }) {
                    Text(text = if (showFrise) "Cacher Frise" else "Afficher Frise")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    showQuiz = !showQuiz
                    showFrise = false
                    showEvents = !showQuiz
                }) {
                    Text(text = if (showQuiz) "Cacher Quiz" else "Afficher Quiz")
                }
            }


            if (showFrise) {
                Frise(eventsList =  events, displayMode = display, latitude, longitude)
            }

            if (showQuiz) {
                Text("Resulat au Quizz précedent : $score")

                QuizScreen(events) { finalScore ->
                    score = finalScore
                    showQuiz = false
                }
            }

            if (showEvents) {
                TextField(
                    value = searchText,
                    onValueChange = { newText ->
                        searchText = newText
                        viewModel.searchEvents(newText)
                    },
                    label = { Text("Recherche") },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn {
                    items(events) { event ->
                        ExpandableEventCard(
                            event = event,
                            onFavoriteClick = { viewModel.toggleFavorite(event.id);
                            }, onEtiquetteClick ={
                                text ->
                                println(text)
                                viewModel.toggleEtiquette(event.id, text)
                            }
                        )
                    }
                }
            }
        }
    }



    enum class DisplayMode {
        Alphabetical,
        Temporal,
        Populaire,
        Geo,
        Etiquette,
    }


    @Composable
    fun DisplayEvent(event: Event) {
        Column {
            Text(text = "ID: ${event.id}", fontSize = 20.sp)
            Text(text = "Label: ${event.label}", fontSize = 20.sp)
            Text(text = "Aliases: ${event.aliases}", fontSize = 20.sp)
            Text(text = "Description: ${event.description}", fontSize = 20.sp)
            Text(text = "Popularity Score: ${event.popularity.en}", fontSize = 20.sp)
            event.date?.let {
                Text(text = "Date: ${it.year}/${it.month}/${it.day}", fontSize = 20.sp)
            }
            event.geo?.let {
                Text(text = "Location: Lat ${it.latitude}, Long ${it.longitude}", fontSize = 20.sp)
            }
            Text(text = "Is Favorite: ${if (event.isFavorite > 0) "Yes" else "No"}", fontSize = 20.sp)
            event.etiquette?.let {
                Text(text = "Etiquette: $it", fontSize = 20.sp)
            }
            event.claims.forEach { claim ->
                Text(text = "Claim ${claim.id}: ${claim.value}", fontSize = 18.sp, color = Color.Gray)
            }
        }
    }

    @Composable
    fun EventDialog(event: Event, onDismiss: () -> Unit) {
        Dialog(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Column {
                    DisplayEvent(event = event)
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }

    @Composable
    fun Frise(eventsList: List<Event>, displayMode: DisplayMode, latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
        var showDialogId by remember { mutableStateOf<Int?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyRow(modifier = modifier) {
                when (displayMode) {
                    DisplayMode.Temporal -> {
                        val startYear = -200
                        val endYear = 2022
                        val totalYears = endYear - startYear
                        items(totalYears + 1) { index ->
                            val year = startYear + index
                            Column() {
                                val relevantEvents = eventsList.filter { it.date?.year == year }
                                Box(
                                    modifier = Modifier
                                        .height(200.dp)
                                        .width(if (relevantEvents.isNotEmpty() || year % 100 == 0) 100.dp else 5.dp)
                                        .background(Color.Blue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (year % 100 == 0 || relevantEvents.isNotEmpty()) {
                                        Text(text = year.toString(), fontSize = 10.sp, color = Color.White)
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {
                                    items(relevantEvents) { event ->
                                        event.date?.let { println(it.year) }
                                        Box(
                                            modifier = Modifier
                                                .size(100.dp)
                                                .background(Color.Gray)
                                                .padding(4.dp).clickable {showDialogId = event.id
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = extractSpecificPart(event.label),
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (showDialogId == event.id) {
                                            EventDialog(event = event, onDismiss = {
                                                showDialogId = null
                                            })
                                        }
                                    }
                                }

                            }
                        }
                    }
                    DisplayMode.Geo -> {
                        val eventsByDistance = eventsList.mapNotNull { event ->
                            event.geo?.let { geo ->
                                val distance = calculateDistance(latitude, longitude, geo.latitude, geo.longitude)
                                distance to event
                            }
                        }.sortedBy { it.first }

                        items(eventsByDistance) { (distance, event) ->
                            if (distance != 0.0) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    Text(
                                        "Distance: ${"%.1f".format(distance/1000)} km",
                                        fontSize = 10.sp,
                                        color = Color.Black
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(Color.Gray)
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = extractSpecificPart(event.label),
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        items(eventsList) { event ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(100.dp)
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(extractFrenchPart(event.label), color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if(lat2 == 0.0 &&  lon2 == 0.0  ){
            return 0.0
        }
        val earthRadius = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
    }

    fun getRandomEvents(events: List<Event>, count: Int = 4): List<Event> {
        return events.shuffled().take(count)
    }


    fun isCorrectOrder(events: List<Event>): Boolean {
        return events.zipWithNext { a, b -> a.date?.year!! <= b.date?.year!! }.all { it }
    }


    @Composable
    fun QuizScreen(events: List<Event>, onFinishQuiz: (Int) -> Unit) {
        val maxRounds = 5
        var currentRound by remember { mutableStateOf(1) }
        var score by remember { mutableStateOf(0) }
        val quizEvents = remember { getRandomEvents(events).toMutableList() }
        var result by remember { mutableStateOf<String?>(null) }
        var selectedIndex by remember { mutableStateOf(-1) }

        if (currentRound <= maxRounds) {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxHeight()) {
                Text("Manche $currentRound sur $maxRounds", style = MaterialTheme.typography.bodyMedium)
                Text("Ordonnez ces événements par date croissante :", style = MaterialTheme.typography.bodyMedium)

                LazyColumn(modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp)) {
                    itemsIndexed(quizEvents) { index, event ->
                        val isSelected = index == selectedIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .border(
                                    width = 2.dp,
                                    color = if (isSelected) Color.Red else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    if (selectedIndex == -1) {
                                        selectedIndex = index
                                    } else if (selectedIndex == index) {
                                        selectedIndex = -1
                                    } else {
                                        // Échanger les événements sélectionnés
                                        Collections.swap(quizEvents, selectedIndex, index)
                                        selectedIndex = -1
                                        result = null
                                    }
                                }
                        ) {
                            Text(
                                "${index + 1}. ${extractFrenchPart(event.label)} (${event.date?.year})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    result = if (isCorrectOrder(quizEvents)) {
                        score++
                        "Correct !"
                    } else {
                        "Incorrect"
                    }

                    if (currentRound < maxRounds) {
                        currentRound++
                        quizEvents.clear()
                        quizEvents.addAll(getRandomEvents(events))
                        selectedIndex = -1
                        result = null
                    } else {
                        currentRound = 0
                        onFinishQuiz(score)
                    }
                }) {
                    Text("Soumettre")
                }
            }
        }
    }






    public fun extractFrenchPart(text: String): String {
        return text.split("||en").firstOrNull()?.removePrefix("fr:")?.trim() ?: ""
    }


    suspend fun fetchWikipediaContent(pageTitle: String): String {
        val cacheKey = pageTitle.trim().toLowerCase()

        // Retourner le contenu du cache s'il existe
        wikipediaContentCache[cacheKey]?.let { return it }

        val client = HttpClient() {
            install(JsonFeature) {
                serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        val url = "https://fr.wikipedia.org/w/api.php" +
                "?action=query" +
                "&prop=extracts" +
                "&exintro" +
                "&titles=${pageTitle}" +
                "&format=json" +
                "&explaintext"

        return try {
            val response: HttpResponse = client.get(url)
            val json = Json.parseToJsonElement(response.receive<String>())
            val pages = json.jsonObject["query"]?.jsonObject?.get("pages")?.jsonObject
            val pageContent = pages?.values?.firstOrNull()?.jsonObject?.get("extract")?.jsonPrimitive?.content ?: "Aucun contenu trouvé pour $pageTitle"

            wikipediaContentCache[cacheKey] = pageContent

            client.close()
            pageContent
        } catch (e: Exception) {
            client.close()
            println("Erreur lors de la récupération du contenu Wikipedia: ${e.localizedMessage}")
            "Erreur lors de la récupération du contenu Wikipedia. Veuillez réessayer."
        }
    }



    fun unescapeJson(jsonString: String): String {
        // Remplace les séquences d'échappement Unicode par le caractère correspondant
        val unicodeCleaned = jsonString.replace(Regex("\\\\u([0-9A-Fa-f]{4})")) { matchResult ->
            val charCode = matchResult.groupValues[1].toInt(16)
            charCode.toChar().toString()
        }
        // Remplace les séquences d'échappement pour les nouvelles lignes
        val newlineCleaned = unicodeCleaned.replace("\\n", "\n")
        // Remplace les autres séquences d'échappement JSON standard (si nécessaire)
        val quotesCleaned = newlineCleaned.replace("\\\"", "\"")

        return quotesCleaned
    }

    fun removeValue(value: String): String {
        // Utilise une expression régulière pour trouver 'Q' suivi de n'importe quel nombre de chiffres
        val pattern = "Q\\d+".toRegex()

        // Retourne une chaîne vide si le motif est trouvé, sinon retourne la valeur originale
        return if (pattern.containsMatchIn(value)) {
            ""
        } else value
    }




    fun extractSpecificPart(input: String): String {
        // Extrait la partie après le dernier ':'
        val partBeforePipe = input.substringBefore("|")

        val partAfterLastColon = partBeforePipe.substringAfterLast(":", partBeforePipe)
        // Ensuite, si cette partie contient '|', on prend tout avant '|'
        // Si la partie initiale contient '(', on prend tout avant '('
        val finalPart = partAfterLastColon.substringBefore("(", partAfterLastColon)
        return finalPart.trim() // Enlevez les espaces blancs autour du texte final
    }


    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ShowEtiquetteDialog(
        showDialog: Boolean,
        onDismiss: () -> Unit,
        text: String,
        onTextChange: (String) -> Unit,
        onConfirm: () -> Unit
    ) {
        if (showDialog) {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Entrez une étiquette") },
                text = {
                    TextField(
                        value = text,
                        onValueChange = onTextChange,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onConfirm() })
                    )
                },
                confirmButton = {
                    Button(onClick = onConfirm) {
                        Text("Confirmer")
                    }
                },
                dismissButton = {
                    Button(onClick = onDismiss) {
                        Text("Annuler")
                    }
                }
            )
        }
    }



    @Composable
    fun ExpandableEventCard(event: Event, onFavoriteClick: () -> Unit, onEtiquetteClick : (String) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        var wikiContent = remember { mutableStateListOf<String>().apply { addAll(List(event.claims.size) { "" }) } }
        var showDialog by remember { mutableStateOf(false) }
        var text by remember { mutableStateOf("") }
        val typography = MaterialTheme.typography
        val showWikiContent by remember { mutableStateOf(List(event.claims.size) { false }.toMutableList()) }
        var recompositionTrigger by remember { mutableStateOf(0) }


        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { expanded = !expanded }
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .animateContentSize()
            ) {
                Text(
                    text = extractFrenchPart(event.label),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (event.isFavorite == 1) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    modifier = Modifier.clickable(onClick = onFavoriteClick)
                )
                Icon(
                    imageVector = Icons.Filled.Create,
                    contentDescription = "Toggle etiquette",
                    modifier =  Modifier.clickable(onClick = {showDialog = true})
                )
                if (event.etiquette != null){
                    Text(text = event.etiquette, fontSize = 20.sp)
                }

                ShowEtiquetteDialog(
                    showDialog = showDialog,
                    onDismiss = { showDialog = false },
                    text = text,
                    onTextChange = { text = it },
                    onConfirm = {
                        onEtiquetteClick(text)
                        showDialog = false
                        text = ""
                    }
                )



                if (expanded) {

                    Text(text = extractFrenchPart(event.description), style = typography.bodyLarge)
                    Text(
                        text = "Popularité: EN: ${event.popularity.en}, FR: ${event.popularity.fr}",
                        style = typography.bodyMedium
                    )
                    recompositionTrigger++
                    event.geo?.let { println(it.latitude) }
                    event.geo?.let { println(it.longitude) }

                    event.claims.forEachIndexed { index, claim ->
                        if (removeValue(extractFrenchPart(claim.value)) != "") {
                            Text("${extractFrenchPart(claim.verboseName)}: ${removeValue(extractFrenchPart(claim.value))}", style = typography.bodyMedium)
                        }
                        claim.item?.let { item ->
                            Text("${extractFrenchPart(item.label)} - ${extractFrenchPart(item.description)}", style = typography.bodyMedium)
                            if(item.wikipedia.isNotEmpty()){
                                Spacer(Modifier.width(15.dp))
                                Button(onClick = {
                                    showWikiContent[index] = !showWikiContent[index]
                                },
                                    Modifier
                                        .fillMaxWidth()
                                        .border(5.dp, Color.Black, RectangleShape)) {
                                    Text(extractSpecificPart(item.wikipedia))
                                }
                                Spacer(Modifier.width(15.dp))


                            }
                            if (index >= 0 && index < showWikiContent.size && index < wikiContent.size) {
                                if(item.wikipedia.isNotEmpty()) {
                                LaunchedEffect(key1 = item.wikipedia) {
                                    wikiContent[index] = fetchWikipediaContent(extractSpecificPart(item.wikipedia))
                                }
                            }

                                if (showWikiContent[index]) {
                                    Text(wikiContent[index], style = typography.bodyLarge)
                                }
                            } else {
                            }

                        }
                    if (claim.value.startsWith("commons:")) {
                                val imageUrl =
                                    "https://commons.wikimedia.org/wiki/Special:FilePath/${
                                        claim.value.removePrefix("commons:")
                                    }"
                                ImageFromUrl(imageUrl)
                            }
                        }
                    }
                }
            }
        }

    @Composable
    fun ImageFromUrl(url: String) {
        var isFullScreen by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val painter = rememberImagePainter(
            request = ImageRequest.Builder(context)
                .data(data = url)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.error)
                .build()
        )

        if (isFullScreen) {
            Dialog(onDismissRequest = { isFullScreen = false }) {
                Image(
                    painter = painter,
                    contentDescription = "Image from Commons",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isFullScreen = false },
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Image(
                painter = painter,
                contentDescription = "Image from Commons",
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isFullScreen = true },
                contentScale = ContentScale.Fit
            )
        }
    }
}

