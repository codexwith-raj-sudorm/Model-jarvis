// ---------------------------------------------------------------------------
// llama_jni.cpp — minimal JNI bridge between LlamaCppEngine.kt and llama.cpp
//
// Written against the current llama.cpp public API (llama.h):
//   llama_backend_init / llama_model_load_from_file / llama_init_from_model
//   llama_model_get_vocab / llama_tokenize / llama_token_to_piece
//   llama_batch_get_one  / llama_decode
//   llama_sampler_chain_* / llama_sampler_sample / llama_vocab_is_eog
//
// Chat templating (ChatML / Gemma / Llama3) is done on the Kotlin side;
// this layer receives a fully-rendered prompt string.
// Streaming: nativeGenerate takes a TokenCallback object whose onToken(String)
// is invoked from the same thread that called nativeGenerate.
//
// THREAD SAFETY:
//   All engine state is guarded by g_mutex — init / unload / generate are
//   strictly serialized, so model switches during generation can no longer
//   segfault. generate() can run for a long time, so load/unload first set
//   g_abort (a cooperative stop flag checked between decode steps): a running
//   generation exits within ~one token and releases the mutex quickly.
//   nativeStopGenerate() sets the flag without taking the lock (safe from any
//   thread, incl. the UI thread). nativeIsLoaded() reads a lock-free atomic,
//   so status checks never block behind a running generation.
// ---------------------------------------------------------------------------

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "jarvis-llama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,    TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   TAG, __VA_ARGS__)

namespace {

struct EngineState {
    llama_model   * model   = nullptr;
    llama_context * ctx     = nullptr;
    const llama_vocab * vocab = nullptr;
    bool initialized = false;
};

EngineState g_state;

// Serializes every command that touches g_state (init / unload / generate).
std::mutex g_mutex;

// Lock-free mirror of g_state.initialized for cheap status checks.
std::atomic<bool> g_initialized{false};

// Cooperative stop: checked between decode steps inside nativeGenerate.
std::atomic<bool> g_abort{false};

void free_engine() {
    if (g_state.ctx)   { llama_free(g_state.ctx);           g_state.ctx   = nullptr; }
    if (g_state.model) { llama_model_free(g_state.model);   g_state.model = nullptr; }
    g_state.vocab = nullptr;
    g_state.initialized = false;
    g_initialized.store(false);
}

std::string jstring_to_std(JNIEnv * env, jstring js) {
    if (!js) return {};
    const char * chars = env->GetStringUTFChars(js, nullptr);
    std::string s(chars);
    env->ReleaseStringUTFChars(js, chars);
    return s;
}

// Returns the length of a trailing incomplete UTF-8 sequence (0 if complete).
// Byte-level-BPE models can emit single continuation bytes as "tokens", so we
// must buffer partial runes instead of handing them to NewStringUTF.
size_t incomplete_tail_len(const std::string & s) {
    size_t n_cont = 0;
    size_t i = s.size();
    while (i-- > 0) {
        unsigned char c = (unsigned char) s[i];
        if ((c & 0xC0) == 0x80) {            // continuation byte
            if (++n_cont > 3) return 0;      // malformed, give up
            continue;
        }
        size_t need = 0;
        if      (c < 0x80)        need = 1;
        else if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        else return 0;                       // invalid lead, give up
        return (n_cont + 1 < need) ? n_cont + 1 : 0;
    }
    return 0;
}

// Strips invalid UTF-8 (bad lead bytes, orphan continuation bytes, overlong
// C0/C1, F5+ leads) so NewStringUTF never receives malformed input — a stray
// invalid byte from the model would otherwise abort the JVM. Stops at a
// trailing partial sequence; the caller keeps those bytes buffered.
std::string sanitize_utf8(const std::string & in) {
    std::string out;
    out.reserve(in.size());
    size_t i = 0;
    while (i < in.size()) {
        unsigned char c = (unsigned char) in[i];
        size_t need;
        if      (c < 0x80)                        need = 1;
        else if (c >= 0xC2 && (c & 0xE0) == 0xC0) need = 2; // C0/C1 = overlong
        else if ((c & 0xF0) == 0xE0)              need = 3;
        else if (c >= 0xF0 && c <= 0xF4)          need = 4; // F5+ = invalid
        else { i++; continue; }                   // drop bad byte
        if (i + need > in.size()) break;          // partial rune — stop here
        bool ok = true;
        for (size_t k = 1; k < need; k++) {
            if (((unsigned char) in[i + k] & 0xC0) != 0x80) { ok = false; break; }
        }
        if (!ok) { i++; continue; }               // lead without continuations
        out.append(in, i, need);
        i += need;
    }
    return out;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_jarvis_assistant_llm_LlamaCppEngine_nativeInit(
        JNIEnv * env, jobject /*thiz*/,
        jstring jModelPath, jint jCtxLen, jint jThreads) {

    g_abort = true;                          // halt any in-flight generation
    std::lock_guard<std::mutex> lock(g_mutex);
    g_abort = false;                         // fresh session

    if (g_state.initialized) free_engine();

    const std::string model_path = jstring_to_std(env, jModelPath);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    // 0 = CPU only; raise for OpenCL/Vulkan GPU builds if you add them.
    mparams.n_gpu_layers = 0;

    g_state.model = llama_model_load_from_file(model_path.c_str(), mparams);
    if (!g_state.model) {
        LOGE("failed to load model: %s", model_path.c_str());
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx       = jCtxLen  > 0 ? (uint32_t) jCtxLen  : 2048;
    cparams.n_seq_max   = 1;
    cparams.n_threads   = jThreads > 0 ? jThreads : 4;
    cparams.n_threads_batch = cparams.n_threads;

    g_state.ctx = llama_init_from_model(g_state.model, cparams);
    if (!g_state.ctx) {
        LOGE("failed to create context");
        free_engine();
        return JNI_FALSE;
    }

    g_state.vocab = llama_model_get_vocab(g_state.model);
    g_state.initialized = true;
    g_initialized.store(true);
    LOGI("model loaded: %s (n_ctx=%u)", model_path.c_str(), llama_n_ctx(g_state.ctx));
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_jarvis_assistant_llm_LlamaCppEngine_nativeIsLoaded(JNIEnv *, jobject) {
    // lock-free on purpose: never block a status check behind a generation
    return g_initialized.load() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_jarvis_assistant_llm_LlamaCppEngine_nativeUnload(JNIEnv *, jobject) {
    g_abort = true;                          // halt any in-flight generation
    std::lock_guard<std::mutex> lock(g_mutex);
    free_engine();
}

// Cooperative stop — call from any thread (UI included); the running
// generation (if any) exits at its next decode step. Never blocks.
JNIEXPORT void JNICALL
Java_com_jarvis_assistant_llm_LlamaCppEngine_nativeStopGenerate(JNIEnv *, jobject) {
    g_abort = true;
}

JNIEXPORT jstring JNICALL
Java_com_jarvis_assistant_llm_LlamaCppEngine_nativeGenerate(
        JNIEnv * env, jobject /*thiz*/,
        jstring jPrompt, jint jNPredict,
        jfloat jTemp, jfloat jTopP, jint jTopK,
        jstring jGrammar, jobject jCallback) {

    std::lock_guard<std::mutex> lock(g_mutex);   // serialize with init/unload
    g_abort = false;                             // clear any stale stop

    if (!g_state.initialized) {
        return env->NewStringUTF("[error] engine not initialized");
    }

    const std::string prompt = jstring_to_std(env, jPrompt);

    // ---- optional GBNF grammar (tool-call repair path) ---------------------
    // When non-null, generation is constrained to the grammar — used to
    // force a syntactically valid TOOL_CALL JSON when the model's free-form
    // attempt was close but malformed.
    std::string grammar;
    const char * grammar_cstr = nullptr;
    if (jGrammar != nullptr) {
        grammar = jstring_to_std(env, jGrammar);
        if (!grammar.empty()) grammar_cstr = grammar.c_str();
    }

    // ---- optional streaming callback -------------------------------------
    jclass    cbClass = nullptr;
    jmethodID cbMethod = nullptr;
    if (jCallback != nullptr) {
        cbClass  = env->GetObjectClass(jCallback);
        cbMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    }

    // ---- sampler chain ----------------------------------------------------
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    if (jTemp <= 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(jTopK > 0 ? jTopK : 40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(jTopP > 0 ? jTopP : 0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(jTemp));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }
    if (grammar_cstr != nullptr) {
        // appended last: the grammar filters the candidates the samplers
        // propose, so output can only follow the grammar
        llama_sampler * g = llama_sampler_init_grammar(g_state.vocab, grammar_cstr, "root");
        if (g != nullptr) {
            llama_sampler_chain_add(smpl, g);
        } else {
            LOGE("failed to init grammar sampler — falling back to free-form");
        }
    }

    // ---- tokenize prompt ---------------------------------------------------
    const int n_prompt_max = (int) llama_n_ctx(g_state.ctx) - 4;
    std::vector<llama_token> tokens(n_prompt_max);
    int n_tokens = llama_tokenize(
            g_state.vocab,
            prompt.c_str(), (int32_t) prompt.size(),
            tokens.data(), (int32_t) tokens.size(),
            /*add_special=*/true, /*parse_special=*/true);
    if (n_tokens < 0) {
        LOGE("prompt too large for context");
        llama_sampler_free(smpl);
        return env->NewStringUTF("[error] prompt too large for context");
    }
    tokens.resize(n_tokens);
    if (n_tokens >= n_prompt_max) {
        llama_sampler_free(smpl);
        return env->NewStringUTF("[error] prompt too large for context");
    }

    // ---- decode the prompt in n_batch chunks ------------------------------
    const int32_t n_batch = (int32_t) llama_n_batch(g_state.ctx);
    bool failed  = false;
    bool aborted = false;
    for (int32_t i = 0; i < n_tokens; i += n_batch) {
        if (g_abort) { aborted = true; break; }
        const int32_t chunk = (n_tokens - i) < n_batch ? (n_tokens - i) : n_batch;
        llama_batch batch = llama_batch_get_one(tokens.data() + i, chunk);
        if (llama_decode(g_state.ctx, batch) != 0) {
            LOGE("llama_decode failed on prompt chunk at %d", i);
            failed = true;
            break;
        }
    }

    // ---- generation loop ---------------------------------------------------
    std::string out;
    std::string pending;   // buffers partial UTF-8 runes for streaming

    // Emits every complete rune currently in `pending` (validated), keeps any
    // partial tail for the next token, drops invalid bytes instead of
    // crashing the JVM's NewStringUTF.
    auto flush_pending = [&]() {
        if (cbMethod == nullptr || pending.empty()) return;
        const size_t bad  = incomplete_tail_len(pending);
        const size_t keep = pending.size() - bad;
        const std::string emit = sanitize_utf8(pending.substr(0, keep));
        if (!emit.empty()) {
            jstring jpiece = env->NewStringUTF(emit.c_str());
            env->CallVoidMethod(jCallback, cbMethod, jpiece);
            env->DeleteLocalRef(jpiece);
        }
        pending.erase(0, keep);
    };

    if (!failed && !aborted) {
        const int n_predict = jNPredict > 0 ? jNPredict : 512;
        for (int i = 0; i < n_predict; i++) {
            if (g_abort) break;

            llama_token id = llama_sampler_sample(smpl, g_state.ctx, -1);
            if (llama_vocab_is_eog(g_state.vocab, id)) break;

            char piece[64];
            int   piece_len = llama_token_to_piece(
                    g_state.vocab, id, piece, sizeof(piece), 0, /*special=*/true);
            if (piece_len < 0) piece_len = -piece_len;
            if (piece_len > 0) {
                out.append(piece, (size_t) piece_len);
                if (cbMethod != nullptr) {
                    pending.append(piece, (size_t) piece_len);
                    flush_pending();
                }
            }

            llama_token next = id;
            llama_batch batch = llama_batch_get_one(&next, 1);
            if (llama_decode(g_state.ctx, batch) != 0) {
                LOGE("llama_decode failed during generation");
                break;
            }
        }
    }

    llama_sampler_free(smpl);

    // final flush: emit whatever is complete, discard a dangling partial rune
    flush_pending();

    // the full result takes the same validated path — `out` can still contain
    // raw invalid bytes even when streaming was disabled
    return env->NewStringUTF(sanitize_utf8(out).c_str());
}

} // extern "C"
