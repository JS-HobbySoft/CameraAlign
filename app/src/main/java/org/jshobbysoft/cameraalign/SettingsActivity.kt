/*
 * Copyright (c) 2022 JS HobbySoft.
 * All rights reserved.
 */

package org.jshobbysoft.cameraalign

import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val pref2 : EditTextPreference? = findPreference("textTransparencyKey")
            pref2?.setOnBindEditTextListener {
                    editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            pref2?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
//                    val re = Regex("[01][0-9]/[0-3][0-9]/[12][09][0-9][0-9] [0-2][0-9]:[0-5][0-9]:[0-9][0-9]")
                    val result = newValue?.toString()?.toInt()
                    if (result in 0..255) {
                        true
                    } else {
                        Toast.makeText(requireActivity(),R.string.toastBadTransparency,Toast.LENGTH_LONG).show()
                        false
                    }
                }

            val pref3 : EditTextPreference? = findPreference("textRedKey")
            pref3?.setOnBindEditTextListener {
                    editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            pref3?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val result = newValue?.toString()?.toInt()
                    if (result in 0..255) {
                        true
                    } else {
                        Toast.makeText(requireActivity(),"Color value must be between 0-255",Toast.LENGTH_LONG).show()
                        false
                    }
                }

            val pref4 : EditTextPreference? = findPreference("textGreenKey")
            pref4?.setOnBindEditTextListener {
                    editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            pref4?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val result = newValue?.toString()?.toInt()
                    if (result in 0..255) {
                        true
                    } else {
                        Toast.makeText(requireActivity(),"Color value must be between 0-255",Toast.LENGTH_LONG).show()
                        false
                    }
                }

            val pref5 : EditTextPreference? = findPreference("textBlueKey")
            pref5?.setOnBindEditTextListener {
                    editText -> editText.inputType = InputType.TYPE_CLASS_NUMBER
            }
            pref5?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val result = newValue?.toString()?.toInt()
                    if (result in 0..255) {
                        true
                    } else {
                        Toast.makeText(requireActivity(),"Color value must be between 0-255",Toast.LENGTH_LONG).show()
                        false
                    }
                }
        }
    }
}