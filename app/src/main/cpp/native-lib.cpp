#include "llama/llama.h"
#include <vector>
#include <jni.h>
#include <string>
#include <cstring>
#include <algorithm>
#include <android/log.h>
#include <chrono>
#include <set>
#include <sstream>


#define LOG_TAG "SLM_NATIVE"

/*llama_batch make_batch(
        const std::vector<llama_token>& tokens,
        int n_ctx) {

    llama_batch batch = llama_batch_init(
            tokens.size(),
            0,
            n_ctx
    );

    for (int i = 0; i < tokens.size(); i++) {
        batch.token[i] = tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i]  = 1;
        batch.logits[i] = false;
    }

    batch.logits[tokens.size() - 1] = true;
    return batch;
}*/

/*static const std::set<std::string> ALLOWED_ALLERGENS = {
        "milk", "egg", "peanut", "tree nut",
        "wheat", "soy", "fish", "shellfish", "sesame"
};*/

std::string runModel(const std::string& prompt,
                     const std::string& model_path,
                     int template_type) {

    // ================= Metrics =================
    auto t_start = std::chrono::high_resolution_clock::now();
    bool first_token_seen = false;

    long ttft_ms = -1;
    long itps = -1;
    long otps = -1;
    long oet_ms = -1;

    int generated_tokens = 0;

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "runModel() started");

    // ================= Apply chat template based on model type =================
    // Template types: 0 = ChatML (Qwen), 1 = Gemma, 2 = Llama 3, 3 = Phi
    std::string formatted_prompt;
    switch (template_type) {
        case 1:
            // Gemma format (Vikhr-Gemma-2B)
            formatted_prompt = "<start_of_turn>user\n" + prompt + "<end_of_turn>\n<start_of_turn>model\n";
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Using Gemma chat template");
            break;
        case 2:
            // Llama 3 format (Llama-3.2-1B, Llama-3.2-3B)
            formatted_prompt = "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n\n" + prompt + "<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n";
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Using Llama 3 chat template");
            break;
        case 3:
            // Phi format (Phi-3.5-mini, Phi-3-mini-4k)
            formatted_prompt = "<|user|>\n" + prompt + "<|end|>\n<|assistant|>\n";
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Using Phi chat template");
            break;
        default:
            // ChatML format (Qwen 2.5) - templateType=0 or default
            formatted_prompt = "<|im_start|>user\n" + prompt + "<|im_end|>\n<|im_start|>assistant\n";
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Using ChatML chat template");
            break;
    }

    // ================= Backend =================
    llama_backend_init();

    // ================= Load model =================
    llama_model_params model_params = llama_model_default_params();

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Loading model from: %s", model_path.c_str());

    llama_model* model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!model) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Failed to load model");
        return "";
    }

    const llama_vocab* vocab = llama_model_get_vocab(model);

    // ================= Context =================
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;  // Increased from 512 to handle long ingredient lists
    ctx_params.n_threads = 4;

    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Failed to create context");
        return "";
    }

    // ================= Tokenize prompt =================
    // Use the formatted prompt with Qwen chat template
    std::vector<llama_token> prompt_tokens(formatted_prompt.size() + 64);

    int n_prompt = llama_tokenize(
            vocab,
            formatted_prompt.c_str(),
            formatted_prompt.size(),
            prompt_tokens.data(),
            prompt_tokens.size(),
            true,   // add BOS
            false
    );

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Formatted prompt: %s", formatted_prompt.c_str());

    if (n_prompt <= 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Tokenization failed");
        return "";
    }

    prompt_tokens.resize(n_prompt);

    // ================= Initial batch (prompt) =================
    llama_batch batch = llama_batch_init(n_prompt, 0, ctx_params.n_ctx);
    batch.n_tokens = n_prompt;

    for (int i = 0; i < n_prompt; i++) {
        batch.token[i] = prompt_tokens[i];
        batch.pos[i]   = i;
        batch.seq_id[i][0] = 0;
        batch.n_seq_id[i]  = 1;
        batch.logits[i]    = false;
    }

    // 🔑 logits only on LAST prompt token
    batch.logits[n_prompt - 1] = true;

    // ================= Prefill =================
    auto t_prefill_start = std::chrono::high_resolution_clock::now();

    if (llama_decode(ctx, batch) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
                            "Prompt decode failed");
        return "";
    }

    auto t_prefill_end = std::chrono::high_resolution_clock::now();
    long prefill_ms =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_prefill_end - t_prefill_start
            ).count();

    if (prefill_ms > 0) {
        itps = (n_prompt * 1000L) / prefill_ms;
    }

    // ================= Sampler =================
    llama_sampler* sampler = llama_sampler_init_greedy();

    // ================= Generation =================
    std::string output;
    const int max_tokens = 32;  // Increased for longer allergen lists

    int n_pos = 0;
    int n_predict = max_tokens;

    auto t_gen_start = std::chrono::high_resolution_clock::now();

    while (n_pos + batch.n_tokens < n_prompt + n_predict) {

        // ---- sample token (AFTER decode) ----
        llama_token token = llama_sampler_sample(sampler, ctx, -1);

        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        // ---- TTFT ----
        if (!first_token_seen) {
            auto t_first = std::chrono::high_resolution_clock::now();
            ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_first - t_start
            ).count();
            first_token_seen = true;
        }

        // ---- token → text ----
        char buf[128];
        int n = llama_token_to_piece(
                vocab, token, buf, sizeof(buf), 0, true);

        if (n > 0) {
            output.append(buf, n);

            // Rule 1: stop at first newline (ONLY comma-separated list)
            if (output.find('\n') != std::string::npos) {
                break;
            }
        }

        generated_tokens++;

        // ---- prepare next batch (REFERENCE CORRECT) ----
        batch = llama_batch_get_one(&token, 1);

        // ---- advance model ----
        if (llama_decode(ctx, batch) != 0) {
            break;
        }

        n_pos += batch.n_tokens;
    }

    auto t_gen_end = std::chrono::high_resolution_clock::now();
    long gen_ms =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    t_gen_end - t_gen_start
            ).count();

    if (gen_ms > 0) {
        otps = (generated_tokens * 1000L) / gen_ms;
    }

    oet_ms = gen_ms;

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Raw model output: %s", output.c_str());

    // NOTE: Filtering/mapping is now done in Kotlin (MainActivity.kt)
    // This allows mapping terms like "Crustaceans" -> "shellfish", "Gluten" -> "wheat"
    // The raw output is passed directly to Kotlin for processing


    // ================= Cleanup =================
    llama_sampler_free(sampler);
    //llama_batch_free(batch);
    llama_free(ctx);
    llama_free_model(model);



    // ================= Return =================
    std::string result =
            "TTFT_MS=" + std::to_string(ttft_ms) +
            ";ITPS=" + std::to_string(itps) +
            ";OTPS=" + std::to_string(otps) +
            ";OET_MS=" + std::to_string(oet_ms) +
            "|" + output;

    return result;
}




extern "C"
JNIEXPORT jstring JNICALL
Java_com_mad_assignment_MainActivity_inferAllergens(
        JNIEnv *env,
        jobject,
        jstring inputPrompt,
        jstring modelPath,
        jint templateType) {

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "inferAllergens() called");

    // Extract model path from Java string
    const char* pathCstr = env->GetStringUTFChars(modelPath, nullptr);
    std::string model_path(pathCstr);
    env->ReleaseStringUTFChars(modelPath, pathCstr);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Model path: %s", model_path.c_str());
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Template type: %d", templateType);

    // Extract prompt from Java string
    const char *cstr = env->GetStringUTFChars(inputPrompt, nullptr);
    std::string prompt(cstr);
    env->ReleaseStringUTFChars(inputPrompt, cstr);

    // Run model with specified path and template type
    std::string output = runModel(prompt, model_path, templateType);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
                        "Inference output: %s", output.c_str());

    return env->NewStringUTF(output.c_str());
}
