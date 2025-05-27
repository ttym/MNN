// Created by ruoyi.sjd on 2024/12/25.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.
package com.alibaba.mnnllm.android.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.alibaba.mls.api.ModelItem
import com.alibaba.mnnllm.android.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

object ModelUtils {

    private const val PREFS_NAME = "UserLocalModels"
    private const val KEY_LOCAL_MODELS = "localModels"
    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    // Simple data class for robust serialization
    data class LocalModelEntry(val modelId: String, val name: String, val path: String)

    // Call this method from your Application class or main activity
    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadUserDefinedLocalModels()
    }

    fun getDrawableId(modelName: String?): Int {
        if (modelName == null) {
            return 0
        }
        val modelLower = modelName.lowercase(Locale.getDefault())
        if (modelLower.contains("deepseek")) {
            return R.drawable.deepseek_icon
        } else if (modelLower.contains("qwen") || modelLower.contains("qwq")) {
            return R.drawable.qwen_icon
        } else if (modelLower.contains("llama") || modelLower.contains("mobilellm")) {
            return R.drawable.llama_icon
        } else if (modelLower.contains("smo")) {
            return R.drawable.smolm_icon
        } else if (modelLower.contains("phi")) {
            return R.drawable.phi_icon
        } else if (modelLower.contains("baichuan")) {
            return R.drawable.baichuan_icon
        } else if (modelLower.contains("yi")) {
            return R.drawable.yi_icon
        } else if (modelLower.contains("glm") || modelLower.contains("codegeex")) {
            return R.drawable.chatglm_icon
        } else if (modelLower.contains("reader")) {
            return R.drawable.jina_icon
        } else if (modelLower.contains("internlm")) {
            return R.drawable.internlm_icon
        } else if (modelLower.contains("gemma")) {
            return R.drawable.gemma_icon
        }
        return 0
    }

    @SuppressLint("DefaultLocale")
    fun generateBenchMarkString(metrics: HashMap<String, Any>): String {
        if (metrics.containsKey("total_timeus")) {
            return generateDiffusionBenchMarkString(metrics)
        }
        val promptLen = metrics["prompt_len"] as Long
        val decodeLen = metrics["decode_len"] as Long
        val prefillTimeUs = metrics["prefill_time"] as Long
        val decodeTimeUs = metrics["decode_time"] as Long
        // Calculate speeds in tokens per second
        val promptSpeed =
            if ((prefillTimeUs > 0)) (promptLen / (prefillTimeUs / 1000000.0)) else 0.0
        val decodeSpeed = if ((decodeTimeUs > 0)) (decodeLen / (decodeTimeUs / 1000000.0)) else 0.0
        return String.format(
            "Prefill: %.2fs, %d tokens, %.2f tokens/s \nDecode: %.2fs, %d tokens, %.2f tokens/s",
            prefillTimeUs.toFloat() / 1000000, promptLen, promptSpeed,
            decodeTimeUs.toFloat() / 1000000,decodeLen, decodeSpeed,
        )
    }

    @SuppressLint("DefaultLocale")
    fun generateDiffusionBenchMarkString(metrics: HashMap<String, Any>): String {
        val totalDuration = metrics["total_timeus"] as Long * 1.0 / 1000000.0
        return String.format("Generate time: %.2f s", totalDuration)
    }

    private val hotList: MutableSet<String> = HashSet()
    private val goodList: MutableSet<String> = HashSet()
    private val blackList: MutableSet<String> = HashSet()

    /**
     * you can add ModelItem.fromLocalModel("Qwen-Omni-7B", "/data/local/tmp/omni_test/model")
     * to load local models
     */
    private val localModelList = mutableListOf<ModelItem>()

    init {
        // Load user-defined models during initialization
        // Ensure init(context) is called before this object is used
        // loadUserDefinedLocalModels() // Now called explicitly after context is available
        blackList.add("taobao-mnn/bge-large-zh-MNN") //embedding
        blackList.add("taobao-mnn/gte_sentence-embedding_multilingual-base-MNN") //embedding
        blackList.add("taobao-mnn/QwQ-32B-Preview-MNN") //too big
        blackList.add("taobao-mnn/codegeex2-6b-MNN") //not for chat
        blackList.add("taobao-mnn/chatglm-6b-MNN") //deprecated
        blackList.add("taobao-mnn/chatglm2-6b-MNN")
        blackList.add("taobao-mnn/stable-diffusion-v1-5-mnn-general") //in android, we use opencl version
    }

    init {
        hotList.add("taobao-mnn/DeepSeek-R1-7B-Qwen-MNN")
    }


    init {
        goodList.add("taobao-mnn/DeepSeek-R1-1.5B-Qwen-MNN")
        goodList.add("taobao-mnn/Qwen2.5-0.5B-Instruct-MNN")
        goodList.add("taobao-mnn/Qwen2.5-1.5B-Instruct-MNN")
        goodList.add("taobao-mnn/Qwen2.5-7B-Instruct-MNN")
        goodList.add("taobao-mnn/Qwen2.5-3B-Instruct-MNN")
        goodList.add("taobao-mnn/gemma-2-2b-it-MNN")
    }

    private fun isBlackListPattern(modelName: String): Boolean {
        return modelName.contains("qwen1.5")
                || modelName.contains("qwen-1")
                || isDiffusionModel(modelName) && (modelName.contains("metal") || modelName.contains(
            "gpu"
        ))
    }


    private fun isQwen3(modelName: String):Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("qwen3")
    }

    fun processList(hfModelItems: List<ModelItem>): List<ModelItem> {

        val goodItems: MutableList<ModelItem> = ArrayList()
        val recommendedItems: MutableList<ModelItem> = ArrayList()
        val chatItems: MutableList<ModelItem> = ArrayList()
        val otherItems: MutableList<ModelItem> = ArrayList()
        for (item in hfModelItems) {
            val modelIdLowerCase = item.modelId!!.lowercase(Locale.getDefault())
            if (blackList.contains(item.modelId) || isBlackListPattern(modelIdLowerCase)) {
                continue
            }
            if (isQwen3(modelIdLowerCase) || isOmni(modelIdLowerCase)) {
                recommendedItems.add(item)
            } else if (goodList.contains(item.modelId)) {
                goodItems.add(item)
            } else if (modelIdLowerCase.contains("chat")) { //optimized for chat, should at top
                chatItems.add(item)
            } else {
                otherItems.add(item)
            }
        }
        val result: MutableList<ModelItem> = mutableListOf()
        result.addAll(localModelList)
        result.addAll(recommendedItems)
        result.addAll(goodItems)
        result.addAll(chatItems)
        result.addAll(otherItems)
        return result
    }

    fun isAudioModel(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("audio") || isOmni(modelName)
    }

    fun isMultiModalModel(modelName: String): Boolean {
        return isAudioModel(modelName) || isVisualModel(modelName) || isDiffusionModel(modelName) || isOmni(modelName)
    }

    fun isDiffusionModel(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("stable-diffusion")
    }

    @JvmStatic
    fun getModelName(modelId: String?): String? {
        if (modelId != null && modelId.contains("/")) {
            return modelId.substring(modelId.lastIndexOf("/") + 1)
        }
        return modelId
    }

    @JvmStatic
    fun generateSimpleTags(modelName: String, modelItem: ModelItem): ArrayList<String> {
        val splits = modelName.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val tags = ArrayList<String>()
        val isDiffusion = isDiffusionModel(modelName)
        if (splits.size > 1 && !isDiffusion) {
            val brand = splits[0]
            tags.add(brand.lowercase(Locale.getDefault()))
        }
        for (i in 1 until splits.size) {
            val tag = splits[i]
            if (tag.lowercase(Locale.getDefault()).matches("^[\\\\.0-9]+[mb]$".toRegex())) {
                tags.add(tag.lowercase(Locale.getDefault()))
            }
        }
        if (isDiffusion) {
            tags.add("diffusion")
        } else {
            tags.add("text")
            if (isAudioModel(modelName)) {
                tags.add("audio")
            } else if (isVisualModel(modelName)) {
                tags.add("visual")
            }
        }
        if (modelItem.isLocal) {
            tags.add("local")
        }
        return tags
    }

    fun isVisualModel(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("vl") || isOmni(modelName)
    }

    fun isR1Model(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("deepseek-r1")
    }

    fun safeModelId(modelId: String): String {
        return modelId.replace("/".toRegex(), "_")
    }

    fun isOmni(modelName: String): Boolean {
        return modelName.lowercase(Locale.getDefault()).contains("omni")
    }

    fun isSupportThinkingSwitch(modelName: String): Boolean {
        return isQwen3(modelName)
    }

    fun supportAudioOutput(modelName: String): Boolean {
        return isOmni(modelName)
    }

    private fun saveUserDefinedLocalModels() {
        if (!::sharedPreferences.isInitialized) return

        // Convert ModelItem list to List<LocalModelEntry> for serialization
        val entriesToSave = localModelList.mapNotNull { item ->
            // Ensure item.name and item.localPath are not null, modelId should also be reliable
            if (item.modelId != null && item.name != null && item.localPath != null) {
                LocalModelEntry(item.modelId!!, item.name!!, item.localPath!!)
            } else {
                // Log or handle items with missing critical information
                Log.w("ModelUtils", "Skipping saving model item with missing info: $item")
                null
            }
        }
        val json = gson.toJson(entriesToSave)
        sharedPreferences.edit().putString(KEY_LOCAL_MODELS, json).apply()
    }

    private fun loadUserDefinedLocalModels() {
        if (!::sharedPreferences.isInitialized) return

        val json = sharedPreferences.getString(KEY_LOCAL_MODELS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<LocalModelEntry>>() {}.type
                val loadedEntries: List<LocalModelEntry> = gson.fromJson(json, type)
                
                localModelList.clear() // Clear current list before loading
                loadedEntries.forEach { entry ->
                    val modelItem = ModelItem.fromLocalModel(entry.name, entry.path)
                    // The modelId from fromLocalModel might differ from entry.modelId if it was custom.
                    // For user-defined local models, entry.name and entry.path are primary.
                    // If entry.modelId needs to be preserved strictly, and ModelItem.modelId is mutable:
                    // modelItem.modelId = entry.modelId // This depends on ModelItem's mutability
                    
                    // Add to list, ensuring no duplicates based on the new modelId from fromLocalModel
                    if (!localModelList.any { it.modelId == modelItem.modelId }) {
                        localModelList.add(modelItem)
                    }
                }
            } catch (e: Exception) {
                Log.e("ModelUtils", "Error loading or deserializing local models", e)
                // Optionally, clear corrupted prefs: sharedPreferences.edit().remove(KEY_LOCAL_MODELS).apply()
            }
        }
    }

    fun addUserDefinedLocalModel(name: String, path: String) {
        // Validate name and path if necessary
        if (name.isBlank() || path.isBlank()) {
            Log.e("ModelUtils", "Attempted to add local model with blank name or path.")
            return
        }

        val modelItem = ModelItem.fromLocalModel(name, path)
        // No longer using extras to mark as userDefined

        // Avoid duplicates based on modelId (which is derived from name/path by fromLocalModel)
        if (localModelList.none { it.modelId == modelItem.modelId }) {
            localModelList.add(0, modelItem) // Add to the beginning of the list
            saveUserDefinedLocalModels()
        } else {
            Log.w("ModelUtils", "Attempted to add duplicate local model: ${modelItem.modelId}")
        }
    }

    fun removeUserDefinedLocalModel(modelId: String) {
        // Remove based on modelId. No longer need to check extras.
        val initialSize = localModelList.size
        localModelList.removeAll { it.modelId == modelId }
        
        if (localModelList.size < initialSize) { // Check if any item was actually removed
            saveUserDefinedLocalModels()
        }
    }
}
