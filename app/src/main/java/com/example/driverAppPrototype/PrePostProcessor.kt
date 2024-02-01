package com.example.driverAppPrototype

import android.graphics.Rect
import java.util.Arrays

class Result(var classIndex: Int, var score: Float, var rect: Rect)
object PrePostProcessor {
    var NO_MEAN_RGB = floatArrayOf(0.0f, 0.0f, 0.0f)
    var NO_STD_RGB = floatArrayOf(1.0f, 1.0f, 1.0f)

    // model input image size
    var mInputWidth = 640
    var mInputHeight = 640

    private const val mOutputRow = 25200 // as decided by the YOLOv5 model for input image of size 640x640
    private var mOutputColumn = 6 // left, top, right, bottom, score and 80 class probability
    private const val mThreshold = 0.60f // score above which a detection is generated, change that to arg in setThreshold function later
    private const val mNmsLimit = 15
    lateinit var mClasses: Array<String>
    // The two methods nonMaxSuppression and IOU below are ported from https://github.com/hollance/YOLO-CoreML-MPSNNGraph/blob/master/Common/Helpers.swift
    /**
     * Removes bounding boxes that overlap too much with other boxes that have
     * a higher score.
     * - Parameters:
     * - boxes: an array of bounding boxes and their scores
     * - limit: the maximum number of boxes that will be selected
     * - threshold: used to decide whether boxes overlap too much
     */
    private fun nonMaxSuppression(
        boxes: ArrayList<Result>
    ): ArrayList<Result> {

        boxes.sortWith(java.util.Comparator { o1, o2 -> o1.score.compareTo(o2.score) })
        val selected = ArrayList<Result>()
        val active = BooleanArray(boxes.size)
        Arrays.fill(active, true)
        var numActive = active.size

        var done = false
        var i = 0
        while (i < boxes.size && !done) {
            if (active[i]) {
                val boxA = boxes[i]
                selected.add(boxA)
                if (selected.size >= mNmsLimit) break
                for (j in i + 1 until boxes.size) {
                    if (active[j]) {
                        val boxB = boxes[j]
                        if (iou(boxA.rect, boxB.rect) > mThreshold) {
                            active[j] = false
                            numActive -= 1
                            if (numActive <= 0) {
                                done = true
                                break
                            }
                        }
                    }
                }
            }
            i++
        }
        return selected
    }

    /**
     * Computes intersection-over-union overlap between two bounding boxes.
     */
    fun iou(a: Rect, b: Rect): Float {
        val areaA = ((a.right - a.left) * (a.bottom - a.top)).toFloat()
        if (areaA <= 0.0) return 0.0f
        val areaB = ((b.right - b.left) * (b.bottom - b.top)).toFloat()
        if (areaB <= 0.0) return 0.0f
        val intersectionMinX = Math.max(a.left, b.left).toFloat()
        val intersectionMinY = Math.max(a.top, b.top).toFloat()
        val intersectionMaxX = Math.min(a.right, b.right).toFloat()
        val intersectionMaxY = Math.min(a.bottom, b.bottom).toFloat()
        val intersectionArea = Math.max(intersectionMaxY - intersectionMinY, 0f) *
                Math.max(intersectionMaxX - intersectionMinX, 0f)
        return intersectionArea / (areaA + areaB - intersectionArea)
    }

    fun outputsToNMSPredictions(
        outputs: FloatArray,
        imgSizeX: Float,
        imgSizeY: Float
    ): ArrayList<Result> {
        val results = ArrayList<Result>()
        for (i in 0 until mOutputRow) {
            if (outputs[i * mOutputColumn + 4] > mThreshold) {
                val x = outputs[i * mOutputColumn]
                val y = outputs[i * mOutputColumn + 1]
                val w = outputs[i * mOutputColumn + 2]
                val h = outputs[i * mOutputColumn + 3]
                val left = imgSizeX / mInputWidth * (x - w / 2)
                val top = imgSizeY / mInputHeight * (y - h / 2)
                val right = imgSizeX / mInputWidth * (x + w / 2)
                val bottom = imgSizeY / mInputHeight * (y + h / 2)
                var max = outputs[i * mOutputColumn + 5]
                var cls = 0
                for (j in 0 until mOutputColumn - 5) {
                    if (outputs[i * mOutputColumn + 5 + j] > max) {
                        max = outputs[i * mOutputColumn + 5 + j]
                        cls = j
                    }
                }
                val rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                val result = Result(cls, outputs[i * mOutputColumn + 4], rect)
                results.add(result)
            }
        }
        return nonMaxSuppression(results)
    }

    fun assignClasses(classes: Array<String>) {
        mClasses = classes
        mOutputColumn = mClasses.size + 5
    }
}