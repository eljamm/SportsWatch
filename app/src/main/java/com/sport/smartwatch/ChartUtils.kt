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

        fun init() {
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

//        data.init()
            if (data.array.size != 0) {
                for (element in data.array) {
                    entries.add(Entry(element.x.toFloat(), element.y.toFloat()))
                }
            }

            setup(entries)
        }

        fun update() {
            data.update()
            entries.removeAll(entries)

            for (element in data.array) {
                entries.add(Entry(element.x.toFloat(), element.y.toFloat()))
            }

            setup(entries)
        }
    }

    inner class ChartData(limit: Int = 10) {
        private val maxNumber: Int = limit
        val array: ArrayList<Point> = ArrayList(maxNumber)
        var index: Int = 0

        fun init() {
            repeat(maxNumber) {
                incrementIndex()
                array.addAll(listOf(Point(index,0)))
            }
        }

        fun update() {
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

        fun addData(number: Int) {
            incrementIndex()
            array.add(Point(index, number))
        }

        private fun randomData(times: Int) {
            repeat(times) {
                incrementIndex()
                val number = (70..120).random()
                array.add(Point(index, number))
            }
        }

        private fun incrementIndex() { if (index>maxNumber) { index = 0 } else { index++ } }
    }
}