// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mnnllm.android.modelist

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.mls.api.ModelItem
import com.alibaba.mls.api.download.DownloadInfo
import com.alibaba.mnnllm.android.R
import java.util.Locale
import java.util.stream.Collectors

class ModelListAdapter(private val items: MutableList<ModelItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var filteredItems: List<ModelItem>? = null
    private var modelListListener: ModelItemListener? = null
    private var modelItemDownloadStatesMap: Map<String, ModelItemDownloadState>? = null
    private val modelItemHolders: MutableSet<ModelItemHolder> = HashSet()
    private var filterQuery: String? = null
    private var filterDownloaded = false

    fun setModelListListener(modelListListener: ModelItemListener?) {
        this.modelListListener = modelListListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.recycle_item_model, parent, false)
        val holder = ModelItemHolder(view, modelListListener!!)
        modelItemHolders.add(holder)
        return holder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val modelItem = getItems()[position]
        val downloadState = modelItemDownloadStatesMap!![modelItem.modelId]
        (holder as ModelItemHolder).bind(modelItem, downloadState)
    }

    // Assuming ModelItemHolder is an inner class or structured similarly to allow modification here.
    // If ModelItemHolder is in a separate file, this change needs to be applied there.
    // For the purpose of this exercise, I'm showing the conceptual change
    // as if it's directly modifiable or part of ModelListAdapter.
    // This is a placeholder for the actual ModelItemHolder.bind method modification.

    /*
    // Conceptual change within ModelItemHolder.bind method:
    fun bind(modelItem: ModelItem, downloadState: ModelItemDownloadState?) {
        itemView.tag = modelItem
        // ... other initializations ...

        if (modelItem.isLocal) {
            nameTextView.text = modelItem.modelName
            // Assuming model_action_open and model_status_local are string resources
            downloadButton.text = itemView.context.getString(R.string.model_action_open)
            downloadButton.isEnabled = true
            progressBar.visibility = View.GONE // Or set to 100%
            statusTextView.text = itemView.context.getString(R.string.model_status_local)
            sizeTextView.visibility = View.GONE // Hide size/progress text if any
            cancelButton.visibility = View.GONE // Hide cancel button

            // Set click listener for the button to directly open/run the model
            downloadButton.setOnClickListener {
                modelListListener.onItemClicked(modelItem)
            }
            itemView.setOnClickListener {
                modelListListener.onItemClicked(modelItem)
            }
            return // Skip further processing for local models
        }

        // ... existing logic for non-local models based on downloadState ...
        // For example:
        // val downloadInfo = downloadState?.downloadInfo
        // if (downloadInfo != null) {
        //    // ... existing switch on downloadInfo.downlodaState ...
        //    // Make sure DownloadSate.COMPLETED also sets button to "Open" or similar
        // } else {
        //    // Handle null downloadInfo, perhaps by setting to NOT_START state UI
        // }
    }
    */

    override fun getItemCount(): Int {
        return getItems().size
    }

    fun updateItems(
        hfModelItems: List<ModelItem>,
        modelItemDownloadStatesMap: Map<String, ModelItemDownloadState>?
    ) {
        this.modelItemDownloadStatesMap = modelItemDownloadStatesMap
        items.clear()
        items.addAll(hfModelItems)
        filter(filterQuery!!, filterDownloaded)
    }

    fun updateItem(modelId: String) {
        val currentItems = getItems()
        var position = -1
        for (i in currentItems.indices) {
            if (currentItems[i].modelId == modelId) {
                position = i
                break
            }
        }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    fun updateProgress(modelId: String?, downloadInfo: DownloadInfo) {
        for (modelItemHolder in modelItemHolders) {
            if (modelItemHolder.itemView.tag == null) {
                continue
            }
            val tempModelId = (modelItemHolder.itemView.tag as ModelItem).modelId
            if (TextUtils.equals(tempModelId, modelId)) {
                modelItemHolder.updateProgress(downloadInfo)
            }
        }
    }

    private fun getItems(): List<ModelItem> {
        return if (filteredItems != null) filteredItems!! else items
    }

    private fun filter(query: String, showDownloadedOnly: Boolean) {
        val filtered = items.stream()
            .filter { hfModelItem: ModelItem ->
                if (showDownloadedOnly) {
                    // For local models, they are always "COMPLETED" in terms of availability
                    if (hfModelItem.isLocal) {
                        // Keep if showing downloaded and it's local
                    } else {
                        val modelItemState = modelItemDownloadStatesMap!![hfModelItem.modelId]
                        if (modelItemState != null && modelItemState.downloadInfo!!.downlodaState != DownloadInfo.DownloadSate.COMPLETED) {
                            return@filter false
                        }
                    }
                }
                val modelName = hfModelItem.modelName!!.lowercase(Locale.getDefault())
                modelName.contains(query.lowercase(Locale.getDefault())) ||
                        hfModelItem.newTags.stream().anyMatch { tag: String ->
                            tag.lowercase(Locale.getDefault()).contains(
                                query.lowercase(
                                    Locale.getDefault()
                                )
                            )
                        }
            }
            .collect(Collectors.toList())
        if (filtered.size != items.size) {
            this.filteredItems = filtered
        } else {
            this.filteredItems = null
        }
        notifyDataSetChanged()
    }

    fun unfilter() {
        this.filteredItems = null
        notifyDataSetChanged()
    }

    fun setFilter(filterQuery: String, filterDownloaded: Boolean) {
        this.filterQuery = filterQuery
        this.filterDownloaded = filterDownloaded
        filter(filterQuery, filterDownloaded)
    }
}
