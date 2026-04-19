package com.example.luontopeli.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.luontopeli.data.local.AppDatabase
import com.example.luontopeli.data.local.entity.NatureSpot
import com.example.luontopeli.data.remote.firebase.AuthManager
import com.example.luontopeli.data.remote.firebase.FirestoreManager
import com.example.luontopeli.data.remote.firebase.StorageManager
import com.example.luontopeli.data.repository.NatureSpotRepository
import com.example.luontopeli.ml.ClassificationResult
import com.example.luontopeli.ml.PlantClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = NatureSpotRepository(
        dao = db.natureSpotDao(),
        firestoreManager = FirestoreManager(),
        storageManager = StorageManager(),
        authManager = AuthManager()
    )
    private val classifier = PlantClassifier()
    private val _capturedImagePath = MutableStateFlow<String?>(null)
    val capturedImagePath: StateFlow<String?> = _capturedImagePath.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _classificationResult = MutableStateFlow<ClassificationResult?>(null)
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult.asStateFlow()
    private val _comment = MutableStateFlow("")
    val comment: StateFlow<String> = _comment.asStateFlow()

    fun setComment(value: String) {
        _comment.value = value
    }

    fun takePhoto(context: Context, imageCapture: ImageCapture) {
        _isLoading.value = true
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outputDir = File(context.filesDir, "nature_photos").also { it.mkdirs() }
        val outputFile = File(outputDir, "IMG_${timestamp}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    _capturedImagePath.value = outputFile.absolutePath
                    viewModelScope.launch {
                        try {
                            val uri = Uri.fromFile(outputFile)
                            _classificationResult.value = classifier.classify(uri, context)
                        } catch (e: Exception) {
                            _classificationResult.value = ClassificationResult.Error(e.message ?: "Tuntematon virhe")
                        }
                        _isLoading.value = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    _isLoading.value = false
                }
            }
        )
    }

    fun clearCapturedImage() {
        _capturedImagePath.value = null
        _classificationResult.value = null
        _comment.value = ""
    }

    fun saveCurrentSpot() {
        val imagePath = _capturedImagePath.value ?: return
        viewModelScope.launch {
            val result = _classificationResult.value
            val (latitude, longitude) = getLastKnownCoordinates()
            val label = (result as? ClassificationResult.Success)?.label
            val spot = NatureSpot(
                name = label ?: "Luontolöytö",
                latitude = latitude,
                longitude = longitude,
                imageLocalPath = imagePath,
                imageFirebaseUrl = null,
                plantLabel = label,
                confidence = (result as? ClassificationResult.Success)?.confidence,
                userId = "anonymous",
                timestamp = System.currentTimeMillis(),
                synced = false,
                comment = _comment.value,
                category = inferCategory(label)
            )
            repository.insertSpot(spot)
            clearCapturedImage()
        }
    }

    private fun inferCategory(label: String?): String {
        val text = label?.lowercase() ?: return "plant"
        return when {
            listOf("mushroom", "fungus", "toadstool", "mold").any { it in text } -> "mushroom"
            listOf("animal", "bird", "mammal", "insect", "butterfly", "spider", "beetle", "ant", "bee", "fly", "dog", "cat", "fox", "wolf", "deer", "hare", "rabbit", "squirrel", "frog", "toad", "fish").any { it in text } -> "animal"
            else -> "plant"
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownCoordinates(): Pair<Double, Double> {
        val manager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val location = listOfNotNull(gps, network).maxByOrNull { it.time }
        return if (location != null) location.latitude to location.longitude else 0.0 to 0.0
    }

    override fun onCleared() {
        super.onCleared()
        classifier.close()
    }
}
