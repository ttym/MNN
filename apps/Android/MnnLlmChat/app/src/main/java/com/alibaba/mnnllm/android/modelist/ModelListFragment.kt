// Created by ruoyi.sjd on 2025/1/13.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mnnllm.android.modelist

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.mls.api.ModelItem
import com.alibaba.mnnllm.android.MainActivity
import com.alibaba.mnnllm.android.R
import com.alibaba.mnnllm.android.mainsettings.MainSettingsActivity
import com.alibaba.mnnllm.android.utils.CrashUtil
import com.alibaba.mnnllm.android.utils.PreferenceUtils.isFilterDownloaded
import com.alibaba.mnnllm.android.utils.PreferenceUtils.setFilterDownloaded
import com.alibaba.mnnllm.android.utils.RouterUtils.startActivity
import com.alibaba.mnnllm.android.utils.ModelUtils

class ModelListFragment : Fragment(), ModelListContract.View {
    private lateinit var modelListRecyclerView: RecyclerView

    override var adapter: ModelListAdapter? = null
        private set
    private var modelListPresenter: ModelListPresenter? = null
    private val hfModelItemList: MutableList<ModelItem> = mutableListOf()

    private lateinit var modelListLoadingView: View
    private lateinit var modelListErrorView: View

    private var modelListErrorText: TextView? = null

    private var filterDownloaded = false
    private var filterQuery = ""

    private lateinit var openDirectoryLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openDirectoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    try {
                        requireActivity().contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        // For now, just show the URI.
                        // In a future step, this URI will be used to add the model via ModelUtils.
                        // Toast.makeText(requireContext(), "Selected directory URI: $uri", Toast.LENGTH_LONG).show()
                        Log.i("ModelListFragment", "Selected directory URI: $uri, attempting to process.")
                        promptForModelNameAndCopy(uri)
                    } catch (e: SecurityException) {
                        Log.e("ModelListFragment", "Failed to take persistable URI permission for $uri", e)
                        Toast.makeText(requireContext(), "Failed to get permissions for the selected directory.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun copyDirectoryFromUri(context: Context, sourceTreeUri: Uri, destinationParentDir: File, modelName: String): File? {
        val sourceDocument = DocumentFile.fromTreeUri(context, sourceTreeUri) ?: return null
        // Use the user-chosen modelName for the destination directory
        val destinationDir = File(destinationParentDir, modelName)

        if (destinationDir.exists()) {
            // Overwrite existing directory
            destinationDir.deleteRecursively()
        }
        if (!destinationDir.mkdirs()) {
            Log.e("CopyDir", "Failed to create destination directory: ${destinationDir.absolutePath}")
            return null
        }

        var success = true // Flag to track overall success

        fun copyFile(sourceFile: DocumentFile, destFile: File) {
            try {
                context.contentResolver.openInputStream(sourceFile.uri)?.use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e("CopyDir", "Error copying file ${sourceFile.name} to ${destFile.name}", e)
                success = false // Mark as failed if any file copy fails
                // Optionally, re-throw or handle more gracefully (e.g., collect all errors)
            }
        }

        fun copyRecursive(currentSourceDir: DocumentFile, currentDestDir: File) {
            if (!success) return // Stop recursion if a failure occurred

            currentSourceDir.listFiles().forEach { entry ->
                if (!success) return@forEach

                val destEntry = File(currentDestDir, entry.name!!)
                if (entry.isDirectory) {
                    if (destEntry.mkdirs()) {
                        copyRecursive(entry, destEntry)
                    } else {
                        Log.e("CopyDir", "Failed to create subdirectory ${destEntry.absolutePath}")
                        success = false
                    }
                } else if (entry.isFile) {
                    copyFile(entry, destEntry)
                }
            }
        }

        copyRecursive(sourceDocument, destinationDir)

        return if (success && destinationDir.exists() && (destinationDir.listFiles()?.isNotEmpty() == true || sourceDocument.listFiles().isEmpty())) {
             // Consider successful if no errors and dest dir exists and is not empty (unless source was empty)
            destinationDir
        } else {
            // Clean up destinationDir if copy failed significantly
            if (destinationDir.exists()) {
                destinationDir.deleteRecursively()
            }
            null
        }
    }


    private fun promptForModelNameAndCopy(sourceUri: Uri) {
        val context = requireContext()
        val documentFile = DocumentFile.fromTreeUri(context, sourceUri)
        val defaultModelName = documentFile?.name ?: "MyModel_${System.currentTimeMillis()}"

        val editText = EditText(context).apply {
            setText(defaultModelName)
            setHint("Enter model name")
        }

        AlertDialog.Builder(context)
            .setTitle("Name Your Model")
            .setView(editText)
            .setPositiveButton("OK") { dialog, _ ->
                val chosenName = editText.text.toString().trim()
                if (chosenName.isEmpty()) {
                    Toast.makeText(context, "Model name cannot be empty.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Define the parent directory for all local models
                val localModelsBaseDir = File(context.filesDir, "local_models")
                if (!localModelsBaseDir.exists() && !localModelsBaseDir.mkdirs()) {
                    Toast.makeText(context, "Failed to create base local models directory.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newModelDir = copyDirectoryFromUri(context, sourceUri, localModelsBaseDir, chosenName)

                if (newModelDir != null && newModelDir.exists()) {
                    ModelUtils.addUserDefinedLocalModel(chosenName, newModelDir.absolutePath)
                    Toast.makeText(context, "Model '$chosenName' added successfully.", Toast.LENGTH_LONG).show()
                    // Refresh the model list
                    modelListPresenter?.load() // Re-trigger load to refresh the list including local models
                } else {
                    Toast.makeText(context, "Failed to copy model files for '$chosenName'.", Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                Toast.makeText(context, "Add model cancelled.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .show()
    }

    private fun setupSearchView(menu: Menu) {
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView?
        if (searchView != null) {
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    filterQuery = query
                    adapter!!.setFilter(query, filterDownloaded)
                    return false
                }

                override fun onQueryTextChange(query: String): Boolean {
                    filterQuery = query
                    adapter!!.setFilter(query, filterDownloaded)
                    return true
                }
            })
            searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    // SearchView is expanded
                    Log.d("SearchView", "SearchView expanded")
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    // SearchView is collapsed
                    Log.d("SearchView", "SearchView collapsed")
                    adapter!!.unfilter()

                    return true
                }
            })
        }
    }

    private val menuProvider: MenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            // Inflate your menu resource here
            menuInflater.inflate(R.menu.menu_main, menu)
            setupSearchView(menu)
            val issueMenu = menu.findItem(R.id.action_github_issue)
            issueMenu.setOnMenuItemClickListener { item: MenuItem? ->
                if (activity != null) {
                    (activity as MainActivity).onReportIssue(null)
                }
                true
            }

            val filterDownloadedMenu = menu.findItem(R.id.action_filter_downloaded)
            filterDownloadedMenu.setChecked(isFilterDownloaded(context))
            filterDownloadedMenu.setOnMenuItemClickListener {
                filterDownloaded = isFilterDownloaded(
                    context
                )
                filterDownloaded = !filterDownloaded
                setFilterDownloaded(context, filterDownloaded)
                filterDownloadedMenu.setChecked(filterDownloaded)
                adapter!!.setFilter(filterQuery, filterDownloaded)
                true
            }
            val settingsMenu = menu.findItem(R.id.action_settings)
            settingsMenu.setOnMenuItemClickListener {
                if (activity != null) {
                    startActivity(activity!!, MainSettingsActivity::class.java)
                }
                true
            }

            val starGithub = menu.findItem(R.id.action_star_project)
            starGithub.setOnMenuItemClickListener { item: MenuItem? ->
                if (activity != null) {
                    (activity as MainActivity).onStarProject(null)
                }
                true
            }
            val reportCrashMenu = menu.findItem(R.id.action_report_crash)
            reportCrashMenu.setOnMenuItemClickListener {
                if (CrashUtil.hasCrash()) {
                    CrashUtil.shareLatestCrash(context!!)
                }
                true
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return true
        }

        override fun onPrepareMenu(menu: Menu) {
            super<MenuProvider>.onPrepareMenu(menu)
            val menuResumeAllDownlods = menu.findItem(R.id.action_resume_all_downloads)
            menuResumeAllDownlods.setVisible(modelListPresenter!!.unfinishedDownloadCount > 0)
            menuResumeAllDownlods.setOnMenuItemClickListener { item: MenuItem? ->
                modelListPresenter!!.resumeAllDownloads()
                true
            }
            val reportCrashMenu = menu.findItem(R.id.action_report_crash)
            reportCrashMenu.isVisible = CrashUtil.hasCrash()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_modellist, container, false)
        modelListRecyclerView = view.findViewById(R.id.model_list_recycler_view)
        modelListLoadingView = view.findViewById(R.id.model_list_loading_view)
        modelListErrorView = view.findViewById(R.id.model_list_failed_view)
        modelListErrorText = modelListErrorView.findViewById(R.id.tv_error_text)
        modelListErrorView.setOnClickListener {
            modelListPresenter!!.load()
        }
        modelListRecyclerView.setLayoutManager(
            LinearLayoutManager(
                context,
                LinearLayoutManager.VERTICAL,
                false
            )
        )
        adapter = ModelListAdapter(hfModelItemList)

        modelListRecyclerView.setAdapter(adapter)
        modelListPresenter = ModelListPresenter(requireContext(), this)
        adapter!!.setModelListListener(modelListPresenter)
        filterDownloaded = isFilterDownloaded(context)
        adapter!!.setFilter(filterQuery, filterDownloaded)
        modelListPresenter!!.onCreate()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val fabAddLocalModel: FloatingActionButton = view.findViewById(R.id.fab_add_local_model)
        fabAddLocalModel.setOnClickListener {
            // Launch the directory picker
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            // Optionally, specify an initial URI to start browsing from
            // intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            try {
                openDirectoryLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("ModelListFragment", "Failed to launch directory picker", e)
                Toast.makeText(requireContext(), "Could not open directory picker.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        modelListPresenter!!.onDestroy()
    }

    override fun onListAvailable() {
        modelListErrorView.visibility = View.GONE
        modelListLoadingView.visibility = View.GONE
        modelListRecyclerView.visibility = View.VISIBLE
    }

    override fun onLoading() {
        if (adapter!!.itemCount > 0) {
            return
        }
        modelListErrorView.visibility = View.GONE
        modelListLoadingView.visibility = View.VISIBLE
        modelListRecyclerView.visibility = View.GONE
    }

    override fun onListLoadError(error: String?) {
        if (adapter!!.itemCount > 0) {
            return
        }
        modelListErrorText!!.text = getString(R.string.loading_failed_click_tor_retry, error)
        modelListErrorView.visibility = View.VISIBLE
        modelListLoadingView.visibility = View.GONE
        modelListRecyclerView.visibility = View.GONE
    }

    override fun runModel(absolutePath: String?, modelId: String?) {
        (activity as MainActivity).runModel(absolutePath, modelId, null)
    }
}
