package com.example.archivepatchertester

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.archivepatchertester.databinding.ActivityMainBinding
import com.google.archivepatcher.applier.FileByFileV1DeltaApplier
import com.google.common.io.BaseEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.util.*
import java.util.zip.GZIPInputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch(Dispatchers.IO) {
            val version25File = File(cacheDir, "Auditor-25.apk")
            assets.open("Auditor-25.apk").use { inStream ->
                version25File.outputStream().use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                }
            }

            val version26File = File(cacheDir, "Auditor-26.apk")
            val version26Digest: String
            assets.open("Auditor-26.apk").use { inStream ->
                DigestOutputStream(version26File.outputStream(), MessageDigest.getInstance("SHA-256")).use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                    version26Digest = BaseEncoding.base16().encode(outStream.messageDigest.digest()).toLowerCase(Locale.ROOT)
                }
            }

            Log.d(TAG, "assets contains these files: ${assets.list("")?.toList()}")

            val patchFile = File(cacheDir, "auditor-25-to-26-patch-gz")
            assets.open("auditor-25-to-26-patch-gz").use { inStream ->
                patchFile.outputStream().use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Size of apk is ${version25File.length()}", Toast.LENGTH_LONG).show()

                Log.d(TAG, "The cache dir: ${cacheDir.listFiles()?.toList()}")
                binding.middleTextView.text = "Applying patch. The cache dir: ${cacheDir.listFiles()?.toList()}"
            }

            val digestFromPatch: String
            GZIPInputStream(patchFile.inputStream()).use { inputPatchDelta ->
                DigestOutputStream(
                    File(cacheDir, "Auditor-26-patched.apk").outputStream(),
                    MessageDigest.getInstance("SHA-256")
                ).use { newFileOut ->
                    FileByFileV1DeltaApplier().applyDelta(
                        version25File,
                        inputPatchDelta,
                        newFileOut
                    )
                    newFileOut.flush()
                    digestFromPatch = BaseEncoding.base16().encode(newFileOut.messageDigest.digest()).toLowerCase(Locale.ROOT)
                }
            }

            /*

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Size of apk is ${version25File.length()}", Toast.LENGTH_LONG).show()

                Log.d(TAG, "The cache dir: ${cacheDir.listFiles()?.toList()}")
                binding.middleTextView.text = "Gemerating patch. The cache dir: ${cacheDir.listFiles()?.toList()}"
            }

            check(version25File.exists() && version26File.exists())
            File(cacheDir, "Auditor-25-to-26.patch").outputStream().use {
                FileByFileV1DeltaGenerator().generateDelta(version25File, version26File, it)
                it.flush()
            }
            */
            withContext(Dispatchers.Main) {
                @SuppressLint("SetTextI18n")
                binding.middleTextView.text = """
                    Done! The cache dir:  ${cacheDir.listFiles()?.toList()}       
                                 
                    Digest from patch is $digestFromPatch,
                    and digest from original is $version26Digest
                    
                    Are they the same? ${digestFromPatch == version26Digest}
                """.trimIndent()
            }

            cacheDir.listFiles()?.forEach { it.delete() }

        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}