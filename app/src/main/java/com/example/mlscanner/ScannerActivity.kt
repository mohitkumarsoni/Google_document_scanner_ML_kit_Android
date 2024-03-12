package com.example.mlscanner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import coil.clear
import coil.dispose
import coil.load
import com.example.mlscanner.databinding.ActivityScannerBinding
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

class ScannerActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityScannerBinding.inflate(layoutInflater)
    }

    private val FULL_MODE = "FULL"
    private val BASE_MODE = "BASE"
    private val BASE_MODE_WITH_FILTER = "BASE_WITH_FILTER"
    private var selectedMode = FULL_MODE
    private var galleryImportAllowed = true
    private var shareIntent : Intent ?= null

    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        populateModeSelector()

        binding.galleryImportSwitch.setOnClickListener {
            if (binding.galleryImportSwitch.isActivated){
                galleryImportAllowed = true
            }
            if (binding.galleryImportSwitch.isActivated.not()){
                galleryImportAllowed = false
            }
        }

        scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                handleActivityResult(it)
        }

    }

    private fun populateModeSelector() {
        val options: MutableList<String> = ArrayList()
        options.add(FULL_MODE)
        options.add(BASE_MODE)
        options.add(BASE_MODE_WITH_FILTER)

        val dataAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        binding.modeSpinner.adapter = dataAdapter
        binding.modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedMode = parent?.getItemAtPosition(position).toString()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

        }

    }

    private fun handleActivityResult(activityResult: ActivityResult) {
        val resultCode: Int = activityResult.resultCode
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)

        if (resultCode == RESULT_OK && result != null) {
            val pages = result.pages

            binding.resultInfoTv.text = getString(R.string.scan_result).plus(result.toString())
            if (pages.isNullOrEmpty().not()) {
                binding.imagePreviewIv.load(pages?.get(0)?.imageUri)
            }
            result.pdf?.uri?.path?.let { path ->
                val externalUri =
                    FileProvider.getUriForFile(this, packageName + ".provider", File(path))

                shareIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, externalUri)
                    type = "application/pdf"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "share pdf"))
            }

        }else if (resultCode == RESULT_CANCELED){
            binding.resultInfoTv.text = "scanner cancelled"
        }else{
            binding.resultInfoTv.text = "failed to scan"
        }

    }

    @Suppress("UNUSED_PARAMETER")
    fun scanButtonClicked(view: View) {
        binding.resultInfoTv.text = ""
        binding.imagePreviewIv.dispose()

        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setGalleryImportAllowed(galleryImportAllowed)

        when(selectedMode){
            FULL_MODE -> options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            BASE_MODE -> options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            BASE_MODE_WITH_FILTER -> options.setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER)
            else -> Log.d("TAG", "Unknown selection : $selectedMode")
        }

        val pageLimitInput = binding.numberOfPagesEt.text.toString()
        if (pageLimitInput.isNotEmpty()){
            try {
                options.setPageLimit(pageLimitInput.toInt())
            }catch (t: Throwable){
                binding.resultInfoTv.text = t.message
                return
            }
        }

        GmsDocumentScanning.getClient(options.build())
            .getStartScanIntent(this)
            .addOnSuccessListener { scannerLauncher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { binding.resultInfoTv.text = getString(R.string.error_default_message, it.message) }

    }

    @Suppress("UNUSED_PARAMETER")
    fun onShareClick(view: View) {
        if (shareIntent!=null) {
            startActivity(Intent.createChooser(shareIntent, "share pdf"))
            return
        }
        Toast.makeText(this, "nothing to share", Toast.LENGTH_SHORT).show()
    }


}
