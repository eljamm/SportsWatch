package com.sport.smartwatch

import android.graphics.Point
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

class HeartDisplay : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_display)

        val chart: LineChart = findViewById<LineChart>(R.id.chart)

        val data: ArrayList<Point> = ArrayList()
        data.add(Point(70, 1))
        data.add(Point(80, 2))
        data.add(Point(81, 3))
        data.add(Point(90, 4))
        data.add(Point(92, 5))
        data.add(Point(85, 6))
        data.add(Point(81, 7))
        data.add(Point(78, 8))

        val entries: ArrayList<Entry> = ArrayList()
        for (element in data) {
            entries.add(Entry(element.y.toFloat(), element.x.toFloat()))
        }

        val dataSet = LineDataSet(entries, "Label")
        val lineData = LineData(dataSet)
        chart.data = lineData
        chart.invalidate()
    }
}