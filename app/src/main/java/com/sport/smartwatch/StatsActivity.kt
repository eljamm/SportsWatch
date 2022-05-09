package com.sport.smartwatch

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val TAG = "SportsWatch"   // Used for debugging

class StatsActivity : AppCompatActivity() {
    private var profileList = ArrayList<Profile>()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        loadData()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStart() {
        super.onStart()

        val intentHeart = Intent(this@StatsActivity, MainActivity::class.java)

        val submit = findViewById<Button>(R.id.btnSUBMIT)
        submit.setOnClickListener{
            val name = findViewById<EditText>(R.id.edtN)
            val weight = findViewById<EditText>(R.id.edtW)
            val age = findViewById<EditText>(R.id.edtA)
            val gender = findViewById<EditText>(R.id.edtG)

            if (name.text.isEmpty() or weight.text.isEmpty() or
                age.text.isEmpty() or gender.text.isEmpty()) {
                Toast.makeText(this@StatsActivity, "Please fill out all the forms",
                    Toast.LENGTH_LONG).show()
            } else {
                val profile = Profile(name.text.toString(), weight.text.toString().toFloat(),
                    age.text.toString().toInt(), gender.text.toString())

                profileList.add(profile)
                saveData()
                startActivity(intentHeart)
            }
        }
    }

    private fun loadData() {
        // method to load arraylist from shared prefs
        // initializing our shared prefs with name as
        // shared preferences.
        val sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE)

        // creating a variable for gson.
        val gson = Gson()

        // below line is to get to string present from our
        // shared prefs if not present setting it as null.
        val json: String? = sharedPreferences.getString("profiles", null)

        // below line is to get the type of our array list.
        val type = object : TypeToken<ArrayList<Profile?>?>() {}.type

        if (json != null) {
            // in below line we are getting data from gson
            // and saving it to our array list
            try {
                profileList = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e(TAG, "loadData: failed", e)
            }
        }

        // checking below if the array list is empty or not
        if (profileList == null) {
            // if the array list is empty
            // creating a new array list.
            profileList = ArrayList<Profile>()
        }
    }

    private fun saveData() {
        // method for saving the data in array list.
        // creating a variable for storing data in
        // shared preferences.
        val sharedPreferences = getSharedPreferences("shared preferences", MODE_PRIVATE)

        // creating a variable for editor to
        // store data in shared preferences.
        val editor = sharedPreferences.edit()

        // creating a new variable for gson.
        val gson = Gson()

        // getting data from gson and storing it in a string.
        val json = gson.toJson(profileList)

        // below line is to save data in shared
        // prefs in the form of string.
        editor.putString("profiles", json)

        // below line is to apply changes
        // and save data in shared prefs.
        editor.apply()

        // after saving data we are displaying a toast message.
        Toast.makeText(this, "Saved Array List to Shared preferences. ",
            Toast.LENGTH_SHORT).show()
    }
}