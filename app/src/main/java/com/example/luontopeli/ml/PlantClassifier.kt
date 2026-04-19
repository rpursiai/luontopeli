package com.example.luontopeli.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PlantClassifier {
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )

    private val plantKeywords = setOf(
        "plant", "flower", "tree", "shrub", "leaf", "fern", "moss",
        "grass", "herb", "bush", "berry", "pine", "birch", "spruce",
        "algae", "lichen", "bark", "nature", "forest", "woodland",
        "botanical", "flora"
    )

    private val mushroomKeywords = setOf(
        "mushroom", "fungus", "toadstool", "mold"
    )

    private val animalKeywords = setOf(
        "animal", "bird", "mammal", "insect", "butterfly", "spider",
        "beetle", "ant", "bee", "fly", "dog", "cat", "fox", "wolf",
        "deer", "hare", "rabbit", "squirrel", "frog", "toad", "fish"
    )

    suspend fun classify(imageUri: Uri, context: Context): ClassificationResult {
        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                labeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        val classifiedLabels = labels.filter { label ->
                            val text = label.text.lowercase()
                            plantKeywords.any { it in text } ||
                                mushroomKeywords.any { it in text } ||
                                animalKeywords.any { it in text }
                        }

                        val result = if (classifiedLabels.isNotEmpty()) {
                            val best = classifiedLabels.maxByOrNull { it.confidence }!!
                            ClassificationResult.Success(
                                label = best.text,
                                confidence = best.confidence,
                                allLabels = labels.take(5)
                            )
                        } else {
                            ClassificationResult.NotNature(
                                allLabels = labels.take(3)
                            )
                        }
                        continuation.resume(result)
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    fun close() {
        labeler.close()
    }
}

sealed class ClassificationResult {
    data class Success(
        val label: String,
        val confidence: Float,
        val allLabels: List<ImageLabel>
    ) : ClassificationResult()

    data class NotNature(
        val allLabels: List<ImageLabel>
    ) : ClassificationResult()

    data class Error(val message: String) : ClassificationResult()
}
