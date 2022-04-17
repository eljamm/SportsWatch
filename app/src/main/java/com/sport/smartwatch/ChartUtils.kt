package com.sport.smartwatch

import android.graphics.Color
import android.graphics.Point
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class ChartUtils {
    inner class Chart(private val lineChart: LineChart) {
        private val data = ChartData(20)
        private val entries: ArrayList<Entry> = ArrayList()

        init {
            lineChart.setNoDataText("Waiting for data")
            lineChart.description.isEnabled = false
            lineChart.setDrawGridBackground(false)
            lineChart.setDrawBorders(false)
            lineChart.axisLeft.isEnabled = true
            lineChart.axisLeft.spaceTop = 40F
            lineChart.axisLeft.spaceBottom = 40F
            lineChart.axisRight.isEnabled = false
            lineChart.xAxis.isEnabled = false
            lineChart.setDrawMarkers(false)
            lineChart.legend.isEnabled = false

            setup(entries)
        }

        private fun setup(entries: ArrayList<Entry>) {
            val lineDataSet = LineDataSet(entries, "")

            lineDataSet.lineWidth = 1.75f
            lineDataSet.circleRadius = 5f
            lineDataSet.circleHoleRadius = 2.5f
            lineDataSet.color = Color.BLUE
            lineDataSet.setCircleColor(Color.BLUE)
            lineDataSet.highLightColor = Color.BLUE
            lineDataSet.setDrawValues(false)

            val lineData = LineData(lineDataSet)

            lineChart.data = lineData
            lineChart.invalidate()
        }

        fun update() {
            entries.removeAll(entries)

            for (element in data.array) {
                entries.add(Entry(element.x.toFloat(), element.y.toFloat()))
            }

            setup(entries)
        }

        fun add(number: Int) {
            data.addData(number)
            update()
        }

        fun addRandom() {
            data.addRandom()
            update()
        }
    }

    inner class ChartData(limit: Int = 10) {
        private val maxNumber: Int = limit
        private var index: Int = 0
        val array: ArrayList<Point> = ArrayList(maxNumber)

        fun addData(number: Int) {
            if (index < maxNumber) {
                incrementIndex()
                array.add(Point(index, number))
            } else {
                index = maxNumber
                for (element in array) { element.x -= 1; }
                array.removeAt(0)
                array.add(Point(index, number))
            }
        }

        fun addRandom() {
            if (index < maxNumber) {
                incrementIndex()
                randomData(1)
            } else {
                index = maxNumber
                for (element in array) { element.x -= 1; }
                array.removeAt(0)
                randomData(1)
            }
        }

        private fun randomData(times: Int) {
            repeat(times) {
                incrementIndex()
                val number = (70..120).random()
                array.add(Point(index, number))
            }
        }

        fun zeros() {
            repeat(maxNumber) {
                incrementIndex()
                array.addAll(listOf(Point(index,0)))
            }
        }

        private fun incrementIndex() { if (index>maxNumber) { index = 0 } else { index++ } }
    }
}