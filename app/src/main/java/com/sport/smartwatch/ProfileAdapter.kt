package com.sport.smartwatch

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

/**
 * Custom ListView adapter that accepts an ArrayList of objects
 */
class ProfileAdapter(context: Context, dataset: ArrayList<Profile>) :
    ArrayAdapter<Profile>(context, R.layout.item, dataset) {

    /**
     * Holds item Views for later modification
     */
    private inner class ViewHolder {
        lateinit var txtName: TextView
        lateinit var imgType: ImageView
    }

    // Specify a custom type of View to use as a data object in the ListView
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val person: Profile = getItem(position)!!  // Element at the given position of the dataset
        val viewHolder = ViewHolder()
        val mConvertView: View  // Should not be returned null or the app will crash

        if (convertView == null) {
            val layoutInflater = LayoutInflater.from(context)
            mConvertView = layoutInflater.inflate(R.layout.item, parent, false)

            // Get item Views
            viewHolder.txtName = mConvertView.findViewById(R.id.txtName)
        } else {
            mConvertView = convertView
            mConvertView.tag    // Get the tag for the ConvertView
        }

        // If the variables were not initialized then we need to catch the exception
        try {
            // Modify item Views
            viewHolder.txtName.text = person.name
        } catch (e: UninitializedPropertyAccessException) {
            Log.d("TEST", "Late init variables not initialized.")
        }
        return mConvertView
    }
}